/*
 * Copyright (c) 2026 Meshtastic LLC
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.TelemetryType
import org.meshtastic.core.model.util.isWithinSizeLimit
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.SessionManager
import org.meshtastic.core.repository.TracerouteHandler
import org.meshtastic.core.repository.toFixedPositionAdminMessage
import org.meshtastic.core.repository.toFixedPositionProto
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
import org.meshtastic.proto.LockdownAuth
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Neighbor
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.Paxcount
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.ToRadio
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
    private val scope: ServiceScope,
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

    private fun Int.nonZeroRequestId(): Int = takeUnless { it == 0 } ?: generatePacketId()

    override fun generatePacketId(): Int {
        val numPacketIds = ((1L shl PACKET_ID_SHIFT_BITS) - 1)
        val next = currentPacketId.incrementAndGet() and PACKET_ID_MASK
        return ((next % numPacketIds) + 1L).toInt()
    }

    private fun computeHopLimit(): Int = (localConfig.value.lora?.hop_limit ?: 0).takeIf { it > 0 } ?: DEFAULT_HOP_LIMIT

    /**
     * Resolves the correct channel index for sending a packet to [toNum].
     *
     * PKI encryption ([NodeAddress.PKC_CHANNEL_INDEX]) is only used for **admin** packets, where end-to-end encryption
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

            myNode?.hasPKC == true && destNode?.hasPKC == true -> NodeAddress.PKC_CHANNEL_INDEX

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

    override suspend fun sendData(p: DataPacket) {
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

        if (!sendNow(p)) {
            p.status = MessageStatus.ERROR
            // Persistence owners treat a normal return as successful admission; throw so they can requeue or fail it.
            throw PacketQueueRejectedException("Data packet")
        }
        p.time = nowMillis
    }

    private suspend fun sendNow(p: DataPacket): Boolean {
        val meshPacket =
            buildMeshPacket(
                to = resolveNodeNum(NodeAddress.fromString(p.to)),
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
        return packetHandler.sendToRadio(meshPacket)
    }

    private suspend fun enqueueOrThrow(packet: MeshPacket, operation: String, expectedConnectionVersion: Long? = null) {
        val accepted =
            if (expectedConnectionVersion == null) {
                packetHandler.sendToRadio(packet)
            } else {
                packetHandler.sendToRadioForConnection(packet, expectedConnectionVersion)
            }
        if (!accepted) throw PacketQueueRejectedException(operation)
    }

    private fun buildAdminMessagePacket(
        destNum: Int,
        requestId: Int,
        wantResponse: Boolean,
        initFn: () -> AdminMessage,
    ): MeshPacket = buildAdminPacket(
        to = destNum,
        id = requestId.nonZeroRequestId(),
        wantResponse = wantResponse,
        adminMessage = initFn().copy(session_passkey = sessionManager.getPasskey(destNum)),
    )

    override suspend fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) {
        enqueueOrThrow(buildAdminMessagePacket(destNum, requestId, wantResponse, initFn), "Admin command")
    }

    override suspend fun sendAdminForConnection(
        destNum: Int,
        expectedConnectionVersion: Long,
        requestId: Int,
        wantResponse: Boolean,
        initFn: () -> AdminMessage,
    ) {
        enqueueOrThrow(
            buildAdminMessagePacket(destNum, requestId, wantResponse, initFn),
            "Admin command",
            expectedConnectionVersion,
        )
    }

    override fun sendAdminImmediate(destNum: Int, initFn: () -> AdminMessage) {
        val adminMsg = initFn().copy(session_passkey = sessionManager.getPasskey(destNum))
        val packet = buildAdminPacket(to = destNum, adminMessage = adminMsg)
        packetHandler.sendToRadio(ToRadio(packet = packet))
    }

    override suspend fun sendAdminAwaitResult(
        destNum: Int,
        requestId: Int,
        wantResponse: Boolean,
        initFn: () -> AdminMessage,
    ): AwaitedSendResult =
        packetHandler.sendToRadioAndAwaitResult(buildAdminMessagePacket(destNum, requestId, wantResponse, initFn))

    override suspend fun sendPosition(pos: ProtoPosition, destNum: Int?, wantResponse: Boolean) {
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Position update")
        val idNum = destNum ?: myNum
        Logger.d { "Sending our position/time to=$idNum" }

        enqueueOrThrow(
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
            "Position update",
        )
        if (localConfig.value.position?.fixed_position != true) {
            nodeManager.handleReceivedPosition(myNum, myNum, pos, nowMillis)
        }
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        val meshPosition =
            ProtoPosition(
                latitude_i = Position.degI(currentPosition.latitude),
                longitude_i = Position.degI(currentPosition.longitude),
                altitude = currentPosition.altitude,
                time = (nowMillis / 1000L).toInt(),
            )
        enqueueOrThrow(
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
            "Position request",
        )
    }

    override suspend fun setFixedPosition(destNum: Int, pos: Position) {
        val removesFixedPosition = pos.isFixedPositionRemoval()
        val myNodeNum =
            if (removesFixedPosition) {
                null
            } else {
                nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Fixed position")
            }
        sendAdmin(destNum) { pos.toFixedPositionAdminMessage() }
        if (myNodeNum != null) {
            nodeManager.handleReceivedPosition(destNum, myNodeNum, pos.toFixedPositionProto(), nowMillis)
        }
    }

    override suspend fun requestUserInfo(destNum: Int) {
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("User-info request")
        val myNode = nodeManager.nodeDBbyNodeNum[myNum] ?: throw LocalNodeUnavailableException("User-info request")
        enqueueOrThrow(
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
            "User-info request",
        )
    }

    override suspend fun requestTraceroute(requestId: Int, destNum: Int) {
        val effectiveRequestId = requestId.nonZeroRequestId()
        enqueueOrThrow(
            buildMeshPacket(
                to = destNum,
                wantAck = true,
                id = effectiveRequestId,
                channel = getChannelIndex(destNum),
                decoded = Data(portnum = PortNum.TRACEROUTE_APP, want_response = true, dest = destNum),
            ),
            "Traceroute request",
        )
        tracerouteHandler.recordStartTime(effectiveRequestId)
    }

    override suspend fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) {
        enqueueTelemetryOrThrow(requestId, destNum, typeValue)
    }

    override suspend fun requestTelemetryForConnection(
        requestId: Int,
        destNum: Int,
        typeValue: Int,
        expectedConnectionVersion: Long,
    ) {
        enqueueTelemetryOrThrow(requestId, destNum, typeValue, expectedConnectionVersion)
    }

    private suspend fun enqueueTelemetryOrThrow(
        requestId: Int,
        destNum: Int,
        typeValue: Int,
        expectedConnectionVersion: Long? = null,
    ) {
        val effectiveRequestId = requestId.nonZeroRequestId()
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

        enqueueOrThrow(
            buildMeshPacket(
                to = destNum,
                id = effectiveRequestId,
                channel = getChannelIndex(destNum),
                decoded = Data(portnum = portNum, payload = payloadBytes, want_response = true, dest = destNum),
            ),
            "Telemetry request",
            expectedConnectionVersion,
        )
    }

    override suspend fun requestNeighborInfo(requestId: Int, destNum: Int) {
        val effectiveRequestId = requestId.nonZeroRequestId()
        val myNum = nodeManager.myNodeNum.value ?: throw LocalNodeUnavailableException("Neighbor-info request")
        val packet =
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
                buildMeshPacket(
                    to = destNum,
                    wantAck = true,
                    id = effectiveRequestId,
                    channel = getChannelIndex(destNum),
                    decoded =
                    Data(
                        portnum = PortNum.NEIGHBORINFO_APP,
                        payload = neighborInfoToSend.encode().toByteString(),
                        want_response = true,
                    ),
                )
            } else {
                // Send request to remote
                buildMeshPacket(
                    to = destNum,
                    wantAck = true,
                    id = effectiveRequestId,
                    channel = getChannelIndex(destNum),
                    decoded = Data(portnum = PortNum.NEIGHBORINFO_APP, want_response = true, dest = destNum),
                )
            }
        enqueueOrThrow(packet, "Neighbor-info request")
        neighborInfoHandler.recordStartTime(effectiveRequestId)
    }

    override fun sendLockdownPassphrase(
        passphrase: String,
        boots: Int,
        hours: Int,
        maxSessionSeconds: Int,
        disable: Boolean,
    ) {
        val validUntilEpoch =
            if (hours > 0) {
                (nowMillis / MILLIS_PER_SECOND + hours.toLong() * SECONDS_PER_HOUR).toInt()
            } else {
                0
            }
        val lockdownAuth =
            LockdownAuth(
                passphrase = passphrase.encodeToByteArray().toByteString(),
                boots_remaining = boots.coerceAtLeast(0),
                valid_until_epoch = validUntilEpoch,
                max_session_seconds = maxSessionSeconds.coerceAtLeast(0),
                disable = disable,
            )
        sendLockdownAdmin(AdminMessage(lockdown_auth = lockdownAuth))
    }

    override fun sendLockNow() {
        sendLockdownAdmin(AdminMessage(lockdown_auth = LockdownAuth(lock_now = true)))
    }

    private fun sendLockdownAdmin(adminMessage: AdminMessage) {
        val myNum = nodeManager.myNodeNum.value ?: return
        val packet =
            MeshPacket(
                to = myNum,
                id = generatePacketId(),
                channel = 0,
                want_ack = true,
                hop_limit = DEFAULT_HOP_LIMIT,
                hop_start = DEFAULT_HOP_LIMIT,
                priority = MeshPacket.Priority.RELIABLE,
                decoded = Data(portnum = PortNum.ADMIN_APP, payload = adminMessage.encode().toByteString()),
            )
        packetHandler.sendToRadio(ToRadio(packet = packet))
    }

    fun resolveNodeNum(address: NodeAddress): Int = when (address) {
        NodeAddress.Broadcast -> NodeAddress.NODENUM_BROADCAST

        NodeAddress.Local -> nodeManager.myNodeNum.value ?: 0

        is NodeAddress.ByNum -> address.num

        is NodeAddress.ById ->
            nodeManager.getNodeById(address.id)?.num
                ?: throw IllegalArgumentException("Unknown node ID ${address.id}")
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

        if (channel == NodeAddress.PKC_CHANNEL_INDEX) {
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

        private const val DEFAULT_HOP_LIMIT = 3

        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_HOUR = 3600
    }
}
