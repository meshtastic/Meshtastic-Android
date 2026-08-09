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
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.QueueStatus
import org.meshtastic.proto.ToRadio
import kotlin.time.Duration.Companion.milliseconds
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
        private val PERSISTED_STATUS_WAIT = 1.seconds
        private val PERSISTED_STATUS_SHUTDOWN_WAIT = 150.milliseconds
        internal val PERSISTED_STATUS_RETRY_DELAY = 100.milliseconds

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

    private val queueMutex = Mutex()
    private val queuedPackets = mutableListOf<QueuedPacket>()

    // Marks the current queue generation as drained. The next admission clears it so reconnect recovery can start a
    // fresh worker; stale workers retain their generation and cannot replace that worker.
    private var queueStopped = false

    private val responseMutex = Mutex()

    private data class QueuedPacket(val packet: MeshPacket, val pending: PendingResponse)

    private enum class StatusPersistence {
        DATA_PACKET,
        REACTION,
    }

    private data class PacketStatusTarget(val packetId: Int, val persistence: StatusPersistence?)

    private data class PersistedStatusTarget(val packetId: Int, val persistence: StatusPersistence)

    private class PendingResponse(val persistence: StatusPersistence?) {
        val deferred: CompletableDeferred<AwaitedSendStatus> = CompletableDeferred()
        private val dispatchDepartureEpoch = atomic<Long?>(null)

        val wasDispatched: Boolean
            get() = dispatchDepartureEpoch.value != null

        val departureEpochAtDispatch: Long?
            get() = dispatchDepartureEpoch.value

        fun recordDispatch(accepted: Boolean, departureEpoch: Long) {
            if (accepted) dispatchDepartureEpoch.value = departureEpoch
        }
    }

    private sealed interface QueueAdmission {
        data class Admitted(val pending: PendingResponse) : QueueAdmission

        data object DuplicateId : QueueAdmission

        data object ScopeInactive : QueueAdmission

        data object TransportUnavailable : QueueAdmission
    }

    private val queueResponse = mutableMapOf<Int, PendingResponse>()

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
    override suspend fun sendToRadio(packet: MeshPacket): Boolean {
        if (packet.id == 0) {
            Logger.w { "Dropping queued packet without an ID" }
            return false
        }
        return when (enqueuePacket(packet)) {
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
        when (val admission = enqueuePacket(packet)) {
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
    private suspend fun awaitAdmittedPacket(packet: MeshPacket, pending: PendingResponse): AwaitedSendResult = try {
        // The queue processor owns both the response timeout and the ID reservation. Permanent
        // service-scope shutdown is the only caller-observed terminal condition because no worker may exist.
        val status = awaitPendingResponse(pending)
        val departureEpochAtDispatch = pending.departureEpochAtDispatch
        AwaitedSendResult(status = status, departureEpochAtDispatch = departureEpochAtDispatch)
    } catch (e: CancellationException) {
        throw e // Preserve structured concurrency cancellation propagation.
    } catch (e: Exception) {
        Logger.w(e) { "sendToRadioAndAwait packet id=${packet.id.toUInt()} failed" }
        pending.deferred.complete(AwaitedSendStatus.SEND_FAILED)
        val departureEpochAtDispatch = pending.departureEpochAtDispatch
        AwaitedSendResult(
            status = AwaitedSendStatus.SEND_FAILED,
            departureEpochAtDispatch = departureEpochAtDispatch,
        )
    }

    /**
     * Reserves [packet]'s non-zero ID and queues it as one atomic admission. The reservation spans both queued and
     * in-flight work, so a second sender cannot replace the original caller's [PendingResponse].
     */
    private suspend fun enqueuePacket(packet: MeshPacket): QueueAdmission = queueMutex.withLock {
        responseMutex.withLock responseLock@{
            if (!scope.isActive) return@responseLock QueueAdmission.ScopeInactive
            if (connectionStateProvider.connectionState.value != ConnectionState.Connected) {
                return@responseLock QueueAdmission.TransportUnavailable
            }
            if (queueResponse.containsKey(packet.id)) return@responseLock QueueAdmission.DuplicateId

            val pending = PendingResponse(persistence = packet.statusPersistence())
            queueResponse[packet.id] = pending
            queueStopped = false // Allow queue to resume after a disconnect/reconnect cycle.
            queuedPackets.add(QueuedPacket(packet = packet, pending = pending))
            startPacketQueueLocked()
            QueueAdmission.Admitted(pending)
        }
    }

    /** Waits for the queue-owned result, draining synchronously if service-scope shutdown wins admission. */
    private suspend fun awaitPendingResponse(pending: PendingResponse): AwaitedSendStatus {
        val scopeJob = scope.coroutineContext[Job] ?: return pending.deferred.await()
        val serviceStopped = select {
            pending.deferred.onAwait { false }
            // External callers observe service shutdown as TRANSPORT_STOPPED; callers inside the service scope retain
            // structured cancellation and leave through the surrounding CancellationException path.
            scopeJob.onJoin { true }
        }
        if (serviceStopped) {
            withContext(NonCancellable) {
                val failedPacketIds =
                    queueMutex.withLock {
                        if (!pending.deferred.isCompleted) stopAndDrainPacketQueueLocked() else emptyList()
                    }
                changeStatusesNow(failedPacketIds, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
            }
        }
        return pending.deferred.await()
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
        // Only the plain res==0 "queue accepted, now full" echo is skipped here. ERRNO_SHOULD_RELEASE denotes a
        // synchronous local-loopback delivery that still needs its queueResponse completed, even when free==0, or it
        // would hang until RESPONSE_TIMEOUT.
        if (queueStatus.res == 0 && isFull) return

        scope.handledLaunch {
            responseMutex.withLock {
                if (requestId != 0) {
                    queueResponse[requestId]
                        ?.takeIf { it.wasDispatched }
                        ?.deferred
                        ?.complete(success.toAwaitedSendStatus())
                } else {
                    // Firmware omits requestId for the active queue entry. The worker is serial, so at most one
                    // dispatched response can still be pending; preserve that invariant if dispatch is parallelized.
                    queueResponse.values
                        .firstOrNull { it.wasDispatched && !it.deferred.isCompleted }
                        ?.deferred
                        ?.complete(success.toAwaitedSendStatus())
                }
            }
        }
    }

    override suspend fun completeDispatchedResponse(dataRequestId: Int, complete: Boolean) {
        responseMutex.withLock {
            queueResponse[dataRequestId]
                ?.takeIf { it.wasDispatched }
                ?.deferred
                ?.complete(complete.toAwaitedSendStatus())
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
        val (packet, pending) = queuedPacket
        try {
            val response = sendPacket(packet, pending)
            Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting" }
            val status = withTimeout(RESPONSE_TIMEOUT) { response.await() }
            Logger.d { "queueJob packet id=${packet.id.toUInt()} status $status" }
            if (status != AwaitedSendStatus.ACCEPTED) changeStatus(packet, MessageStatus.ERROR)
            // External status paths only complete the response; the queue worker owns reservation removal so an ID
            // cannot be reused while its original packet can still be transmitted.
            removePendingResponse(packet.id, pending)
        } catch (_: TimeoutCancellationException) {
            Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
            // Complete an awaiting caller before removing the response. Its timeout starts here, after the packet is
            // actually sent, rather than while it waits behind the existing FIFO backlog.
            val completedByTimeout = pending.deferred.complete(AwaitedSendStatus.TIMED_OUT)
            // A response the transport already accepted must not be reclassified by the timeout.
            val terminalStatus = if (completedByTimeout) AwaitedSendStatus.TIMED_OUT else pending.deferred.await()
            if (terminalStatus != AwaitedSendStatus.ACCEPTED) changeStatus(packet, MessageStatus.ERROR)
            removePendingResponse(packet.id, pending)
        } catch (e: CancellationException) {
            // A later queued packet makes finishPacketQueueGeneration restart the worker instead of draining the
            // interrupted entry. Complete and remove this reservation before propagating cancellation. A response
            // the transport already accepted keeps its ACCEPTED status: shutdown preemption cannot reclassify it.
            val completedByCancellation = pending.deferred.complete(AwaitedSendStatus.TRANSPORT_STOPPED)
            val terminalStatus =
                if (completedByCancellation) {
                    AwaitedSendStatus.TRANSPORT_STOPPED
                } else {
                    withContext(NonCancellable) { pending.deferred.await() }
                }
            if (terminalStatus != AwaitedSendStatus.ACCEPTED) {
                withContext(NonCancellable) {
                    changeStatusNow(packet, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
                }
            }
            removePendingResponse(packet.id, pending)
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            Logger.w(e) { "queueJob packet id=${packet.id.toUInt()} failed" }
            pending.deferred.complete(AwaitedSendStatus.SEND_FAILED)
            changeStatus(packet, MessageStatus.ERROR)
            removePendingResponse(packet.id, pending)
        }
        // Queue shutdown can clear the same entry concurrently; identity-checked removal keeps every path idempotent.
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

                    else -> {
                        // A normally completed worker has no pending response. Anything left here belongs to an
                        // in-flight packet interrupted by cancellation or an unexpected worker exit.
                        completePendingResponses(AwaitedSendStatus.TRANSPORT_STOPPED)
                    }
                }
            }
        changeStatusesNow(failedPacketIds, MessageStatus.ERROR, PERSISTED_STATUS_SHUTDOWN_WAIT)
    }

    private suspend fun stopAndDrainPacketQueueLocked(): List<PacketStatusTarget> {
        queueStopped = true
        queuedPackets.clear()
        return completePendingResponses(AwaitedSendStatus.TRANSPORT_STOPPED)
    }

    private fun changeStatus(packet: MeshPacket, status: MessageStatus) =
        scope.handledLaunch { changeStatusNow(packet, status) }

    private suspend fun changeStatusNow(
        packet: MeshPacket,
        status: MessageStatus,
        persistenceWait: kotlin.time.Duration = PERSISTED_STATUS_WAIT,
    ) {
        changeStatusesNow(
            targets = listOf(PacketStatusTarget(packet.id, packet.statusPersistence())),
            status = status,
            persistenceWait = persistenceWait,
        )
    }

    /**
     * Applies [status] to durable rows, sharing one brief wait across app payloads whose inserts may still be in
     * flight.
     */
    private suspend fun changeStatusesNow(
        targets: Collection<PacketStatusTarget>,
        status: MessageStatus,
        persistenceWait: kotlin.time.Duration = PERSISTED_STATUS_WAIT,
    ) {
        val distinctTargets = targets.filter { it.packetId != 0 }.distinctBy(PacketStatusTarget::packetId)

        val waiting =
            distinctTargets
                .mapNotNull { target ->
                    target.persistence?.let { persistence -> PersistedStatusTarget(target.packetId, persistence) }
                }
                .associateByTo(mutableMapOf(), PersistedStatusTarget::packetId)
        withTimeoutOrNull(persistenceWait) {
            while (waiting.isNotEmpty()) {
                waiting.values.toList().forEach { target ->
                    val resolved =
                        when (target.persistence) {
                            StatusPersistence.DATA_PACKET ->
                                packetRepository.value.getPacketByPacketId(target.packetId)?.let { packet ->
                                    applyQueueStatus(packet, status)
                                    true
                                } ?: false

                            StatusPersistence.REACTION ->
                                packetRepository.value.getReactionByPacketId(target.packetId)?.let { reaction ->
                                    applyQueueStatus(reaction, status)
                                    true
                                } ?: false
                        }
                    if (resolved) waiting.remove(target.packetId)
                }
                if (waiting.isNotEmpty()) delay(PERSISTED_STATUS_RETRY_DELAY)
            }
        }
        waiting.keys.forEach { packetId -> logMissingStatusRow(packetId, status) }
    }

    private suspend fun applyQueueStatus(packet: DataPacket, status: MessageStatus) {
        if (shouldApplyQueueStatus(packet.status, status)) packetRepository.value.updateMessageStatus(packet, status)
    }

    private suspend fun applyQueueStatus(reaction: Reaction, status: MessageStatus) {
        if (shouldApplyQueueStatus(reaction.status, status)) {
            packetRepository.value.updateReaction(reaction.copy(status = status))
        }
    }

    private fun shouldApplyQueueStatus(current: MessageStatus?, status: MessageStatus): Boolean = when (status) {
        MessageStatus.ENROUTE ->
            current == null || current == MessageStatus.UNKNOWN || current == MessageStatus.QUEUED

        MessageStatus.ERROR ->
            current == null ||
                current == MessageStatus.UNKNOWN ||
                current == MessageStatus.QUEUED ||
                current == MessageStatus.ENROUTE

        else -> current != status
    }

    private fun logMissingStatusRow(packetId: Int, status: MessageStatus) {
        Logger.d { "Skipping $status for mesh packet id=${packetId.toUInt()}: no persisted packet row" }
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
    private suspend fun sendPacket(packet: MeshPacket, pending: PendingResponse): Deferred<AwaitedSendStatus> {
        try {
            requireConnected()
            // Serialize dispatch publication with response callbacks. Even a transport that schedules a callback
            // immediately cannot complete this response before wasDispatched reflects the admission result.
            responseMutex.withLock {
                val departureEpoch = connectionStateProvider.connectionLifecycle.value.epochs.departures
                pending.recordDispatch(
                    accepted = dispatchToRadio(ToRadio(packet = packet)),
                    departureEpoch = departureEpoch,
                )
            }
            if (!pending.wasDispatched) {
                Logger.w { "sendToRadio dropped: no active transport accepted id=${packet.id.toUInt()}" }
                pending.deferred.complete(AwaitedSendStatus.SEND_FAILED)
            }
        } catch (ex: RadioNotConnectedException) {
            Logger.w(ex) { "sendToRadio skipped: Not connected to radio" }
            pending.deferred.complete(AwaitedSendStatus.TRANSPORT_STOPPED)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e(ex) { "sendToRadio error: ${ex.message}" }
            pending.deferred.complete(AwaitedSendStatus.SEND_FAILED)
        }
        // The declared Deferred return type exposes only read-only completion to callers.
        return pending.deferred
    }

    private suspend fun removePendingResponse(packetId: Int, pending: PendingResponse) = withContext(NonCancellable) {
        responseMutex.withLock { if (queueResponse[packetId] === pending) queueResponse.remove(packetId) }
    }

    private fun requireConnected() {
        if (connectionStateProvider.connectionState.value != ConnectionState.Connected) {
            throw RadioNotConnectedException()
        }
    }

    private fun Boolean.toAwaitedSendStatus(): AwaitedSendStatus =
        if (this) AwaitedSendStatus.ACCEPTED else AwaitedSendStatus.RADIO_REJECTED

    private suspend fun completePendingResponses(status: AwaitedSendStatus): List<PacketStatusTarget> =
        responseMutex.withLock {
            val completedPackets =
                queueResponse.mapNotNull { (packetId, pending) ->
                    PacketStatusTarget(packetId, pending.persistence).takeIf { pending.deferred.complete(status) }
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
