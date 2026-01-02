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
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.FromRadio.PayloadVariantCase
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.fromRadio
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

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
        runCatching { MeshProtos.FromRadio.parseFrom(bytes) }
            .onSuccess { proto ->
                if (proto.payloadVariantCase == PayloadVariantCase.PAYLOADVARIANT_NOT_SET) {
                    Logger.w { "Received FromRadio with PAYLOADVARIANT_NOT_SET. rawBytes=${bytes.toHexString()}" }
                }
                processFromRadio(proto, myNodeNum)
            }
            .onFailure { primaryException ->
                runCatching {
                    val logRecord = MeshProtos.LogRecord.parseFrom(bytes)
                    processFromRadio(fromRadio { this.logRecord = logRecord }, myNodeNum)
                }
                    .onFailure { _ ->
                        Logger.e(primaryException) {
                            "Failed to parse radio packet (len=${bytes.size} contents=${bytes.toHexString()}). " +
                                "Not a valid FromRadio or LogRecord."
                        }
                    }
            }
    }

    private fun processFromRadio(proto: MeshProtos.FromRadio, myNodeNum: Int?) {
        // Audit log every incoming variant
        logVariant(proto)

        if (proto.payloadVariantCase == PayloadVariantCase.PACKET) {
            handleReceivedMeshPacket(proto.packet, myNodeNum)
        } else {
            fromRadioDispatcher.handleFromRadio(proto)
        }
    }

    private fun logVariant(proto: MeshProtos.FromRadio) {
        val (type, message) =
            when (proto.payloadVariantCase) {
                PayloadVariantCase.LOG_RECORD -> "LogRecord" to proto.logRecord.toString()
                PayloadVariantCase.REBOOTED -> "Rebooted" to proto.rebooted.toString()
                PayloadVariantCase.XMODEMPACKET -> "XmodemPacket" to proto.xmodemPacket.toString()
                PayloadVariantCase.DEVICEUICONFIG -> "DeviceUIConfig" to proto.deviceuiConfig.toString()
                PayloadVariantCase.FILEINFO -> "FileInfo" to proto.fileInfo.toString()
                else -> return // Other variants (Config, NodeInfo, etc.) are handled by dispatcher but not necessarily
                // logged as raw strings here
            }

        insertMeshLog(
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = type,
                received_date = System.currentTimeMillis(),
                raw_message = message,
                fromRadio = proto,
            ),
        )
    }

    fun handleReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?) {
        val rxTime =
            if (packet.rxTime == 0) (System.currentTimeMillis().milliseconds.inWholeSeconds).toInt() else packet.rxTime
        val preparedPacket = packet.toBuilder().setRxTime(rxTime).build()

        if (nodeManager.isNodeDbReady.value) {
            processReceivedMeshPacket(preparedPacket, myNodeNum)
        } else {
            synchronized(earlyReceivedPackets) {
                val queueSize = earlyReceivedPackets.size
                if (queueSize >= maxEarlyPacketBuffer) {
                    val dropped = earlyReceivedPackets.removeFirst()
                    historyLog(Log.WARN) {
                        val portLabel =
                            if (dropped.hasDecoded()) {
                                Portnums.PortNum.forNumber(dropped.decoded.portnumValue)?.name
                                    ?: dropped.decoded.portnumValue.toString()
                            } else {
                                "unknown"
                            }
                        "dropEarlyPacket bufferFull size=$queueSize id=${dropped.id} port=$portLabel"
                    }
                }
                earlyReceivedPackets.addLast(preparedPacket)
                val portLabel =
                    if (preparedPacket.hasDecoded()) {
                        Portnums.PortNum.forNumber(preparedPacket.decoded.portnumValue)?.name
                            ?: preparedPacket.decoded.portnumValue.toString()
                    } else {
                        "unknown"
                    }
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
        if (!packet.hasDecoded()) return
        val log =
            MeshLog(
                uuid = UUID.randomUUID().toString(),
                message_type = "Packet",
                received_date = System.currentTimeMillis(),
                raw_message = packet.toString(),
                fromNum = packet.from,
                portNum = packet.decoded.portnumValue,
                fromRadio = fromRadio { this.packet = packet },
            )
        val logJob = insertMeshLog(log)
        logInsertJobByPacketId[packet.id] = logJob
        logUuidByPacketId[packet.id] = log.uuid

        scope.handledLaunch { serviceRepository.emitMeshPacket(packet) }

        myNodeNum?.let { myNum ->
            val isOtherNode = myNum != packet.from
            nodeManager.updateNodeInfo(myNum, withBroadcast = isOtherNode) {
                it.lastHeard = (System.currentTimeMillis().milliseconds.inWholeSeconds).toInt()
            }
            nodeManager.updateNodeInfo(packet.from, withBroadcast = false, channel = packet.channel) {
                it.lastHeard = packet.rxTime
                it.snr = packet.rxSnr
                it.rssi = packet.rxRssi
                it.hopsAway =
                    if (packet.decoded.portnumValue == Portnums.PortNum.RANGE_TEST_APP_VALUE) {
                        0
                    } else if (packet.hopStart == 0 || packet.hopLimit > packet.hopStart) {
                        -1
                    } else {
                        packet.hopStart - packet.hopLimit
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
