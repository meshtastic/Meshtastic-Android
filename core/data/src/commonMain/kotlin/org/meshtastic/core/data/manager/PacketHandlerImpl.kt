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
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.util.isWithinSizeLimit
import org.meshtastic.core.repository.ConnectionStateProvider
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
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.sdk.PacedMessageHandle
import org.meshtastic.sdk.QueueReceipt
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.SendFailure
import org.meshtastic.sdk.SendOutcome
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
@Single
class PacketHandlerImpl(
    private val packetRepository: Lazy<PacketRepository>,
    private val radioInterfaceService: RadioInterfaceService,
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

        /** Firmware's `HOP_RELIABLE`, used when the radio's LoRa config sets no hop limit. */
        private const val DEFAULT_HOP_LIMIT = 3
    }

    private fun activeClient(): RadioClient? =
        radioInterfaceService.activeSession.value?.let(radioInterfaceService::clientForSession)

    // With no client the ID names no wire packet, so any non-zero value serves.
    override fun generatePacketId(): Int = activeClient()?.nextPacketId()?.raw ?: Random.nextInt(1, Int.MAX_VALUE)

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
     * The wire form of [p] under its persisted ID. The SDK's send builder builds only inside the SDK's own send, and
     * the session's paced send takes a [MeshPacket], so the durable path builds the packet the SDK's paced queue then
     * owns.
     */
    private fun meshPacketFor(p: DataPacket, data: Data): MeshPacket? {
        val client = activeClient() ?: return null
        val to = resolveNodeNum(NodeAddress.fromString(p.to))
        val pki = p.channel == NodeAddress.PKC_CHANNEL_INDEX
        // Firmware raises a zero hop limit only on a want_ack packet, so a zero would go out unrelayed.
        val hopLimit =
            p.hopLimit.takeIf { it > 0 }
                ?: client.configBundle.value?.configs?.firstNotNullOfOrNull { it.lora }?.hop_limit?.takeIf { it > 0 }
                ?: DEFAULT_HOP_LIMIT
        return MeshPacket.Builder()
            .also { wb ->
                // The stored row's sender is our node; queue receipts find it by from, to and ID.
                wb.from = nodeManager.myNodeNum.value ?: 0
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
        return safeCatching { submit(packet)?.takeIf { it.awaitAdmission() } }
            .onFailure { Logger.w(it) { "SDK send submission failed for packet ${packet.id.toUInt()}" } }
            .getOrNull()
    }

    @Suppress("ReturnCount")
    private fun submit(packet: MeshPacket): PacedMessageHandle? {
        if (!scope.isActive) return null
        val session = radioInterfaceService.activeSession.value ?: return null
        if (connectionStateProvider.connectionLifecycle.value.state != ConnectionState.Connected) return null

        val handle = radioInterfaceService.sendPaced(session, packet) ?: return null
        scope.handledLaunch { projectSend(session, packet, handle) }
        return handle
    }

    private suspend fun projectSend(session: RadioSessionContext, packet: MeshPacket, handle: PacedMessageHandle) {
        if (!handle.awaitAdmission()) return
        if (!handle.awaitDispatch()) {
            projectStatus(session, packet, MessageStatus.ERROR)
            return
        }
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
}
