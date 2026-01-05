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
import com.google.protobuf.ByteString
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
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.AppOnlyProtos.ChannelSet
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.MeshProtos.MeshPacket
import org.meshtastic.proto.Portnums
import org.meshtastic.proto.TelemetryProtos
import org.meshtastic.proto.position
import org.meshtastic.proto.telemetry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours

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
    private val sessionPasskey = AtomicReference(ByteString.EMPTY)
    private val offlineSentPackets = CopyOnWriteArrayList<DataPacket>()
    val tracerouteStartTimes = ConcurrentHashMap<Int, Long>()
    val neighborInfoStartTimes = ConcurrentHashMap<Int, Long>()

    private val localConfig = MutableStateFlow(LocalConfig.getDefaultInstance())
    private val channelSet = MutableStateFlow(ChannelSet.getDefaultInstance())

    @Volatile var lastNeighborInfo: MeshProtos.NeighborInfo? = null

    private val rememberDataType =
        setOf(
            Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
            Portnums.PortNum.ALERT_APP_VALUE,
            Portnums.PortNum.WAYPOINT_APP_VALUE,
        )

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

    fun setSessionPasskey(key: ByteString) {
        sessionPasskey.set(key)
    }

    private fun getHopLimit(): Int = localConfig.value.lora.hopLimit

    private fun getAdminChannelIndex(toNum: Int): Int {
        val myNum = nodeManager?.myNodeNum ?: return 0
        val myNode = nodeManager.nodeDBbyNodeNum[myNum]
        val destNode = nodeManager.nodeDBbyNodeNum[toNum]

        val adminChannelIndex =
            when {
                myNum == toNum -> 0
                myNode?.hasPKC == true && destNode?.hasPKC == true -> DataPacket.PKC_CHANNEL_INDEX
                else ->
                    channelSet.value.settingsList
                        .indexOfFirst { it.name.equals(ADMIN_CHANNEL_NAME, ignoreCase = true) }
                        .coerceAtLeast(0)
            }
        return adminChannelIndex
    }

    fun sendData(p: DataPacket) {
        if (p.id == 0) p.id = generatePacketId()
        val bytes = p.bytes ?: ByteArray(0)
        require(p.dataType != 0) { "Port numbers must be non-zero!" }
        if (bytes.size >= MeshProtos.Constants.DATA_PAYLOAD_LEN_VALUE) {
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
        val meshPacket =
            newMeshPacketTo(p.to ?: DataPacket.ID_BROADCAST).buildMeshPacket(
                id = p.id,
                wantAck = p.wantAck,
                hopLimit = if (p.hopLimit > 0) p.hopLimit else getHopLimit(),
                channel = p.channel,
            ) {
                portnumValue = p.dataType
                payload = ByteString.copyFrom(p.bytes ?: ByteArray(0))
                p.replyId?.let { if (it != 0) replyId = it }
                if (p.emoji != 0) emoji = p.emoji
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
        initFn: AdminProtos.AdminMessage.Builder.() -> Unit,
    ) {
        val packet =
            newMeshPacketTo(destNum).buildAdminPacket(id = requestId, wantResponse = wantResponse, initFn = initFn)
        packetHandler?.sendToRadio(packet)
    }

    fun sendPosition(pos: MeshProtos.Position, destNum: Int? = null, wantResponse: Boolean = false) {
        val myNum = nodeManager?.myNodeNum ?: return
        val idNum = destNum ?: myNum
        Logger.d { "Sending our position/time to=$idNum ${Position(pos)}" }

        if (!localConfig.value.position.fixedPosition) {
            nodeManager.handleReceivedPosition(myNum, myNum, pos)
        }

        packetHandler?.sendToRadio(
            newMeshPacketTo(idNum).buildMeshPacket(
                channel = if (destNum == null) 0 else nodeManager.nodeDBbyNodeNum[destNum]?.channel ?: 0,
                priority = MeshPacket.Priority.BACKGROUND,
            ) {
                portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                payload = pos.toByteString()
                this.wantResponse = wantResponse
            },
        )
    }

    fun requestPosition(destNum: Int, currentPosition: Position) {
        val meshPosition = position {
            latitudeI = Position.degI(currentPosition.latitude)
            longitudeI = Position.degI(currentPosition.longitude)
            altitude = currentPosition.altitude
            time = (System.currentTimeMillis() / TIME_MS_TO_S).toInt()
        }
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(
                channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
                priority = MeshPacket.Priority.BACKGROUND,
            ) {
                portnumValue = Portnums.PortNum.POSITION_APP_VALUE
                payload = meshPosition.toByteString()
                wantResponse = true
            },
        )
    }

    fun setFixedPosition(destNum: Int, pos: Position) {
        val meshPos = position {
            latitudeI = Position.degI(pos.latitude)
            longitudeI = Position.degI(pos.longitude)
            altitude = pos.altitude
        }
        sendAdmin(destNum) {
            if (pos != Position(0.0, 0.0, 0)) {
                setFixedPosition = meshPos
            } else {
                removeFixedPosition = true
            }
        }
        nodeManager?.handleReceivedPosition(destNum, nodeManager.myNodeNum ?: 0, meshPos)
    }

    fun requestUserInfo(destNum: Int) {
        val myNum = nodeManager?.myNodeNum ?: return
        val myNode = nodeManager.getOrCreateNodeInfo(myNum)
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(channel = nodeManager.nodeDBbyNodeNum[destNum]?.channel ?: 0) {
                portnumValue = Portnums.PortNum.NODEINFO_APP_VALUE
                wantResponse = true
                payload = myNode.user.toByteString()
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
                portnumValue = Portnums.PortNum.TRACEROUTE_APP_VALUE
                wantResponse = true
            },
        )
    }

    fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        val type = TelemetryType.entries.getOrNull(typeValue) ?: TelemetryType.DEVICE
        val telemetryRequest = telemetry {
            when (type) {
                TelemetryType.ENVIRONMENT ->
                    environmentMetrics = TelemetryProtos.EnvironmentMetrics.getDefaultInstance()
                TelemetryType.AIR_QUALITY -> airQualityMetrics = TelemetryProtos.AirQualityMetrics.getDefaultInstance()
                TelemetryType.POWER -> powerMetrics = TelemetryProtos.PowerMetrics.getDefaultInstance()
                TelemetryType.LOCAL_STATS -> localStats = TelemetryProtos.LocalStats.getDefaultInstance()
                TelemetryType.DEVICE -> deviceMetrics = TelemetryProtos.DeviceMetrics.getDefaultInstance()
            }
        }
        packetHandler?.sendToRadio(
            newMeshPacketTo(destNum).buildMeshPacket(
                id = requestId,
                channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
            ) {
                portnumValue = Portnums.PortNum.TELEMETRY_APP_VALUE
                payload = telemetryRequest.toByteString()
                wantResponse = true
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
                        MeshProtos.NeighborInfo.newBuilder()
                            .setNodeId(myNum)
                            .setLastSentById(myNum)
                            .setNodeBroadcastIntervalSecs(oneHour)
                            .addNeighbors(
                                MeshProtos.Neighbor.newBuilder()
                                    .setNodeId(0) // Dummy node ID that can be intercepted
                                    .setSnr(0f)
                                    .setLastRxTime((System.currentTimeMillis() / TIME_MS_TO_S).toInt())
                                    .setNodeBroadcastIntervalSecs(oneHour)
                                    .build(),
                            )
                            .build()
                    }

            // Send the neighbor info from our connected radio to ourselves (simulated)
            packetHandler?.sendToRadio(
                newMeshPacketTo(destNum).buildMeshPacket(
                    wantAck = true,
                    id = requestId,
                    channel = nodeManager?.nodeDBbyNodeNum?.get(destNum)?.channel ?: 0,
                ) {
                    portnumValue = Portnums.PortNum.NEIGHBORINFO_APP_VALUE
                    payload = neighborInfoToSend.toByteString()
                    wantResponse = true
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
                    portnumValue = Portnums.PortNum.NEIGHBORINFO_APP_VALUE
                    wantResponse = true
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

    private fun newMeshPacketTo(toId: String): MeshPacket.Builder {
        val destNum = resolveNodeNum(toId)
        return newMeshPacketTo(destNum)
    }

    private fun newMeshPacketTo(destNum: Int): MeshPacket.Builder = MeshPacket.newBuilder().apply { to = destNum }

    private fun MeshPacket.Builder.buildMeshPacket(
        wantAck: Boolean = false,
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        hopLimit: Int = 0,
        channel: Int = 0,
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        initFn: MeshProtos.Data.Builder.() -> Unit,
    ): MeshPacket {
        this.id = id
        this.wantAck = wantAck
        this.hopLimit = if (hopLimit > 0) hopLimit else getHopLimit()
        this.priority = priority

        if (channel == DataPacket.PKC_CHANNEL_INDEX) {
            pkiEncrypted = true
            nodeManager?.nodeDBbyNodeNum?.get(to)?.user?.publicKey?.let { publicKey = it }
        } else {
            this.channel = channel
        }

        this.decoded = MeshProtos.Data.newBuilder().apply(initFn).build()
        return build()
    }

    private fun MeshPacket.Builder.buildAdminPacket(
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        wantResponse: Boolean = false,
        initFn: AdminProtos.AdminMessage.Builder.() -> Unit,
    ): MeshPacket =
        buildMeshPacket(
            id = id,
            wantAck = true,
            channel = getAdminChannelIndex(to),
            priority = MeshPacket.Priority.RELIABLE,
        ) {
            this.wantResponse = wantResponse
            portnumValue = Portnums.PortNum.ADMIN_APP_VALUE
            payload =
                AdminProtos.AdminMessage.newBuilder()
                    .apply(initFn)
                    .setSessionPasskey(sessionPasskey.get())
                    .build()
                    .toByteString()
        }

    companion object {
        private const val PACKET_ID_MASK = 0xffffffffL
        private const val PACKET_ID_SHIFT_BITS = 32
        private const val TIME_MS_TO_S = 1000L

        private const val ADMIN_CHANNEL_NAME = "admin"
        private const val NODE_ID_PREFIX = "!"
        private const val NODE_ID_START_INDEX = 1
        private const val HEX_RADIX = 16
    }
}
