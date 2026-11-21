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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.dao.shouldApplyOutgoingQueueStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.isWithinSizeLimit
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.PersistedReactionId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.proto.Constants
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.QueueReceipt
import org.meshtastic.sdk.SendFailure
import org.meshtastic.sdk.SendOutcome
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
@Single
class PacketHandlerImpl(
    private val packetRepository: Lazy<PacketRepository>,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val connectionStateProvider: ConnectionStateProvider,
    private val nodeManager: NodeManager,
    private val scope: ServiceScope,
) : PacketHandler {

    companion object {
        private val PERSISTED_STATUS_WAIT = 1.seconds
        internal val PERSISTED_STATUS_RETRY_DELAY = 100.milliseconds

        /**
         * Grace period after which a sent packet still [MessageStatus.ENROUTE] is stamped [Routing.Error.TIMEOUT]
         * (retryable) instead of showing as sending forever. Generous — well past the radio's retransmit window — and
         * matches iOS's sendAckTimeout so both apps time out alike.
         */
        internal val SEND_ACK_TIMEOUT = 5.minutes

        /**
         * Minimum re-arm delay on reconnect: the firmware's phone-queue backlog may still deliver the missing ACK/NAK
         * just after the config handshake, so give it a moment before stamping a timeout.
         */
        internal val REARM_GRACE = 30.seconds

        private const val PACKET_ID_MASK = 0xffffffffL
        private const val PACKET_ID_SHIFT_BITS = 32

        /** Firmware's `HOP_RELIABLE`, used when the radio's LoRa config sets no hop limit. */
        private const val DEFAULT_HOP_LIMIT = 3
    }

    private val packetIdCounter = atomic(Random(nowMillis).nextLong().absoluteValue)

    override val currentPacketId: Long
        get() = packetIdCounter.value

    override fun generatePacketId(): Int {
        val numPacketIds = ((1L shl PACKET_ID_SHIFT_BITS) - 1)
        val next = packetIdCounter.incrementAndGet() and PACKET_ID_MASK
        return ((next % numPacketIds) + 1L).toInt()
    }

    private enum class StatusPersistence {
        DATA_PACKET,
        REACTION,
    }

    private data class PacketStatusTarget(val packet: MeshPacket, val persistence: StatusPersistence?)

    private sealed interface PersistedStatusTarget {
        data class DataPacket(val id: PersistedPacketId) : PersistedStatusTarget

        data class Reaction(val id: PersistedReactionId) : PersistedStatusTarget
    }

    private val timeoutMutex = Mutex()
    private val sendAckTimeoutJobs = mutableMapOf<PersistedStatusTarget, Job>()

    private data class Submission(val handle: PacedMessageHandle, val departureEpoch: Long)

    private fun logPacket(packet: MeshPacket) {
        if (packet.decoded != null) {
            val packetToSave =
                MeshLog(
                    uuid = Uuid.random().toString(),
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = packet.toString(),
                    fromNum = MeshLog.NODE_NUM_LOCAL,
                    portNum = packet.decoded?.portnum?.value ?: 0,
                    fromRadio = FromRadio.Builder().also { wb -> wb.packet = packet }.build(),
                )
            insertMeshLog(packetToSave)
        }
    }

    override fun logRequest(requestId: Int, to: Int, port: PortNum) = logPacket(
        MeshPacket.Builder()
            .also { wb ->
                wb.id = requestId
                wb.from = nodeManager.myNodeNum.value ?: 0
                wb.to = to
                wb.decoded =
                    Data.Builder()
                        .also { data ->
                            data.portnum = port
                            data.want_response = true
                            data.dest = to
                        }
                        .build()
            }
            .build(),
    )

    override suspend fun sendData(p: DataPacket) {
        if (p.id == 0) p.id = generatePacketId()
        require(p.dataType != 0) { "Port numbers must be non-zero!" }
        val data =
            Data.Builder()
                .also { wb ->
                    wb.portnum = PortNum.fromValue(p.dataType) ?: PortNum.UNKNOWN_APP
                    wb.payload = p.bytes ?: ByteString.EMPTY
                    wb.reply_id = p.replyId ?: 0
                    wb.emoji = p.emoji
                }
                .build()
        if (!Data.ADAPTER.isWithinSizeLimit(data, Constants.DATA_PAYLOAD_LEN.value)) {
            p.status = MessageStatus.ERROR
            error("Message too long: ${Data.ADAPTER.encodedSize(data)} bytes")
        }
        p.status = MessageStatus.QUEUED
        val packet = meshPacketFor(p, data)
        if (packet == null || !sendToRadio(packet)) {
            p.status = MessageStatus.ERROR
            // Persistence owners treat a normal return as successful admission; throw so they can requeue or fail it.
            throw PacketQueueRejectedException("Data packet")
        }
        p.time = nowMillis
    }

    /**
     * The wire form of [p] under its persisted ID. The SDK's send builder assigns its own ID and carries no reply,
     * emoji or PKI fields, so the durable send path builds the packet the SDK's paced queue then owns.
     */
    private fun meshPacketFor(p: DataPacket, data: Data): MeshPacket? {
        val client =
            radioInterfaceService.activeSession.value?.let(radioInterfaceService::clientForSession) ?: return null
        val to = resolveNodeNum(NodeAddress.fromString(p.to))
        val pki = p.channel == NodeAddress.PKC_CHANNEL_INDEX
        // Firmware raises a zero hop limit only on a want_ack packet, so a zero would go out unrelayed.
        val hopLimit =
            p.hopLimit.takeIf { it > 0 }
                ?: client.configBundle.value?.configs?.firstNotNullOfOrNull { it.lora }?.hop_limit?.takeIf { it > 0 }
                ?: DEFAULT_HOP_LIMIT
        return MeshPacket.Builder()
            .also { wb ->
                wb.to = to
                wb.id = p.id
                wb.want_ack = p.wantAck
                wb.hop_limit = hopLimit
                wb.channel = if (pki) 0 else p.channel
                if (pki) {
                    wb.pki_encrypted = true
                    wb.public_key = publicKeyOf(to)
                }
                wb.decoded = data
            }
            .build()
    }

    /** The key [Node.hasPKC][org.meshtastic.core.model.Node.hasPKC] reads: the stored key, else the user's. */
    private fun publicKeyOf(nodeNum: Int): ByteString {
        val node = nodeManager.nodeDBbyNodeNum[nodeNum]
        val key = node?.let { it.publicKey ?: it.user.public_key } ?: ByteString.EMPTY
        if (key.size == 0) Logger.w { "No public key for node ${nodeNum.toUInt()}, PKI encryption will fail" }
        return key
    }

    private fun resolveNodeNum(address: NodeAddress): Int = when (address) {
        NodeAddress.Broadcast -> NodeAddress.NODENUM_BROADCAST

        NodeAddress.Local -> nodeManager.myNodeNum.value ?: 0

        is NodeAddress.ByNum -> address.num

        is NodeAddress.ById ->
            nodeManager.getNodeById(address.id)?.num
                ?: throw IllegalArgumentException("Unknown node ID ${address.id}")
    }

    override suspend fun sendTracked(packet: MeshPacket): PacedMessageHandle? {
        if (packet.id == 0) return null
        return safeCatching { submit(packet, expectedVersion = null)?.handle?.takeIf { it.awaitAdmission() } }
            .onFailure { Logger.w(it) { "SDK send submission failed for packet ${packet.id.toUInt()}" } }
            .getOrNull()
    }

    override suspend fun sendToRadioAndAwaitResult(packet: MeshPacket): AwaitedSendResult =
        sendAndAwait(packet, expectedVersion = null)

    override suspend fun sendToRadioAndAwaitResultForConnection(
        packet: MeshPacket,
        expectedConnectionVersion: Long,
    ): AwaitedSendResult = sendAndAwait(packet, expectedConnectionVersion)

    @Suppress("ReturnCount")
    private fun submit(packet: MeshPacket, expectedVersion: Long?): Submission? {
        if (!scope.isActive) return null
        val session = radioInterfaceService.activeSession.value ?: return null
        val lifecycle = connectionStateProvider.connectionLifecycle.value
        if (
            lifecycle.state != ConnectionState.Connected ||
            (expectedVersion != null && lifecycle.version != expectedVersion)
        ) {
            return null
        }

        val handle = radioInterfaceService.sendPaced(session, packet) ?: return null
        scope.handledLaunch { projectSend(session, packet, handle) }
        return Submission(handle, lifecycle.epochs.departures)
    }

    @Suppress("ReturnCount")
    private suspend fun sendAndAwait(packet: MeshPacket, expectedVersion: Long?): AwaitedSendResult {
        if (packet.id == 0) return AwaitedSendResult(AwaitedSendStatus.REJECTED)
        // A strict caller depends on positive routing evidence or a firmware local-loopback receipt.
        val outbound = if (packet.want_ack) packet else packet.newBuilder().also { it.want_ack = true }.build()
        val submission =
            safeCatching { submit(outbound, expectedVersion) }
                .getOrElse {
                    Logger.w(it) { "SDK send submission failed for packet ${packet.id.toUInt()}" }
                    return AwaitedSendResult(AwaitedSendStatus.SEND_FAILED)
                } ?: return AwaitedSendResult(AwaitedSendStatus.TRANSPORT_STOPPED)

        val handle = submission.handle
        val dispatched = handle.awaitDispatch()
        val outcome = handle.message.await()
        val status =
            when (outcome) {
                SendOutcome.Success -> AwaitedSendStatus.ACCEPTED

                is SendOutcome.Failure ->
                    when (outcome.reason) {
                        SendFailure.QueueFull,
                        SendFailure.IdCollision,
                        -> AwaitedSendStatus.REJECTED

                        SendFailure.Disconnected,
                        SendFailure.HandshakeFailed,
                        SendFailure.Cancelled,
                        -> AwaitedSendStatus.TRANSPORT_STOPPED

                        SendFailure.AckTimeout,
                        SendFailure.Timeout,
                        -> AwaitedSendStatus.TIMED_OUT

                        else -> if (dispatched) AwaitedSendStatus.RADIO_REJECTED else AwaitedSendStatus.SEND_FAILED
                    }
            }
        return AwaitedSendResult(status, submission.departureEpoch.takeIf { dispatched })
    }

    private suspend fun projectSend(session: RadioSessionContext, packet: MeshPacket, handle: PacedMessageHandle) {
        if (!handle.awaitAdmission()) return
        if (!handle.awaitDispatch()) {
            projectStatus(session, packet, MessageStatus.ERROR)
            return
        }
        logPacket(packet)
        projectStatus(session, packet, MessageStatus.ENROUTE)

        when (handle.awaitQueueReceipt()) {
            QueueReceipt.Rejected -> projectStatus(session, packet, MessageStatus.ERROR)

            QueueReceipt.LocalDelivery -> projectStatus(session, packet, MessageStatus.DELIVERED)

            QueueReceipt.Unconfirmed -> {
                val failure = (handle.message.await() as? SendOutcome.Failure)?.reason
                if (failure is SendFailure.QueueRejected) projectStatus(session, packet, MessageStatus.ERROR)
            }

            QueueReceipt.Accepted,
            QueueReceipt.NotDispatched,
            -> Unit
        }
    }

    private suspend fun projectStatus(session: RadioSessionContext, packet: MeshPacket, status: MessageStatus) {
        val persistence = packet.statusPersistence() ?: return
        radioInterfaceService.runWithSessionLease(session) {
            val target = PacketStatusTarget(packet, persistence)
            withTimeoutOrNull(PERSISTED_STATUS_WAIT) {
                while (!applyQueueStatus(session, target, status)) delay(PERSISTED_STATUS_RETRY_DELAY)
            }
        }
    }

    override fun rearmSendAckTimeouts() {
        val session = radioInterfaceService.activeSession.value ?: return
        radioInterfaceService.launchSessionWork(scope, session) {
            packetRepository.value.getEnroutePackets().forEach { persisted ->
                val remaining = persisted.packet.time + SEND_ACK_TIMEOUT.inWholeMilliseconds - nowMillis
                scheduleSendAckTimeout(
                    session,
                    PersistedStatusTarget.DataPacket(persisted.id),
                    remaining.milliseconds.coerceAtLeast(REARM_GRACE),
                )
            }
            packetRepository.value.getEnrouteReactions().forEach { persisted ->
                val remaining = persisted.reaction.timestamp + SEND_ACK_TIMEOUT.inWholeMilliseconds - nowMillis
                scheduleSendAckTimeout(
                    session,
                    PersistedStatusTarget.Reaction(persisted.id),
                    remaining.milliseconds.coerceAtLeast(REARM_GRACE),
                )
            }
        }
    }

    /**
     * A send whose routing ACK/NAK never reaches the app (typically because it was disconnected when the radio's
     * response arrived) would stay ENROUTE — "Sending…" — forever. Stamp it as a retryable timeout instead; a late ACK
     * still upgrades it via handleAckNak.
     *
     * Reconnecting re-arms timeouts for the selected database. A retired session cannot settle its rows through the
     * active database provider.
     */
    private suspend fun scheduleSendAckTimeout(
        session: RadioSessionContext,
        target: PersistedStatusTarget,
        delayFor: Duration = SEND_ACK_TIMEOUT,
    ) {
        timeoutMutex.withLock {
            sendAckTimeoutJobs.remove(target)?.cancel()
            sendAckTimeoutJobs.values.removeAll { it.isCompleted }
            sendAckTimeoutJobs[target] = scope.handledLaunch {
                delay(delayFor)
                // Any settlement that lands before the conditional transaction wins.
                radioInterfaceService.runWithSessionLease(session) {
                    when (target) {
                        is PersistedStatusTarget.DataPacket ->
                            packetRepository.value.timeOutEnroutePacket(target.id, Routing.Error.TIMEOUT.value)

                        is PersistedStatusTarget.Reaction ->
                            packetRepository.value.timeOutEnrouteReaction(target.id, Routing.Error.TIMEOUT.value)
                    }
                }
            }
        }
    }

    private suspend fun applyQueueStatus(
        session: RadioSessionContext,
        target: PacketStatusTarget,
        status: MessageStatus,
    ): Boolean = when (target.persistence) {
        StatusPersistence.DATA_PACKET -> {
            val persisted = packetRepository.value.applyOutgoingQueueStatus(target.packet, status) ?: return false
            val current = persisted.packet.status
            val shouldPersist =
                status == MessageStatus.ENROUTE &&
                    (current == MessageStatus.ENROUTE || shouldApplyOutgoingQueueStatus(current, status))
            if (shouldPersist) {
                scheduleSendAckTimeout(session, PersistedStatusTarget.DataPacket(persisted.id))
            }
            true
        }

        StatusPersistence.REACTION -> {
            val persisted =
                packetRepository.value.applyOutgoingReactionQueueStatus(target.packet.id, status) ?: return false
            val current = persisted.reaction.status
            if (
                status == MessageStatus.ENROUTE &&
                (current == MessageStatus.ENROUTE || shouldApplyOutgoingQueueStatus(current, status))
            ) {
                scheduleSendAckTimeout(session, PersistedStatusTarget.Reaction(persisted.id))
            }
            true
        }

        null -> true
    }

    private fun MeshPacket.statusPersistence(): StatusPersistence? {
        val data = decoded ?: return null
        return when {
            data.isReaction() -> StatusPersistence.REACTION
            data.portnum.value in PERSISTED_DATA_PORT_NUMBERS -> StatusPersistence.DATA_PACKET
            else -> null
        }
    }

    private fun insertMeshLog(packetToSave: MeshLog) {
        scope.handledLaunch {
            Logger.d {
                "insert: ${packetToSave.message_type} = " +
                    "${packetToSave.raw_message.toOneLineString()} from=${packetToSave.fromNum}"
            }
            meshLogRepository.value.insert(packetToSave)
        }
    }
}
