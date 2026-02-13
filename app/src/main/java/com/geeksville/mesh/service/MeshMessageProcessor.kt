/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.geeksville.mesh.service

import android.util.Log
import co.touchlab.kermit.Logger
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.concurrent.handledLaunch
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.data.repository.MeshLogRepository
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
@Singleton
class MeshMessageProcessor
@Inject
constructor(
    private val nodeManager: MeshNodeManager,
    private val serviceRepository: ServiceRepository,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val router: MeshRouter,
    private val fromRadioDispatcher: FromRadioPacketHandler,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logUuidByPacketId = ConcurrentHashMap<Int, String>()
    private val logInsertJobByPacketId = ConcurrentHashMap<Int, Job>()

    private val earlyReceivedPackets = ArrayDeque<MeshPacket>()
    private val maxEarlyPacketBuffer = 128

    fun clearEarlyPackets() {
        synchronized(earlyReceivedPackets) { earlyReceivedPackets.clear() }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        nodeManager.isNodeDbReady
            .onEach { ready ->
                if (ready) {
                    flushEarlyReceivedPackets("dbReady")
                }
            }
            .launchIn(scope)
    }

    fun handleFromRadio(bytes: ByteArray, myNodeNum: Int?) {
        runCatching { FromRadio.ADAPTER.decode(bytes) }
            .onSuccess { proto -> processFromRadio(proto, myNodeNum) }
            .onFailure { primaryException ->
                runCatching {
                    val logRecord = LogRecord.ADAPTER.decode(bytes)
                    processFromRadio(FromRadio(log_record = logRecord), myNodeNum)
                }
                    .onFailure { _ ->
                        Logger.e(primaryException) {
                            "Failed to parse radio packet (len=${bytes.size} contents=${bytes.toHexString()}). " +
                                "Not a valid FromRadio or LogRecord."
                        }
                    }
            }
    }

    private fun processFromRadio(proto: FromRadio, myNodeNum: Int?) {
        // Audit log every incoming variant
        logVariant(proto)

        val packet = proto.packet
        if (packet != null) {
            handleReceivedMeshPacket(packet, myNodeNum)
        } else {
            fromRadioDispatcher.handleFromRadio(proto)
        }
    }

    private fun logVariant(proto: FromRadio) {
        val (type, message) =
            when {
                proto.log_record != null -> "LogRecord" to proto.log_record.toString()
                proto.rebooted != null -> "Rebooted" to proto.rebooted.toString()
                proto.xmodemPacket != null -> "XmodemPacket" to proto.xmodemPacket.toString()
                proto.deviceuiConfig != null -> "DeviceUIConfig" to proto.deviceuiConfig.toString()
                proto.fileInfo != null -> "FileInfo" to proto.fileInfo.toString()
                proto.my_info != null -> "MyInfo" to proto.my_info.toString()
                proto.node_info != null -> "NodeInfo" to proto.node_info.toString()
                proto.config != null -> "Config" to proto.config.toString()
                proto.moduleConfig != null -> "ModuleConfig" to proto.moduleConfig.toString()
                proto.channel != null -> "Channel" to proto.channel.toString()
                proto.clientNotification != null -> "ClientNotification" to proto.clientNotification.toString()
                else -> return
            }

        insertMeshLog(
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = type,
                received_date = System.currentTimeMillis(),
                raw_message = message,
                fromRadio = proto,
            ),
        )
    }

    fun handleReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?) {
        val rxTime =
            if (packet.rx_time == 0) {
                (System.currentTimeMillis().milliseconds.inWholeSeconds).toInt()
            } else {
                packet.rx_time
            }
        val preparedPacket = packet.copy(rx_time = rxTime)

        if (nodeManager.isNodeDbReady.value) {
            processReceivedMeshPacket(preparedPacket, myNodeNum)
        } else {
            synchronized(earlyReceivedPackets) {
                val queueSize = earlyReceivedPackets.size
                if (queueSize >= maxEarlyPacketBuffer) {
                    val dropped = earlyReceivedPackets.removeFirst()
                    historyLog(Log.WARN) {
                        val portLabel =
                            dropped.decoded?.portnum?.name ?: dropped.decoded?.portnum?.value?.toString() ?: "unknown"
                        "dropEarlyPacket bufferFull size=$queueSize id=${dropped.id} port=$portLabel"
                    }
                }
                earlyReceivedPackets.addLast(preparedPacket)
                val portLabel =
                    preparedPacket.decoded?.portnum?.name
                        ?: preparedPacket.decoded?.portnum?.value?.toString()
                        ?: "unknown"
                historyLog {
                    "queueEarlyPacket size=${earlyReceivedPackets.size} id=${preparedPacket.id} port=$portLabel"
                }
            }
        }
    }

    private fun flushEarlyReceivedPackets(reason: String) {
        val packets =
            synchronized(earlyReceivedPackets) {
                if (earlyReceivedPackets.isEmpty()) return
                val list = earlyReceivedPackets.toList()
                earlyReceivedPackets.clear()
                list
            }
        historyLog { "replayEarlyPackets reason=$reason count=${packets.size}" }
        val myNodeNum = nodeManager.myNodeNum
        packets.forEach { processReceivedMeshPacket(it, myNodeNum) }
    }

    private fun processReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?) {
        val decoded = packet.decoded ?: return
        val log =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = "Packet",
                received_date = System.currentTimeMillis(),
                raw_message = packet.toString(),
                fromNum = packet.from,
                portNum = decoded.portnum.value,
                fromRadio = FromRadio(packet = packet),
            )
        val logJob = insertMeshLog(log)
        logInsertJobByPacketId[packet.id] = logJob
        logUuidByPacketId[packet.id] = log.uuid

        scope.handledLaunch { serviceRepository.emitMeshPacket(packet) }

        myNodeNum?.let { myNum ->
            val from = packet.from
            val isOtherNode = myNum != from
            nodeManager.updateNodeInfo(myNum, withBroadcast = isOtherNode) {
                it.lastHeard = (System.currentTimeMillis().milliseconds.inWholeSeconds).toInt()
            }
            nodeManager.updateNodeInfo(from, withBroadcast = false, channel = packet.channel) {
                it.lastHeard = packet.rx_time
                it.snr = packet.rx_snr
                it.rssi = packet.rx_rssi
                it.hopsAway =
                    if (decoded.portnum == PortNum.RANGE_TEST_APP) {
                        0
                    } else if (packet.hop_start == 0 && (decoded.bitfield ?: 0) == 0) {
                        -1
                    } else if (packet.hop_limit > packet.hop_start) {
                        -1
                    } else {
                        packet.hop_start - packet.hop_limit
                    }
            }

            try {
                router.dataHandler.handleReceivedData(packet, myNum, log.uuid, logJob)
            } finally {
                logUuidByPacketId.remove(packet.id)
                logInsertJobByPacketId.remove(packet.id)
            }
        }
    }

    private fun insertMeshLog(log: MeshLog): Job = scope.handledLaunch { meshLogRepository.get().insert(log) }

    private inline fun historyLog(
        priority: Int = Log.INFO,
        throwable: Throwable? = null,
        crossinline message: () -> String,
    ) {
        if (!BuildConfig.DEBUG) return
        val logger = Logger.withTag("HistoryReplay")
        val msg = message()
        when (priority) {
            Log.VERBOSE -> logger.v(throwable) { msg }
            Log.DEBUG -> logger.d(throwable) { msg }
            Log.INFO -> logger.i(throwable) { msg }
            Log.WARN -> logger.w(throwable) { msg }
            Log.ERROR -> logger.e(throwable) { msg }
            else -> logger.i(throwable) { msg }
        }
    }

    private fun ByteArray.toHexString(): String =
        this.joinToString(",") { byte -> String.format(Locale.US, "0x%02x", byte) }
}
