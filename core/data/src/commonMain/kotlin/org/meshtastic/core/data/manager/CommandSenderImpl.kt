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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.isWithinSizeLimit
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.Constants
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.HostMetrics
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import org.meshtastic.proto.Position as ProtoPosition

@Suppress("TooManyFunctions", "CyclomaticComplexMethod", "LongParameterList")
@Single
class CommandSenderImpl(
    private val packetHandler: PacketHandler,
    private val nodeManager: NodeManager,
    private val radioConfigRepository: RadioConfigRepository,
    private val tracerouteHandler: TracerouteHandler,
    private val neighborInfoHandler: NeighborInfoHandler,
    private val sessionManager: SessionManager,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : CommandSender {
    private val currentPacketId = atomic(Random(nowMillis).nextLong().absoluteValue)

    private val localConfig = MutableStateFlow(LocalConfig())
    private val channelSet = MutableStateFlow(ChannelSet())

    init {
        radioConfigRepository.localConfigFlow.onEach { localConfig.value = it }.launchIn(scope)
        radioConfigRepository.channelSetFlow.onEach { channelSet.value = it }.launchIn(scope)
    }

    override fun getCachedLocalConfig(): LocalConfig = localConfig.value

    override fun getCachedChannelSet(): ChannelSet = channelSet.value

    override fun getCurrentPacketId(): Long = currentPacketId.value

    override fun generatePacketId(): Int {
        val numPacketIds = ((1L shl PACKET_ID_SHIFT_BITS) - 1)
        val next = currentPacketId.incrementAndGet() and PACKET_ID_MASK
        return ((next % numPacketIds) + 1L).toInt()
    }

    private fun computeHopLimit(): Int = (localConfig.value.lora?.hop_limit ?: 0).takeIf { it > 0 } ?: DEFAULT_HOP_LIMIT

    /**
     * Resolves the correct channel index for sending a packet to [toNum].
     *
     * PKI encryption ([DataPacket.PKC_CHANNEL_INDEX]) is only used for **admin** packets, where end-to-end encryption
     * is appropriate. Protocol-level requests (traceroute, telemetry, position, nodeinfo, neighborinfo) must NOT use
     * PKI because relay nodes need to read and/or modify the inner payload (e.g. traceroute appends each hop's node
     * number). These requests fall back to the node's heard-on channel.
     */
    private fun getAdminChannelIndex(toNum: Int): Int {
        val myNum = nodeManager.myNodeNum.value ?: return 0
        val myNode = nodeManager.nodeDBbyNodeNum[myNum]
        val destNode = nodeManager.nodeDBbyNodeNum[toNum]

        return when {
            myNum == toNum -> 0
            myNode?.hasPKC == true && destNode?.hasPKC == true -> DataPacket.PKC_CHANNEL_INDEX
            else ->
                channelSet.value.settings
                    .indexOfFirst { it.name.equals(ADMIN_CHANNEL_NAME, ignoreCase = true) }
                    .coerceAtLeast(0)
        }
    }

    /**
     * Returns the heard-on channel for a non-admin request to [toNum]. Does NOT use PKI — protocol-level requests need
     * clear inner payloads.
     */
    private fun getChannelIndex(toNum: Int): Int = nodeManager.nodeDBbyNodeNum[toNum]?.channel ?: 0

    override fun sendData(p: DataPacket) {
        if (p.id == 0) p.id = generatePacketId()
        val bytes = p.bytes ?: ByteString.EMPTY
        require(p.dataType != 0) { "Port numbers must be non-zero!" }

        // Use Wire extension for accurate size validation
        val data =
            Data(
                portnum = PortNum.fromValue(p.dataType) ?: PortNum.UNKNOWN_APP,
                payload = bytes,
                reply_id = p.replyId ?: 0,
                emoji = p.emoji,
            )

        if (!Data.ADAPTER.isWithinSizeLimit(data, Constants.DATA_PAYLOAD_LEN.value)) {
            val actualSize = Data.ADAPTER.encodedSize(data)
            p.status = MessageStatus.ERROR
            error("Message too long: $actualSize bytes")
        } else {
            p.status = MessageStatus.QUEUED
        }

        sendNow(p)
    }

    private fun sendNow(p: DataPacket) {
        val meshPacket =
            buildMeshPacket(
                to = resolveNodeNum(p.to ?: DataPacket.ID_BROADCAST),
                id = p.id,
                wantAck = p.wantAck,
                hopLimit = if (p.hopLimit > 0) p.hopLimit else computeHopLimit(),
                channel = p.channel,
                decoded =
                Data(
                    portnum = PortNum.fromValue(p.dataType) ?: PortNum.UNKNOWN_APP,
                    payload = p.bytes ?: ByteString.EMPTY,
                    reply_id = p.replyId ?: 0,
                    emoji = p.emoji,
                ),
            )
        p.time = nowMillis
        packetHandler.sendToRadio(meshPacket)
    }

    override fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) {
        val adminMsg = initFn().copy(session_passkey = sessionManager.getPasskey(destNum))
        val packet =
            buildAdminPacket(to = destNum, id = requestId, wantResponse = wantResponse, adminMessage = adminMsg)
        packetHandler.sendToRadio(packet)
    }

    override suspend fun sendAdminAwait(
        destNum: Int,
        requestId: Int,
        wantResponse: Boolean,
        initFn: () -> AdminMessage,
    ): Boolean {
        val adminMsg = initFn().copy(session_passkey = sessionManager.getPasskey(destNum))
        val packet =
            buildAdminPacket(to = destNum, id = requestId, wantResponse = wantResponse, adminMessage = adminMsg)
        return packetHandler.sendToRadioAndAwait(packet)
    }

    override fun sendPosition(pos: ProtoPosition, destNum: Int?, wantResponse: Boolean) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val idNum = destNum ?: myNum
        Logger.d { "Sending our position/time to=$idNum $pos" }

        if (localConfig.value.position?.fixed_position != true) {
            nodeManager.handleReceivedPosition(myNum, myNum, pos, nowMillis)
        }

        packetHandler.sendToRadio(
            buildMeshPacket(
                to = idNum,
                channel = if (destNum == null) 0 else getChannelIndex(destNum),
                priority = MeshPacket.Priority.BACKGROUND,
                decoded =
                Data(
                    portnum = PortNum.POSITION_APP,
                    payload = pos.encode().toByteString(),
                    want_response = wantResponse,
                ),
            ),
        )
    }

    override fun requestPosition(destNum: Int, currentPosition: Position) {
        val meshPosition =
            ProtoPosition(
                latitude_i = Position.degI(currentPosition.latitude),
                longitude_i = Position.degI(currentPosition.longitude),
                altitude = currentPosition.altitude,
                time = (nowMillis / 1000L).toInt(),
            )
        packetHandler.sendToRadio(
            buildMeshPacket(
                to = destNum,
                channel = getChannelIndex(destNum),
                priority = MeshPacket.Priority.BACKGROUND,
                decoded =
                Data(
                    portnum = PortNum.POSITION_APP,
                    payload = meshPosition.encode().toByteString(),
                    want_response = true,
                ),
            ),
        )
    }

    override fun setFixedPosition(destNum: Int, pos: Position) {
        val meshPos =
            ProtoPosition(
                latitude_i = Position.degI(pos.latitude),
                longitude_i = Position.degI(pos.longitude),
                altitude = pos.altitude,
            )
        sendAdmin(destNum) {
            if (pos != Position(0.0, 0.0, 0)) {
                AdminMessage(set_fixed_position = meshPos)
            } else {
                AdminMessage(remove_fixed_position = true)
            }
        }
        nodeManager.handleReceivedPosition(destNum, nodeManager.myNodeNum.value ?: 0, meshPos, nowMillis)
    }

    override fun requestUserInfo(destNum: Int) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val myNode = nodeManager.nodeDBbyNodeNum[myNum] ?: return
        packetHandler.sendToRadio(
            buildMeshPacket(
                to = destNum,
                channel = getChannelIndex(destNum),
                decoded =
                Data(
                    portnum = PortNum.NODEINFO_APP,
                    want_response = true,
                    payload = myNode.user.encode().toByteString(),
                ),
            ),
        )
    }

    override fun requestTraceroute(requestId: Int, destNum: Int) {
        tracerouteHandler.recordStartTime(requestId)
        packetHandler.sendToRadio(
            buildMeshPacket(
                to = destNum,
                wantAck = true,
                id = requestId,
                channel = getChannelIndex(destNum),
                decoded = Data(portnum = PortNum.TRACEROUTE_APP, want_response = true, dest = destNum),
            ),
        )
    }

    override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        val type = TelemetryType.entries.getOrNull(typeValue) ?: TelemetryType.DEVICE

        val portNum: PortNum
        val payloadBytes: ByteString

        if (type == TelemetryType.PAX) {
            portNum = PortNum.PAXCOUNTER_APP
            payloadBytes = Paxcount().encode().toByteString()
        } else {
            portNum = PortNum.TELEMETRY_APP
            payloadBytes =
                Telemetry(
                    device_metrics = if (type == TelemetryType.DEVICE) DeviceMetrics() else null,
                    environment_metrics = if (type == TelemetryType.ENVIRONMENT) EnvironmentMetrics() else null,
                    air_quality_metrics = if (type == TelemetryType.AIR_QUALITY) AirQualityMetrics() else null,
                    power_metrics = if (type == TelemetryType.POWER) PowerMetrics() else null,
                    local_stats = if (type == TelemetryType.LOCAL_STATS) LocalStats() else null,
                    host_metrics = if (type == TelemetryType.HOST) HostMetrics() else null,
                )
                    .encode()
                    .toByteString()
        }

        packetHandler.sendToRadio(
            buildMeshPacket(
                to = destNum,
                id = requestId,
                channel = getChannelIndex(destNum),
                decoded = Data(portnum = portNum, payload = payloadBytes, want_response = true, dest = destNum),
            ),
        )
    }

    override fun requestNeighborInfo(requestId: Int, destNum: Int) {
        neighborInfoHandler.recordStartTime(requestId)
        val myNum = nodeManager.myNodeNum.value ?: 0
        if (destNum == myNum) {
            val neighborInfoToSend =
                neighborInfoHandler.lastNeighborInfo
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
                                    last_rx_time = (nowMillis / 1000L).toInt(),
                                    node_broadcast_interval_secs = oneHour,
                                ),
                            ),
                        )
                    }

            // Send the neighbor info from our connected radio to ourselves (simulated)
            packetHandler.sendToRadio(
                buildMeshPacket(
                    to = destNum,
                    wantAck = true,
                    id = requestId,
                    channel = getChannelIndex(destNum),
                    decoded =
                    Data(
                        portnum = PortNum.NEIGHBORINFO_APP,
                        payload = neighborInfoToSend.encode().toByteString(),
                        want_response = true,
                    ),
                ),
            )
        } else {
            // Send request to remote
            packetHandler.sendToRadio(
                buildMeshPacket(
                    to = destNum,
                    wantAck = true,
                    id = requestId,
                    channel = getChannelIndex(destNum),
                    decoded = Data(portnum = PortNum.NEIGHBORINFO_APP, want_response = true, dest = destNum),
                ),
            )
        }
    }

    fun resolveNodeNum(toId: String): Int = when (toId) {
        DataPacket.ID_BROADCAST -> DataPacket.NODENUM_BROADCAST
        else -> {
            val numericNum =
                if (toId.startsWith(NODE_ID_PREFIX)) {
                    toId.substring(NODE_ID_START_INDEX).toLongOrNull(HEX_RADIX)?.toInt()
                } else {
                    null
                }
            numericNum
                ?: nodeManager.nodeDBbyID[toId]?.num
                ?: throw IllegalArgumentException("Unknown node ID $toId")
        }
    }

    private fun buildMeshPacket(
        to: Int,
        wantAck: Boolean = false,
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        hopLimit: Int = 0,
        channel: Int = 0,
        priority: MeshPacket.Priority = MeshPacket.Priority.UNSET,
        decoded: Data,
    ): MeshPacket {
        val actualHopLimit = if (hopLimit > 0) hopLimit else computeHopLimit()

        var pkiEncrypted = false
        var publicKey: ByteString = ByteString.EMPTY
        var actualChannel = channel

        if (channel == DataPacket.PKC_CHANNEL_INDEX) {
            pkiEncrypted = true
            val destNode = nodeManager.nodeDBbyNodeNum[to]
            // Resolve the public key using the same fallback as Node.hasPKC:
            // standalone publicKey (populated after Room round-trip) first, then
            // the embedded user.public_key (always available in-memory).
            publicKey = destNode?.let { it.publicKey ?: it.user.public_key } ?: ByteString.EMPTY
            if (publicKey.size == 0) {
                Logger.w { "buildMeshPacket: no public key for node ${to.toUInt()}, PKI encryption will fail" }
            }
            actualChannel = 0
        }

        return MeshPacket(
            from = nodeManager.myNodeNum.value ?: 0,
            to = to,
            id = id,
            want_ack = wantAck,
            hop_limit = actualHopLimit,
            hop_start = actualHopLimit,
            priority = priority,
            pki_encrypted = pkiEncrypted,
            public_key = publicKey,
            channel = actualChannel,
            decoded = decoded,
        )
    }

    private fun buildAdminPacket(
        to: Int,
        id: Int = generatePacketId(), // always assign a packet ID if we didn't already have one
        wantResponse: Boolean = false,
        adminMessage: AdminMessage,
    ): MeshPacket =
        buildMeshPacket(
            to = to,
            id = id,
            wantAck = true,
            channel = getAdminChannelIndex(to),
            priority = MeshPacket.Priority.RELIABLE,
            decoded =
            Data(
                want_response = wantResponse,
                portnum = PortNum.ADMIN_APP,
                payload = adminMessage.encode().toByteString(),
            ),
        )

    companion object {
        private const val PACKET_ID_MASK = 0xffffffffL
        private const val PACKET_ID_SHIFT_BITS = 32

        private const val ADMIN_CHANNEL_NAME = "admin"
        private const val NODE_ID_PREFIX = "!"
        private const val NODE_ID_START_INDEX = 1
        private const val HEX_RADIX = 16

        private const val DEFAULT_HOP_LIMIT = 3
    }
}
