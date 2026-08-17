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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.dao.shouldApplyOutgoingQueueStatus
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.repository.PersistedReactionId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.Routing
import org.meshtastic.proto.ToRadio
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
    private val scope: ServiceScope,
) : PacketHandler {

    companion object {
        internal val RESPONSE_TIMEOUT = 5.seconds

        /** Routing acknowledgements traverse the mesh and need a larger budget than the local queue response. */
        internal val ROUTING_RESPONSE_TIMEOUT = 30.seconds
        private val PERSISTED_STATUS_WAIT = 1.seconds
        private val PERSISTED_STATUS_SHUTDOWN_WAIT = 150.milliseconds
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

        /**
         * Firmware-internal `ErrorCode` (MeshTypes.h `ERRNO_SHOULD_RELEASE`) leaked into `QueueStatus.res`: "no error,
         * but the packet should still be released". Firmware 2.8+ returns it for self-addressed packets, which are
         * delivered through the synchronous local loopback instead of the TX queue — a success. Note it numerically
         * collides with `Routing.Error.PKI_UNKNOWN_PUBKEY` (35); `QueueStatus.res` carries ErrorCode semantics, not
         * Routing.Error.
         */
        private const val ERRNO_SHOULD_RELEASE = 35
    }

    private var queueJob: Job? = null
    private var queueGeneration = 0L

    // Lock-order invariant: acquire queueMutex before responseMutex whenever both are needed. Never acquire
    // responseMutex first on a path that can later need queueMutex, or admission and teardown can deadlock.
    private val queueMutex = Mutex()
    private val queuedPackets = mutableListOf<QueuedPacket>()

    // Marks the current queue generation as drained. The next admission clears it so reconnect recovery can start a
    // fresh worker; stale workers retain their generation and cannot replace that worker.
    private var queueStopped = false

    private val responseMutex = Mutex()

    private data class QueuedPacket(
        val packet: MeshPacket,
        val pending: PendingResponse,
        val expectedConnectionVersion: Long?,
    )

    private enum class StatusPersistence {
        DATA_PACKET,
        REACTION,
    }

    private data class PacketStatusTarget(val packet: MeshPacket, val persistence: StatusPersistence?)

    private sealed interface PersistedStatusTarget {
        data class DataPacket(val id: PersistedPacketId) : PersistedStatusTarget

        data class Reaction(val id: PersistedReactionId) : PersistedStatusTarget
    }

    private class PendingResponse(val packet: MeshPacket, val persistence: StatusPersistence?, awaitRouting: Boolean) {
        val queueDeferred = CompletableDeferred<AwaitedSendStatus>()
        val routingDeferred = CompletableDeferred<AwaitedSendStatus>().takeIf { awaitRouting }
        val dispatchAccepted = CompletableDeferred<Boolean>()
        private val dispatchResolved = atomic(false)
        private val dispatchDepartureEpoch = atomic<Long?>(null)
        private val queueStatus = atomic<AwaitedSendStatus?>(null)
        private val routingStatus = atomic<AwaitedSendStatus?>(null)

        val wasDispatched: Boolean
            get() = dispatchDepartureEpoch.value != null

        val departureEpochAtDispatch: Long?
            get() = dispatchDepartureEpoch.value

        val terminalStatus: AwaitedSendStatus?
            get() = routingStatus.value ?: queueStatus.value

        val isTerminal: Boolean
            get() = queueDeferred.isCompleted && (routingDeferred?.isCompleted != false)

        fun recordDispatch(accepted: Boolean, departureEpoch: Long) {
            if (!dispatchResolved.compareAndSet(expect = false, update = true)) return
            if (accepted) dispatchDepartureEpoch.value = departureEpoch
            dispatchAccepted.complete(accepted)
        }

        fun completeQueue(status: AwaitedSendStatus, routingTerminal: Boolean = status != AwaitedSendStatus.ACCEPTED) {
            if (queueDeferred.complete(status)) queueStatus.compareAndSet(null, status)
            if (routingTerminal) completeRouting(status)
        }

        fun completeRouting(status: AwaitedSendStatus) {
            val deferred = routingDeferred ?: return
            if (deferred.complete(status)) routingStatus.compareAndSet(null, status)
        }

        fun completeAll(status: AwaitedSendStatus) {
            if (dispatchResolved.compareAndSet(expect = false, update = true)) dispatchAccepted.complete(false)
            completeQueue(status, routingTerminal = true)
        }

        fun sendFailureStatus(): AwaitedSendStatus =
            if (wasDispatched) AwaitedSendStatus.TRANSPORT_STOPPED else AwaitedSendStatus.SEND_FAILED
    }

    private sealed interface QueueAdmission {
        data class Admitted(val pending: PendingResponse) : QueueAdmission

        data object DuplicateId : QueueAdmission

        data object ScopeInactive : QueueAdmission

        data object TransportUnavailable : QueueAdmission
    }

    private val queueResponse = mutableMapOf<Int, PendingResponse>()

    private val timeoutMutex = Mutex()
    private val sendAckTimeoutJobs = mutableMapOf<PersistedStatusTarget, Job>()

    override fun sendToRadio(p: ToRadio) {
        if (!dispatchToRadio(p)) {
            Logger.w { "sendToRadio dropped: no active transport accepted outbound command" }
        }
    }

    private fun dispatchToRadio(p: ToRadio): Boolean {
        Logger.d { "Sending to radio ${p.toPIIString()}" }
        val dispatched = radioInterfaceService.trySendToRadio(p.encode())
        if (!dispatched) return false

        p.packet?.let { changeStatus(it, MessageStatus.ENROUTE) }
        val packet = p.packet
        if (packet?.decoded != null) {
            val packetToSave =
                MeshLog(
                    uuid = Uuid.random().toString(),
                    message_type = "Packet",
                    received_date = nowMillis,
                    raw_message = packet.toString(),
                    fromNum = MeshLog.NODE_NUM_LOCAL,
                    portNum = packet.decoded?.portnum?.value ?: 0,
                    fromRadio = FromRadio(packet = packet),
                )
            insertMeshLog(packetToSave)
        }
        return true
    }

    /**
     * Enqueue [packet] for transmission. Order is preserved for sequential calls from the same coroutine (mutex
     * acquisition is uncontested between sequential calls). Transactional sequences that require strict ordering across
     * multiple calls — e.g. an `editSettings { … }` begin → writes → commit sequence — MUST be issued from a single
     * coroutine; concurrent senders share FIFO only at the per-call grain.
     */
    override suspend fun sendToRadio(packet: MeshPacket): Boolean =
        enqueueForSend(packet, expectedConnectionVersion = null)

    override suspend fun sendToRadioForConnection(packet: MeshPacket, expectedConnectionVersion: Long): Boolean =
        enqueueForSend(packet, expectedConnectionVersion)

    private suspend fun enqueueForSend(packet: MeshPacket, expectedConnectionVersion: Long?): Boolean {
        if (packet.id == 0) {
            Logger.w { "Dropping queued packet without an ID" }
            return false
        }
        return when (enqueuePacket(packet, expectedConnectionVersion = expectedConnectionVersion)) {
            is QueueAdmission.Admitted -> true

            QueueAdmission.TransportUnavailable -> {
                Logger.w { "Rejecting packet id=${packet.id.toUInt()}: radio is not connected" }
                false
            }

            QueueAdmission.ScopeInactive -> {
                Logger.w { "Rejecting packet id=${packet.id.toUInt()}: service scope is no longer active" }
                false
            }

            QueueAdmission.DuplicateId -> {
                Logger.w { "Rejecting packet with reserved id=${packet.id.toUInt()}" }
                false
            }
        }
    }

    override suspend fun sendToRadioAndAwaitResult(packet: MeshPacket): AwaitedSendResult = if (packet.id == 0) {
        Logger.w { "Rejecting awaited packet without an ID" }
        AwaitedSendResult(status = AwaitedSendStatus.REJECTED)
    } else {
        when (val admission = enqueuePacket(packet, awaitRouting = true)) {
            is QueueAdmission.Admitted -> awaitAdmittedPacket(packet, admission.pending)

            QueueAdmission.TransportUnavailable -> {
                Logger.w { "Rejecting awaited packet id=${packet.id.toUInt()}: radio is not connected" }
                AwaitedSendResult(status = AwaitedSendStatus.TRANSPORT_STOPPED)
            }

            QueueAdmission.ScopeInactive -> {
                Logger.w { "Rejecting awaited packet id=${packet.id.toUInt()}: service scope is no longer active" }
                AwaitedSendResult(status = AwaitedSendStatus.TRANSPORT_STOPPED)
            }

            QueueAdmission.DuplicateId -> {
                Logger.w { "Rejecting duplicate awaited packet id=${packet.id.toUInt()}" }
                AwaitedSendResult(status = AwaitedSendStatus.REJECTED)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun awaitAdmittedPacket(packet: MeshPacket, pending: PendingResponse): AwaitedSendResult {
        val routing = checkNotNull(pending.routingDeferred) { "awaited packet must reserve a routing response" }
        return try {
            awaitPendingOrServiceStop(pending.dispatchAccepted)
            val status = awaitPendingOrServiceStop(routing)
            AwaitedSendResult(status = status, departureEpochAtDispatch = pending.departureEpochAtDispatch)
        } catch (e: CancellationException) {
            throw e // Caller cancellation does not release queue or response ownership.
        } catch (e: Exception) {
            Logger.w(e) { "sendToRadioAndAwait packet id=${packet.id.toUInt()} failed" }
            val failureStatus = pending.sendFailureStatus()
            responseMutex.withLock {
                pending.completeRouting(failureStatus)
                removePendingIfTerminalLocked(packet.id, pending)
            }
            AwaitedSendResult(
                status = pending.terminalStatus ?: failureStatus,
                departureEpochAtDispatch = pending.departureEpochAtDispatch,
            )
        }
    }

    /**
     * Reserves [packet]'s non-zero ID and queues it as one atomic admission. The reservation spans queued work, the
     * firmware queue result, and (for strict callers) the later routing acknowledgement.
     */
    private suspend fun enqueuePacket(
        packet: MeshPacket,
        awaitRouting: Boolean = false,
        expectedConnectionVersion: Long? = null,
    ): QueueAdmission = queueMutex.withLock {
        responseMutex.withLock responseLock@{
            if (!scope.isActive) return@responseLock QueueAdmission.ScopeInactive
            if (expectedConnectionVersion == null) {
                if (connectionStateProvider.connectionState.value != ConnectionState.Connected) {
                    return@responseLock QueueAdmission.TransportUnavailable
                }
            } else {
                val lifecycle = connectionStateProvider.connectionLifecycle.value
                if (
                    lifecycle.state !is ConnectionState.Connected || lifecycle.version != expectedConnectionVersion
                ) {
                    return@responseLock QueueAdmission.TransportUnavailable
                }
            }
            if (queueResponse.containsKey(packet.id)) return@responseLock QueueAdmission.DuplicateId

            val pending =
                PendingResponse(
                    packet = packet,
                    persistence = packet.statusPersistence(),
                    awaitRouting = awaitRouting,
                )
            queueResponse[packet.id] = pending
            queueStopped = false // Allow queue to resume after a disconnect/reconnect cycle.
            queuedPackets.add(
                QueuedPacket(
                    packet = packet,
                    pending = pending,
                    expectedConnectionVersion = expectedConnectionVersion,
                ),
            )
            startPacketQueueLocked()
            QueueAdmission.Admitted(pending)
        }
    }

    /** Waits for one pending stage while converting permanent service shutdown into a synchronous queue drain. */
    private suspend fun <T> awaitPendingOrServiceStop(deferred: Deferred<T>): T {
        val scopeJob = scope.coroutineContext[Job] ?: return deferred.await()
        val serviceStopped = select {
            deferred.onAwait { false }
            scopeJob.onJoin { true }
        }
        if (serviceStopped) {
            withContext(NonCancellable) {
                val failedPacketIds =
                    queueMutex.withLock { if (!deferred.isCompleted) stopAndDrainPacketQueueLocked() else emptyList() }
                changeStatusesNow(failedPacketIds, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
            }
        }
        return deferred.await()
    }

    override fun stopPacketQueue() {
        // Run async so callers (non-suspend) don't block, but all mutations are
        // serialized under the same mutexes used by the queue processor and senders.
        scope.handledLaunch {
            Logger.i { "Stopping packet queueJob" }
            withContext(NonCancellable) {
                val failedPacketIds =
                    queueMutex.withLock {
                        queueStopped = true
                        queueJob?.cancel()
                        queueJob = null
                        queueGeneration++
                        queuedPackets.clear()
                        completePendingResponses(AwaitedSendStatus.TRANSPORT_STOPPED)
                    }
                changeStatusesNow(failedPacketIds, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
            }
        }
    }

    override fun handleQueueStatus(queueStatus: QueueStatus) {
        Logger.d { "[queueStatus] ${queueStatus.toOneLineString()}" }
        val (success, isFull, requestId) =
            with(queueStatus) { Triple(res == 0 || res == ERRNO_SHOULD_RELEASE, free == 0, mesh_packet_id) }
        // Only plain res==0 "accepted, now full" is an advisory echo. ERRNO_SHOULD_RELEASE is synchronous local
        // delivery and therefore completes both the firmware-queue stage and any strict routing waiter.
        if (queueStatus.res == 0 && isFull && requestId == 0) return

        scope.handledLaunch {
            responseMutex.withLock {
                val entry =
                    if (requestId != 0) {
                        queueResponse[requestId]?.let { requestId to it }
                    } else {
                        // Firmware can omit mesh_packet_id for the active queue entry. Strict routing waiters whose
                        // queue stage already completed remain in the map, so correlate only an unfinished queue stage.
                        queueResponse.entries
                            .firstOrNull { (_, pending) -> pending.wasDispatched && !pending.queueDeferred.isCompleted }
                            ?.let { it.key to it.value }
                    }
                val (packetId, pending) = entry ?: return@withLock
                if (!pending.wasDispatched) return@withLock

                val status = success.toAwaitedSendStatus()
                pending.completeQueue(status, routingTerminal = !success || queueStatus.res == ERRNO_SHOULD_RELEASE)
                removePendingIfTerminalLocked(packetId, pending)
            }
        }
    }

    override suspend fun completeDispatchedResponse(dataRequestId: Int, complete: Boolean) {
        responseMutex.withLock {
            val pending = queueResponse[dataRequestId]?.takeIf { it.wasDispatched } ?: return@withLock
            pending.completeRouting(complete.toAwaitedSendStatus())
            removePendingIfTerminalLocked(dataRequestId, pending)
        }
    }

    /**
     * Starts the packet queue processor. Must be called while holding [queueMutex] to ensure the check-then-start is
     * atomic — preventing two concurrent callers from launching duplicate processors.
     */
    private fun startPacketQueueLocked() {
        check(queueMutex.isLocked) { "Packet queue workers must start while queueMutex is held" }
        if (queueStopped || queueJob?.isActive == true) return

        val generation = ++queueGeneration
        // Install cleanup before admission releases queueMutex so cancellation cannot bypass the worker's finally.
        // UNDISPATCHED enters immediately, then suspends on queueMutex until enqueuePacket releases the admission
        // lock; this closes the cancellation gap without running queue mutation inside the caller's critical section.
        queueJob =
            scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    while (connectionStateProvider.connectionState.value == ConnectionState.Connected) {
                        val queuedPacket = queueMutex.withLock { queuedPackets.removeFirstOrNull() } ?: break
                        processQueuedPacket(queuedPacket)
                    }
                } finally {
                    finishPacketQueueGeneration(generation)
                }
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun processQueuedPacket(queuedPacket: QueuedPacket) {
        val (packet, pending, expectedConnectionVersion) = queuedPacket
        try {
            val response = sendPacket(packet, pending, expectedConnectionVersion)
            Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting for QueueStatus" }
            val status = withTimeout(RESPONSE_TIMEOUT) { response.await() }
            Logger.d { "queueJob packet id=${packet.id.toUInt()} queue status $status" }
            if (status != AwaitedSendStatus.ACCEPTED) changeStatus(packet, MessageStatus.ERROR)
            removePendingIfTerminal(packet.id, pending)
        } catch (_: TimeoutCancellationException) {
            Logger.d { "queueJob packet id=${packet.id.toUInt()} queue response timeout" }
            responseMutex.withLock {
                // QueueStatus is a bounded, lossy phone-side signal in firmware. A timeout after transport dispatch is
                // therefore inconclusive: release the local queue stage, but keep any strict routing waiter alive.
                pending.completeQueue(AwaitedSendStatus.TIMED_OUT, routingTerminal = false)
                removePendingIfTerminalLocked(packet.id, pending)
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                responseMutex.withLock {
                    pending.completeAll(AwaitedSendStatus.TRANSPORT_STOPPED)
                    removePendingIfTerminalLocked(packet.id, pending)
                }
                if (pending.terminalStatus != AwaitedSendStatus.ACCEPTED) {
                    changeStatusNow(packet, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
                }
            }
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            Logger.w(e) { "queueJob packet id=${packet.id.toUInt()} failed" }
            responseMutex.withLock {
                pending.completeAll(pending.sendFailureStatus())
                removePendingIfTerminalLocked(packet.id, pending)
            }
            if (pending.terminalStatus != AwaitedSendStatus.ACCEPTED) changeStatus(packet, MessageStatus.ERROR)
        }
    }

    private suspend fun finishPacketQueueGeneration(generation: Long) = withContext(NonCancellable) {
        // Keep completion, replacement, and disconnect draining atomic with new admissions. queueGeneration
        // advances only under queueMutex: stopPacketQueue() drains every pending response, while
        // startPacketQueueLocked() advances it only after the previous queueJob is inactive. Therefore a stale
        // worker has already lost ownership to a path that drained or replaced it and must not clear its successor.
        val failedPacketIds =
            queueMutex.withLock {
                if (generation != queueGeneration) return@withLock emptyList()
                queueJob = null
                when {
                    queueStopped || !scope.isActive -> stopAndDrainPacketQueueLocked()

                    connectionStateProvider.connectionState.value != ConnectionState.Connected ->
                        stopAndDrainPacketQueueLocked()

                    queuedPackets.isNotEmpty() -> {
                        startPacketQueueLocked()
                        emptyList()
                    }

                    else -> emptyList() // Strict routing waiters may remain after their QueueStatus completed.
                }
            }
        changeStatusesNow(failedPacketIds, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
    }

    private suspend fun stopAndDrainPacketQueueLocked(): List<PacketStatusTarget> {
        queueStopped = true
        queuedPackets.clear()
        return completePendingResponses(AwaitedSendStatus.TRANSPORT_STOPPED)
    }

    override fun rearmSendAckTimeouts() {
        scope.handledLaunch {
            packetRepository.value.getEnroutePackets().forEach { persisted ->
                val remaining = persisted.packet.time + SEND_ACK_TIMEOUT.inWholeMilliseconds - nowMillis
                scheduleSendAckTimeout(
                    PersistedStatusTarget.DataPacket(persisted.id),
                    remaining.milliseconds.coerceAtLeast(REARM_GRACE),
                )
            }
            packetRepository.value.getEnrouteReactions().forEach { persisted ->
                val remaining = persisted.reaction.timestamp + SEND_ACK_TIMEOUT.inWholeMilliseconds - nowMillis
                scheduleSendAckTimeout(
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
     * Packet and reaction timers are keyed by stable persisted-row identity. Re-arming supersedes the pending timer for
     * the same target, and timers deliberately survive disconnects.
     */
    private suspend fun scheduleSendAckTimeout(target: PersistedStatusTarget, delayFor: Duration = SEND_ACK_TIMEOUT) {
        timeoutMutex.withLock {
            sendAckTimeoutJobs.remove(target)?.cancel()
            sendAckTimeoutJobs.values.removeAll { it.isCompleted }
            sendAckTimeoutJobs[target] =
                scope.handledLaunch {
                    delay(delayFor)
                    // Conditional in the DAO transaction: an ACK/NAK landing while this timer waited must win.
                    when (target) {
                        is PersistedStatusTarget.DataPacket ->
                            packetRepository.value.timeOutEnroutePacket(target.id, Routing.Error.TIMEOUT.value)

                        is PersistedStatusTarget.Reaction ->
                            packetRepository.value.timeOutEnrouteReaction(target.id, Routing.Error.TIMEOUT.value)
                    }
                }
        }
    }

    private fun changeStatus(packet: MeshPacket, status: MessageStatus) =
        scope.handledLaunch { changeStatusNow(packet, status) }

    private suspend fun changeStatusNow(
        packet: MeshPacket,
        status: MessageStatus,
        persistenceWait: Duration = PERSISTED_STATUS_WAIT,
    ) {
        changeStatusesNow(
            targets = listOf(PacketStatusTarget(packet, packet.statusPersistence())),
            status = status,
            persistenceWait = persistenceWait,
        )
    }

    /**
     * Applies [status] to durable rows, sharing one brief wait across app payloads whose inserts may still be in
     * flight. Data packets resolve by the full outgoing identity from #6624 before their exact persisted row is
     * mutated, so sender-scoped packet-ID collisions and terminal ACK/NAK states both fail closed.
     */
    private suspend fun changeStatusesNow(
        targets: Collection<PacketStatusTarget>,
        status: MessageStatus,
        persistenceWait: Duration = PERSISTED_STATUS_WAIT,
    ) {
        val waiting =
            targets
                .filter { it.packet.id != 0 && it.persistence != null }
                .distinctBy { it.packet.id }
                .associateByTo(mutableMapOf()) { it.packet.id }

        withTimeoutOrNull(persistenceWait) {
            while (waiting.isNotEmpty()) {
                waiting.values.toList().forEach { target ->
                    if (applyQueueStatus(target, status)) waiting.remove(target.packet.id)
                }
                if (waiting.isNotEmpty()) delay(PERSISTED_STATUS_RETRY_DELAY)
            }
        }
        waiting.keys.forEach { packetId -> logMissingStatusRow(packetId, status) }
    }

    private suspend fun applyQueueStatus(target: PacketStatusTarget, status: MessageStatus): Boolean =
        when (target.persistence) {
            StatusPersistence.DATA_PACKET -> {
                val persisted = packetRepository.value.applyOutgoingQueueStatus(target.packet, status) ?: return false
                val current = persisted.packet.status
                val shouldPersist =
                    status == MessageStatus.ENROUTE &&
                        (current == MessageStatus.ENROUTE || shouldApplyOutgoingQueueStatus(current, status))
                if (shouldPersist) {
                    scheduleSendAckTimeout(PersistedStatusTarget.DataPacket(persisted.id))
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
                    scheduleSendAckTimeout(PersistedStatusTarget.Reaction(persisted.id))
                }
                true
            }

            null -> true
        }

    private fun logMissingStatusRow(packetId: Int, status: MessageStatus) {
        Logger.d { "Skipping $status for mesh packet id=${packetId.toUInt()}: no unambiguous persisted row" }
    }

    private fun MeshPacket.statusPersistence(): StatusPersistence? {
        val data = decoded ?: return null
        return when {
            data.isReaction() -> StatusPersistence.REACTION
            data.portnum.value in PERSISTED_DATA_PORT_NUMBERS -> StatusPersistence.DATA_PACKET
            else -> null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendPacket(
        packet: MeshPacket,
        pending: PendingResponse,
        expectedConnectionVersion: Long?,
    ): Deferred<AwaitedSendStatus> {
        try {
            // Publish the dispatch epoch under the response mutex so an immediate QueueStatus/Routing response cannot
            // complete this packet before transport ownership is visible.
            responseMutex.withLock {
                val lifecycle = connectionStateProvider.connectionLifecycle.value
                if (
                    lifecycle.state !is ConnectionState.Connected ||
                    (expectedConnectionVersion != null && lifecycle.version != expectedConnectionVersion)
                ) {
                    throw RadioNotConnectedException()
                }
                val departureEpoch = lifecycle.epochs.departures
                val accepted = dispatchToRadio(ToRadio(packet = packet))
                pending.recordDispatch(accepted = accepted, departureEpoch = departureEpoch)
                if (!accepted) pending.completeAll(AwaitedSendStatus.SEND_FAILED)
            }
            if (pending.wasDispatched && pending.routingDeferred != null) {
                scheduleRoutingResponseExpiry(packet.id, pending)
            } else if (!pending.wasDispatched) {
                Logger.w { "sendToRadio dropped: no active transport accepted id=${packet.id.toUInt()}" }
            }
        } catch (ex: RadioNotConnectedException) {
            Logger.w(ex) { "sendToRadio skipped: Not connected to radio" }
            responseMutex.withLock { pending.completeAll(AwaitedSendStatus.TRANSPORT_STOPPED) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e(ex) { "sendToRadio error: ${ex.message}" }
            responseMutex.withLock { pending.completeAll(pending.sendFailureStatus()) }
        }
        return pending.queueDeferred
    }

    /**
     * Owns strict-routing expiry from service scope so caller cancellation cannot strand a packet-ID reservation. Queue
     * rejection, routing completion, disconnect, and service shutdown can all finish the pending response first; the
     * identity check in [removePendingIfTerminalLocked] keeps a late expiry from touching a reused ID.
     */
    private fun scheduleRoutingResponseExpiry(packetId: Int, pending: PendingResponse) {
        scope.handledLaunch {
            delay(ROUTING_RESPONSE_TIMEOUT)
            responseMutex.withLock {
                pending.completeRouting(AwaitedSendStatus.TIMED_OUT)
                removePendingIfTerminalLocked(packetId, pending)
            }
        }
    }

    private suspend fun removePendingIfTerminal(packetId: Int, pending: PendingResponse) =
        withContext(NonCancellable) { responseMutex.withLock { removePendingIfTerminalLocked(packetId, pending) } }

    private fun removePendingIfTerminalLocked(packetId: Int, pending: PendingResponse) {
        if (pending.isTerminal && queueResponse[packetId] === pending) queueResponse.remove(packetId)
    }

    private fun Boolean.toAwaitedSendStatus(): AwaitedSendStatus =
        if (this) AwaitedSendStatus.ACCEPTED else AwaitedSendStatus.RADIO_REJECTED

    private suspend fun completePendingResponses(status: AwaitedSendStatus): List<PacketStatusTarget> =
        responseMutex.withLock {
            val completedPackets =
                queueResponse.mapNotNull { (_, pending) ->
                    pending.completeAll(status)
                    PacketStatusTarget(pending.packet, pending.persistence).takeIf {
                        pending.terminalStatus != AwaitedSendStatus.ACCEPTED
                    }
                }
            queueResponse.clear()
            completedPackets
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
