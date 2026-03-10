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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.isLora
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.uuid.Uuid

/** Implementation of [MeshMessageProcessor] that handles raw radio messages and prepares mesh packets for routing. */
@Suppress("TooManyFunctions")
@Single
class MeshMessageProcessorImpl(
    private val nodeManager: NodeManager,
    private val serviceRepository: ServiceRepository,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val router: Lazy<MeshRouter>,
    private val fromRadioDispatcher: FromRadioPacketHandler,
) : MeshMessageProcessor {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mapsMutex = Mutex()
    private val logUuidByPacketId = mutableMapOf<Int, String>()
    private val logInsertJobByPacketId = mutableMapOf<Int, Job>()

    private val earlyMutex = Mutex()
    private val earlyReceivedPackets = kotlin.collections.ArrayDeque<MeshPacket>()
    private val maxEarlyPacketBuffer = 10240

    override fun clearEarlyPackets() {
        scope.launch { earlyMutex.withLock { earlyReceivedPackets.clear() } }
    }

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        nodeManager.isNodeDbReady
            .onEach { ready ->
                if (ready) {
                    flushEarlyReceivedPackets("dbReady")
                }
            }
            .launchIn(scope)
    }

    override fun handleFromRadio(bytes: ByteArray, myNodeNum: Int?) {
        runCatching { FromRadio.ADAPTER.decode(bytes) }
            .onSuccess { proto -> processFromRadio(proto, myNodeNum) }
            .onFailure { primaryException ->
                runCatching {
                    val logRecord = LogRecord.ADAPTER.decode(bytes)
                    processFromRadio(FromRadio(log_record = logRecord), myNodeNum)
                }
                    .onFailure { _ ->
                        Logger.e(primaryException) {
                            "Failed to parse radio packet (len=${bytes.size}). " + "Not a valid FromRadio or LogRecord."
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
                received_date = nowMillis,
                raw_message = message,
                fromRadio = proto,
            ),
        )
    }

    override fun handleReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?) {
        val rxTime =
            if (packet.rx_time == 0) {
                nowSeconds.toInt()
            } else {
                packet.rx_time
            }
        val preparedPacket = packet.copy(rx_time = rxTime)

        if (nodeManager.isNodeDbReady.value) {
            processReceivedMeshPacket(preparedPacket, myNodeNum)
        } else {
            scope.launch {
                earlyMutex.withLock {
                    val queueSize = earlyReceivedPackets.size
                    if (queueSize >= maxEarlyPacketBuffer) {
                        earlyReceivedPackets.removeFirstOrNull()
                    }
                    earlyReceivedPackets.addLast(preparedPacket)
                }
            }
        }
    }

    private fun flushEarlyReceivedPackets(reason: String) {
        scope.launch {
            val packets =
                earlyMutex.withLock {
                    if (earlyReceivedPackets.isEmpty()) return@withLock emptyList<MeshPacket>()
                    val list = earlyReceivedPackets.toList()
                    earlyReceivedPackets.clear()
                    list
                }
            if (packets.isEmpty()) return@launch

            Logger.d { "replayEarlyPackets reason=$reason count=${packets.size}" }
            val myNodeNum = nodeManager.myNodeNum
            packets.forEach { processReceivedMeshPacket(it, myNodeNum) }
        }
    }

    @Suppress("LongMethod")
    private fun processReceivedMeshPacket(packet: MeshPacket, myNodeNum: Int?) {
        val decoded = packet.decoded ?: return
        val log =
            MeshLog(
                uuid = Uuid.random().toString(),
                message_type = "Packet",
                received_date = nowMillis,
                raw_message = packet.toString(),
                fromNum = if (packet.from == myNodeNum) MeshLog.NODE_NUM_LOCAL else packet.from,
                portNum = decoded.portnum.value,
                fromRadio = FromRadio(packet = packet),
            )
        val logJob = insertMeshLog(log)

        scope.launch {
            mapsMutex.withLock {
                logInsertJobByPacketId[packet.id] = logJob
                logUuidByPacketId[packet.id] = log.uuid
            }
        }

        scope.handledLaunch { serviceRepository.emitMeshPacket(packet) }

        myNodeNum?.let { myNum ->
            val from = packet.from
            val isOtherNode = myNum != from
            nodeManager.updateNode(myNum, withBroadcast = isOtherNode) { node: Node ->
                node.copy(lastHeard = nowSeconds.toInt())
            }
            nodeManager.updateNode(from, withBroadcast = false, channel = packet.channel) { node: Node ->
                val viaMqtt = packet.via_mqtt == true
                val isDirect = packet.hop_start == packet.hop_limit

                var snr = node.snr
                var rssi = node.rssi
                if (isDirect && packet.isLora() && !viaMqtt) {
                    snr = packet.rx_snr
                    rssi = packet.rx_rssi
                }

                val hopsAway =
                    if (decoded.portnum == PortNum.RANGE_TEST_APP) {
                        0
                    } else if (viaMqtt) {
                        -1
                    } else if (packet.hop_start == 0 && (decoded.bitfield ?: 0) == 0) {
                        -1
                    } else if (packet.hop_limit > packet.hop_start) {
                        -1
                    } else {
                        packet.hop_start - packet.hop_limit
                    }

                node.copy(
                    lastHeard = packet.rx_time,
                    viaMqtt = viaMqtt,
                    lastTransport = packet.transport_mechanism.value,
                    snr = snr,
                    rssi = rssi,
                    hopsAway = hopsAway,
                )
            }

            try {
                router.value.dataHandler.handleReceivedData(packet, myNum, log.uuid, logJob)
            } finally {
                scope.launch {
                    mapsMutex.withLock {
                        logUuidByPacketId.remove(packet.id)
                        logInsertJobByPacketId.remove(packet.id)
                    }
                }
            }
        }
    }

    private fun insertMeshLog(log: MeshLog): Job = scope.handledLaunch { meshLogRepository.value.insert(log) }
}
