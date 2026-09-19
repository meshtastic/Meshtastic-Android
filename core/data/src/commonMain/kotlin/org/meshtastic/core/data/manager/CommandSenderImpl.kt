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

    private val localConfig = MutableStateFlow(LocalConfig.Builder().build())
    private val channelSet = MutableStateFlow(ChannelSet.Builder().build())

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
            Data.Builder()
                .also { wb ->
                    wb.portnum = PortNum.fromValue(p.dataType) ?: PortNum.UNKNOWN_APP
                    wb.payload = bytes
                    wb.reply_id = p.replyId ?: 0
                    wb.emoji = p.emoji
                }
                .build()

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
                Data.Builder()
                    .also { wb ->
                        wb.portnum = PortNum.fromValue(p.dataType) ?: PortNum.UNKNOWN_APP
                        wb.payload = p.bytes ?: ByteString.EMPTY
                        wb.reply_id = p.replyId ?: 0
                        wb.emoji = p.emoji
                    }
                    .build(),
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
        adminMessage =
        initFn().newBuilder().also { wb -> wb.session_passkey = sessionManager.getPasskey(destNum) }.build(),
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
        val adminMsg =
            initFn().newBuilder().also { wb -> wb.session_passkey = sessionManager.getPasskey(destNum) }.build()
        val packet = buildAdminPacket(to = destNum, adminMessage = adminMsg)
        packetHandler.sendToRadio(ToRadio.Builder().also { wb -> wb.packet = packet }.build())
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
                Data.Builder()
                    .also { wb ->
                        wb.portnum = PortNum.POSITION_APP
                        wb.payload = pos.encode().toByteString()
                        wb.want_response = wantResponse
                    }
                    .build(),
            ),
            "Position update",
        )
        if (localConfig.value.position?.fixed_position != true) {
            nodeManager.handleReceivedPosition(myNum, myNum, pos, nowMillis)
        }
    }

    override suspend fun requestPosition(destNum: Int, currentPosition: Position) {
        val meshPosition =
            ProtoPosition.Builder()
                .also { wb ->
                    wb.latitude_i = Position.degI(currentPosition.latitude)
                    wb.longitude_i = Position.degI(currentPosition.longitude)
                    wb.altitude = currentPosition.altitude
                    wb.time = (nowMillis / MILLIS_PER_SECOND).toInt()
                }
                .build()
        enqueueOrThrow(
            buildMeshPacket(
                to = destNum,
                channel = getChannelIndex(destNum),
                priority = MeshPacket.Priority.BACKGROUND,
                decoded =
                Data.Builder()
                    .also { wb ->
                        wb.portnum = PortNum.POSITION_APP
                        wb.payload = meshPosition.encode().toByteString()
                        wb.want_response = true
                    }
                    .build(),
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
                Data.Builder()
                    .also { wb ->
                        wb.portnum = PortNum.NODEINFO_APP
                        wb.want_response = true
                        wb.payload = myNode.user.encode().toByteString()
                    }
                    .build(),
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
                decoded =
                Data.Builder()
                    .also { wb ->
                        wb.portnum = PortNum.TRACEROUTE_APP
                        wb.want_response = true
                        wb.dest = destNum
                    }
                    .build(),
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
            payloadBytes = Paxcount.Builder().build().encode().toByteString()
        } else {
            portNum = PortNum.TELEMETRY_APP
            payloadBytes =
                Telemetry.Builder()
                    .also { wb ->
                        wb.device_metrics = if (type == TelemetryType.DEVICE) DeviceMetrics.Builder().build() else null
                        wb.environment_metrics =
                            if (type == TelemetryType.ENVIRONMENT) EnvironmentMetrics.Builder().build() else null
                        wb.air_quality_metrics =
                            if (type == TelemetryType.AIR_QUALITY) AirQualityMetrics.Builder().build() else null
                        wb.power_metrics = if (type == TelemetryType.POWER) PowerMetrics.Builder().build() else null
                        wb.local_stats = if (type == TelemetryType.LOCAL_STATS) LocalStats.Builder().build() else null
                        wb.host_metrics = if (type == TelemetryType.HOST) HostMetrics.Builder().build() else null
                    }
                    .build()
                    .encode()
                    .toByteString()
        }

        enqueueOrThrow(
            buildMeshPacket(
                to = destNum,
                id = effectiveRequestId,
                channel = getChannelIndex(destNum),
                decoded =
                Data.Builder()
                    .also { wb ->
                        wb.portnum = portNum
                        wb.payload = payloadBytes
                        wb.want_response = true
                        wb.dest = destNum
                    }
                    .build(),
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
                            NeighborInfo.Builder()
                                .also { wb ->
                                    wb.node_id = myNum
                                    wb.last_sent_by_id = myNum
                                    wb.node_broadcast_interval_secs = oneHour
                                    wb.neighbors =
                                        listOf(
                                            Neighbor.Builder()
                                                .also { wb ->
                                                    wb.node_id = 0
                                                    // Dummy node ID that can be intercepted
                                                    wb.snr = 0f
                                                    wb.last_rx_time = (nowMillis / MILLIS_PER_SECOND).toInt()
                                                    wb.node_broadcast_interval_secs = oneHour
                                                }
                                                .build(),
                                        )
                                }
                                .build()
                        }

                // Send the neighbor info from our connected radio to ourselves (simulated)
                buildMeshPacket(
                    to = destNum,
                    wantAck = true,
                    id = effectiveRequestId,
                    channel = getChannelIndex(destNum),
                    decoded =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.NEIGHBORINFO_APP
                            wb.payload = neighborInfoToSend.encode().toByteString()
                            wb.want_response = true
                        }
                        .build(),
                )
            } else {
                // Send request to remote
                buildMeshPacket(
                    to = destNum,
                    wantAck = true,
                    id = effectiveRequestId,
                    channel = getChannelIndex(destNum),
                    decoded =
                    Data.Builder()
                        .also { wb ->
                            wb.portnum = PortNum.NEIGHBORINFO_APP
                            wb.want_response = true
                            wb.dest = destNum
                        }
                        .build(),
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
    ): Boolean {
        val validUntilEpoch =
            if (hours > 0) {
                (nowMillis / MILLIS_PER_SECOND + hours.toLong() * SECONDS_PER_HOUR).toInt()
            } else {
                0
            }
        val lockdownAuth =
            LockdownAuth.Builder()
                .also { wb ->
                    wb.passphrase = passphrase.encodeToByteArray().toByteString()
                    wb.boots_remaining = boots.coerceAtLeast(0)
                    wb.valid_until_epoch = validUntilEpoch
                    wb.max_session_seconds = maxSessionSeconds.coerceAtLeast(0)
                    wb.disable = disable
                }
                .build()
        return sendLockdownAdmin(AdminMessage.Builder().also { wb -> wb.lockdown_auth = lockdownAuth }.build())
    }

    override fun sendLockNow(): Boolean = sendLockdownAdmin(
        AdminMessage.Builder()
            .also { wb -> wb.lockdown_auth = LockdownAuth.Builder().also { wb -> wb.lock_now = true }.build() }
            .build(),
    )

    private fun sendLockdownAdmin(adminMessage: AdminMessage): Boolean {
        val myNum = nodeManager.myNodeNum.value ?: return false
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.to = myNum
                    wb.id = generatePacketId()
                    wb.channel = 0
                    wb.want_ack = true
                    wb.hop_limit = DEFAULT_HOP_LIMIT
                    wb.hop_start = DEFAULT_HOP_LIMIT
                    wb.priority = MeshPacket.Priority.RELIABLE
                    wb.decoded =
                        Data.Builder()
                            .also { wb ->
                                wb.portnum = PortNum.ADMIN_APP
                                wb.payload = adminMessage.encode().toByteString()
                            }
                            .build()
                }
                .build()
        return packetHandler.trySendToRadio(ToRadio.Builder().also { wb -> wb.packet = packet }.build())
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

        return MeshPacket.Builder()
            .also { wb ->
                wb.from = nodeManager.myNodeNum.value ?: 0
                wb.to = to
                wb.id = id
                wb.want_ack = wantAck
                wb.hop_limit = actualHopLimit
                wb.hop_start = actualHopLimit
                wb.priority = priority
                wb.pki_encrypted = pkiEncrypted
                wb.public_key = publicKey
                wb.channel = actualChannel
                wb.decoded = decoded
            }
            .build()
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
            Data.Builder()
                .also { wb ->
                    wb.want_response = wantResponse
                    wb.portnum = PortNum.ADMIN_APP
                    wb.payload = adminMessage.encode().toByteString()
                }
                .build(),
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
