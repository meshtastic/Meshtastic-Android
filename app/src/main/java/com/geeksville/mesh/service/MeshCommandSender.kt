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

import android.os.RemoteException
import androidx.annotation.VisibleForTesting
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Constants
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HealthMetrics
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours
import org.meshtastic.proto.Position as ProtoPosition

@Suppress("TooManyFunctions")
@Singleton
class MeshCommandSender
@Inject
constructor(
    private val packetHandler: PacketHandler?,
    private val nodeManager: MeshNodeManager?,
    private val connectionStateHolder: ConnectionStateHandler?,
    private val radioConfigRepository: RadioConfigRepository?,
) {
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val currentPacketId = AtomicLong(java.util.Random(System.currentTimeMillis()).nextLong().absoluteValue)
    private val sessionPasskey = AtomicReference(okio.ByteString.EMPTY)
    private val offlineSentPackets = CopyOnWriteArrayList<DataPacket>()
    val tracerouteStartTimes = ConcurrentHashMap<Int, Long>()
    val neighborInfoStartTimes = ConcurrentHashMap<Int, Long>()

    private val localConfig = MutableStateFlow(LocalConfig())
    private val channelSet = MutableStateFlow(ChannelSet())

    @Volatile var lastNeighborInfo: NeighborInfo? = null

    private val rememberDataType =
        setOf(PortNum.TEXT_MESSAGE_APP.value, PortNum.ALERT_APP.value, PortNum.WAYPOINT_APP.value)

    fun start(scope: CoroutineScope) {
        this.scope = scope
        radioConfigRepository?.localConfigFlow?.onEach { localConfig.value = it }?.launchIn(scope)
        radioConfigRepository?.channelSetFlow?.onEach { channelSet.value = it }?.launchIn(scope)
    }

    @VisibleForTesting internal constructor() : this(null, null, null, null)

    fun getCurrentPacketId(): Long = currentPacketId.get()

    fun generatePacketId(): Int {
        val numPacketIds = ((1L shl PACKET_ID_SHIFT_BITS) - 1)
        val next = currentPacketId.incrementAndGet() and PACKET_ID_MASK
        return ((next % numPacketIds) + 1L).toInt()
    }

    fun setSessionPasskey(key: okio.ByteString) {
        sessionPasskey.set(key)
    }

    private fun getHopLimit(): Int = localConfig.value.lora?.hop_limit?.takeIf { it > 0 } ?: DEFAULT_HOP_LIMIT

    private fun getAdminChannelIndex(toNum: Int): Int {
        val myNum = nodeManager?.myNodeNum ?: return 0
        val myNode = nodeManager.nodeDBbyNodeNum[myNum]
        val destNode = nodeManager.nodeDBbyNodeNum[toNum]

        val adminChannelIndex =
            when {
                myNum == toNum -> 0
                myNode?.hasPKC == true && destNode?.hasPKC == true -> DataPacket.PKC_CHANNEL_INDEX
                else ->
                    channelSet.value.settings
                        .indexOfFirst { it.name.equals(ADMIN_CHANNEL_NAME, ignoreCase = true) }
                        .coerceAtLeast(0)
            }
        return adminChannelIndex
    }

    fun sendData(p: DataPacket) {
        if (p.id == 0) p.id = generatePacketId()
        val payloadSize = p.bytes?.size ?: 0
        require(p.dataType != 0) { "Port numbers must be non-zero!" }
        if (payloadSize >= Constants.DATA_PAYLOAD_LEN.value) {
            p.status = MessageStatus.ERROR
            throw RemoteException("Message too long")
        } else {
            p.status = MessageStatus.QUEUED
        }

        if (connectionStateHolder?.connectionState?.value == ConnectionState.Connected) {
            try {
                sendNow(p)
            } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
                Logger.e(ex) { "Error sending message, so enqueueing" }
                enqueueForSending(p)
            }
        } else {
            enqueueForSending(p)
        }
    }

    private fun sendNow(p: DataPacket) {
        val portNum = PortNum.fromValue(p.dataType) ?: PortNum.UNKNOWN_APP
        val meshPacket =
            newMeshPacketTo(p.to ?: DataPacket.ID_BROADCAST).buildMeshPacket(
                id = p.id,
                wantAck = p.wantAck,
                hopLimit = if (p.hopLimit > 0) p.hopLimit else getHopLimit(),
                channel = p.channel,
            ) {
                copy(
                    portnum = portNum,
                    payload = p.bytes ?: okio.ByteString.EMPTY,
                    reply_id = p.replyId ?: 0,
                    emoji = p.emoji,
                )
            }
        p.time = System.currentTimeMillis()
        packetHandler?.sendToRadio(meshPacket)
    }

    private fun enqueueForSending(p: DataPacket) {
        if (p.dataType in rememberDataType) {
            offlineSentPackets.add(p)
        }
    }

    fun processQueuedPackets() {
        val sentPackets = mutableListOf<DataPacket>()
        offlineSentPackets.forEach { p ->
            try {
                sendNow(p)
                sentPackets.add(p)
            } catch (@Suppress("TooGenericExceptionCaught") ex: Exception) {
                Logger.e(ex) { "Error sending queued message:" }
            }
        }
        offlineSentPackets.removeAll(sentPackets)
    }

    fun sendAdmin(
        destNum: Int,
        requestId: Int = generatePacketId(),
        wantResponse: Boolean = false,
        initFn: AdminMessage.() -> AdminMessage,
    ) {
        val packet =
            newMeshPacketTo(destNum).buildAdminPacket(id = requestId, wantResponse = wantResponse, initFn = initFn)
        packetHandler?.sendToRadio(packet)
    }

    fun sendPosition(pos: ProtoPosition, destNum: Int? = null, wantResponse: Boolean = false) {
        val myNum = nodeManager?.myNodeNum ?: return
        val idNum = destNum ?: myNum
        Logger.d { "Sending our position/time to=$idNum ${Position(pos)}" }

        if (localConfig.value.position?.fixed_position != true) {
            nodeManager.handleReceivedPosition(myNum, myNum, pos)
        }

        packetHandler?.sendToRadio(
            newMeshPacketTo(idNum).buildMeshPacket(
                channel = if (destNum == null) 0 else nodeManager.nodeDBbyNodeNum[destNum]?.channel ?: 0,
                priority = MeshPacket.Priority.BACKGROUND,
            ) {
                copy(
                    portnum = PortNum.POSITION_APP,
                    payload = ProtoPosition.ADAPTER.encodeByteString(pos),
                    want_response = wantResponse,
                )
            },
        )
    }

    fun requestPosition(destNum: Int, currentPosition: Position) {
        val meshPosition =
            ProtoPosition(
                latitude_i = Position.degI(currentPosition.latitude),
                longitude_i = Position.degI(currentPosition.longitude),
                altitude = currentPosition.altitude,
                time = (System.currentTimeMillis() / TIME_MS_TO_S).toInt(),
            )
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(
                channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
                priority = MeshPacket.Priority.BACKGROUND,
            ) {
                copy(
                    portnum = PortNum.POSITION_APP,
                    payload = ProtoPosition.ADAPTER.encodeByteString(meshPosition),
                    want_response = true,
                )
            },
        )
    }

    fun setFixedPosition(destNum: Int, pos: Position) {
        val meshPos =
            ProtoPosition(
                latitude_i = Position.degI(pos.latitude),
                longitude_i = Position.degI(pos.longitude),
                altitude = pos.altitude,
            )
        sendAdmin(destNum) {
            if (pos != Position(0.0, 0.0, 0)) {
                copy(set_fixed_position = meshPos)
            } else {
                copy(remove_fixed_position = true)
            }
        }
        nodeManager?.handleReceivedPosition(destNum, nodeManager.myNodeNum ?: 0, meshPos)
    }

    fun requestUserInfo(destNum: Int) {
        val myNum = nodeManager?.myNodeNum ?: return
        val myNode = nodeManager.getOrCreateNodeInfo(myNum)
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(channel = nodeManager.nodeDBbyNodeNum[destNum]?.channel ?: 0) {
                copy(
                    portnum = PortNum.NODEINFO_APP,
                    want_response = true,
                    payload = User.ADAPTER.encodeByteString(myNode.user),
                )
            },
        )
    }

    fun requestTraceroute(requestId: Int, destNum: Int) {
        tracerouteStartTimes[requestId] = System.currentTimeMillis()
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(
                wantAck = true,
                id = requestId,
                channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
            ) {
                copy(portnum = PortNum.TRACEROUTE_APP, want_response = true)
            },
        )
    }

    fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        val type = TelemetryType.entries.getOrNull(typeValue) ?: TelemetryType.DEVICE
        val telemetryRequest =
            Telemetry(
                device_metrics = if (type == TelemetryType.DEVICE) DeviceMetrics() else null,
                environment_metrics = if (type == TelemetryType.ENVIRONMENT) EnvironmentMetrics() else null,
                air_quality_metrics =
                if (type == TelemetryType.AIR_QUALITY) org.meshtastic.proto.AirQualityMetrics() else null,
                power_metrics = if (type == TelemetryType.POWER) PowerMetrics() else null,
                local_stats = if (type == TelemetryType.LOCAL_STATS) LocalStats() else null,
                health_metrics = if (type == TelemetryType.HEALTH) HealthMetrics() else null,
            )
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(
                id = requestId,
                channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
            ) {
                copy(
                    portnum = PortNum.TELEMETRY_APP,
                    payload = Telemetry.ADAPTER.encodeByteString(telemetryRequest),
                    want_response = true,
                )
            },
        )
    }

    fun requestNeighborInfo(requestId: Int, destNum: Int) {
        neighborInfoStartTimes[requestId] = System.currentTimeMillis()
        val myNum = nodeManager?.myNodeNum ?: 0
        if (destNum == myNum) {
            val neighborInfoToSend =
                lastNeighborInfo
                    ?: run {
                        val oneHour = 1.hours.inWholeMinutes.toInt()
                        Logger.d { "No stored neighbor info from connected radio, sending dummy data" }
                        NeighborInfo(
                            node_id = myNum,
                            last_sent_by_id = myNum,
                            node_broadcast_interval_secs = oneHour,
                            neighbors =
                            listOf(
                                Neighbor(
                                    node_id = 0, // Dummy node ID that can be intercepted
                                    snr = 0f,
                                    last_rx_time = (System.currentTimeMillis() / TIME_MS_TO_S).toInt(),
                                    node_broadcast_interval_secs = oneHour,
                                ),
                            ),
                        )
                    }

            // Send the neighbor info from our connected radio to ourselves (simulated)
            packetHandler?.sendToRadio(
                newMeshPacketTo(destNum).buildMeshPacket(
                    wantAck = true,
                    id = requestId,
                    channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
                ) {
                    copy(
                        portnum = PortNum.NEIGHBORINFO_APP,
                        payload = NeighborInfo.ADAPTER.encodeByteString(neighborInfoToSend),
                        want_response = true,
                    )
                },
            )
        } else {
            // Send request to remote
            packetHandler?.sendToRadio(
                newMeshPacketTo(destNum).buildMeshPacket(
                    wantAck = true,
                    id = requestId,
                    channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
                ) {
                    copy(portnum = PortNum.NEIGHBORINFO_APP, want_response = true)
                },
            )
        }
    }

    @VisibleForTesting
    internal fun resolveNodeNum(toId: String): Int = when (toId) {
        DataPacket.ID_BROADCAST -> DataPacket.NODENUM_BROADCAST
        else -> {
            val numericNum =
                if (toId.startsWith(NODE_ID_PREFIX)) {
                    toId.substring(NODE_ID_START_INDEX).toLongOrNull(HEX_RADIX)?.toInt()
                } else {
                    null
                }
            numericNum
                ?: nodeManager?.nodeDBbyID?.get(toId)?.num
                ?: throw IllegalArgumentException("Unknown node ID $toId")
        }
    }

    private fun newMeshPacketTo(toId: String): MeshPacket {
        val destNum = resolveNodeNum(toId)
        return newMeshPacketTo(destNum)
    }

    private fun newMeshPacketTo(destNum: Int): MeshPacket = MeshPacket(to = destNum)

    private fun MeshPacket.buildMeshPacket(
        wantAck: Boolean = false,
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        hopLimit: Int = 0,
        channel: Int = 0,
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        initFn: Data.() -> Data,
    ): MeshPacket {
        var updated =
            this.copy(
                id = id,
                want_ack = wantAck,
                hop_limit = if (hopLimit > 0) hopLimit else getHopLimit(),
                priority = priority,
            )

        if (channel == DataPacket.PKC_CHANNEL_INDEX) {
            updated =
                updated.copy(
                    pki_encrypted = true,
                    public_key = nodeManager?.nodeDBbyNodeNum?.get(to)?.user?.public_key ?: okio.ByteString.EMPTY,
                )
        } else {
            updated = updated.copy(channel = channel)
        }

        return updated.copy(decoded = Data().initFn())
    }

    private fun MeshPacket.buildAdminPacket(
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        wantResponse: Boolean = false,
        initFn: AdminMessage.() -> AdminMessage,
    ): MeshPacket =
        buildMeshPacket(
            id = id,
            wantAck = true,
            channel = getAdminChannelIndex(to),
            priority = MeshPacket.Priority.RELIABLE,
        ) {
            copy(
                want_response = wantResponse,
                portnum = PortNum.ADMIN_APP,
                payload =
                AdminMessage.ADAPTER.encodeByteString(
                    AdminMessage().initFn().copy(session_passkey = sessionPasskey.get()),
                ),
            )
        }

    companion object {
        private const val PACKET_ID_MASK = 0xffffffffL
        private const val PACKET_ID_SHIFT_BITS = 32
        private const val TIME_MS_TO_S = 1000L

        private const val ADMIN_CHANNEL_NAME = "admin"
        private const val NODE_ID_PREFIX = "!"
        private const val NODE_ID_START_INDEX = 1
        private const val HEX_RADIX = 16

        private const val DEFAULT_HOP_LIMIT = 3
    }
}
