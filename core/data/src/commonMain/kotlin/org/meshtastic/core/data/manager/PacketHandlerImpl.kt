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
        private val TIMEOUT = 5.seconds

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

    // Set to true by stopPacketQueue() under queueMutex. Checked by startPacketQueueLocked()
    // and the queue processor's finally block to prevent restarting a stopped queue.
    private var queueStopped = false

    private val responseMutex = Mutex()

    private data class QueuedPacket(val packet: MeshPacket, val pending: PendingResponse)

    private class PendingResponse {
        val deferred: CompletableDeferred<AwaitedSendStatus> = CompletableDeferred()
        private val dispatched = atomic(false)

        val wasDispatched: Boolean
            get() = dispatched.value

        fun recordDispatch(accepted: Boolean) {
            dispatched.value = accepted
        }
    }

    private val queueResponse = mutableMapOf<Int, PendingResponse>()

    override fun sendToRadio(p: ToRadio) {
        dispatchToRadio(p)
    }

    private fun dispatchToRadio(p: ToRadio): Boolean {
        Logger.d { "Sending to radio ${p.toPIIString()}" }
        val dispatched = radioInterfaceService.trySendToRadio(p.encode())
        if (!dispatched) return false

        p.packet?.id?.let { changeStatus(it, MessageStatus.ENROUTE) }
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
        val pending = packet.takeIf { it.id != 0 }?.let { enqueuePacket(it) }
        when {
            packet.id == 0 -> Logger.w { "Dropping queued packet without an ID" }

            pending == null && !scope.isActive ->
                Logger.w { "Rejecting packet id=${packet.id.toUInt()}: service scope is no longer active" }

            pending == null -> Logger.w { "Rejecting packet with reserved id=${packet.id.toUInt()}" }
        }
        return pending != null
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun sendToRadioAndAwaitResult(packet: MeshPacket): AwaitedSendResult {
        val pending = packet.takeIf { it.id != 0 }?.let { enqueuePacket(it) }
        if (pending == null) {
            val status =
                when {
                    packet.id == 0 -> {
                        Logger.w { "Rejecting awaited packet without an ID" }
                        AwaitedSendStatus.REJECTED
                    }

                    !scope.isActive -> {
                        Logger.w {
                            "Rejecting awaited packet id=${packet.id.toUInt()}: service scope is no longer active"
                        }
                        AwaitedSendStatus.TRANSPORT_STOPPED
                    }

                    else -> {
                        Logger.w { "Rejecting duplicate awaited packet id=${packet.id.toUInt()}" }
                        AwaitedSendStatus.REJECTED
                    }
                }
            return AwaitedSendResult(status = status, dispatched = false)
        }
        return try {
            // The queue processor owns both the response timeout and the ID reservation. Permanent
            // service-scope shutdown is the only caller-observed terminal condition because no worker may exist.
            AwaitedSendResult(status = awaitPendingResponse(pending), dispatched = pending.wasDispatched)
        } catch (e: CancellationException) {
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            Logger.d { "sendToRadioAndAwait packet id=${packet.id.toUInt()} failed: ${e.message}" }
            AwaitedSendResult(status = AwaitedSendStatus.SEND_FAILED, dispatched = pending.wasDispatched)
        }
    }

    /**
     * Reserves [packet]'s non-zero ID and queues it as one atomic admission. The reservation spans both queued and
     * in-flight work, so a second sender cannot replace the original caller's [PendingResponse].
     */
    private suspend fun enqueuePacket(packet: MeshPacket): PendingResponse? = queueMutex.withLock {
        responseMutex.withLock responseLock@{
            if (!scope.isActive || queueResponse.containsKey(packet.id)) return@responseLock null

            val pending = PendingResponse()
            queueResponse[packet.id] = pending
            queueStopped = false // Allow queue to resume after a disconnect/reconnect cycle.
            queuedPackets.add(QueuedPacket(packet = packet, pending = pending))
            startPacketQueueLocked()
            pending
        }
    }

    /** Waits for the queue-owned result, draining synchronously if service-scope shutdown wins admission. */
    private suspend fun awaitPendingResponse(pending: PendingResponse): AwaitedSendStatus {
        val scopeJob = scope.coroutineContext[Job] ?: return pending.deferred.await()
        val serviceStopped = select {
            pending.deferred.onAwait { false }
            scopeJob.onJoin { true }
        }
        if (serviceStopped) {
            withContext(NonCancellable) {
                val failedPacketIds =
                    queueMutex.withLock {
                        if (!pending.deferred.isCompleted) stopAndDrainPacketQueueLocked() else emptyList()
                    }
                changeStatusesNow(failedPacketIds, MessageStatus.ERROR)
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
                changeStatusesNow(failedPacketIds, MessageStatus.ERROR)
            }
        }
    }

    override fun handleQueueStatus(queueStatus: QueueStatus) {
        Logger.d { "[queueStatus] ${queueStatus.toOneLineString()}" }
        val (success, isFull, requestId) =
            with(queueStatus) { Triple(res == 0 || res == ERRNO_SHOULD_RELEASE, free == 0, mesh_packet_id) }
        // Only the plain res==0 "queue accepted, now full" echo is skipped here. ERRNO_SHOULD_RELEASE denotes a
        // synchronous local-loopback delivery that still needs its queueResponse completed, even when free==0, or it
        // would hang until TIMEOUT.
        if (queueStatus.res == 0 && isFull) return

        scope.handledLaunch {
            responseMutex.withLock {
                if (requestId != 0) {
                    queueResponse[requestId]
                        ?.takeIf { it.wasDispatched }
                        ?.deferred
                        ?.complete(success.toAwaitedSendStatus())
                } else {
                    queueResponse.values
                        .firstOrNull { it.wasDispatched && !it.deferred.isCompleted }
                        ?.deferred
                        ?.complete(success.toAwaitedSendStatus())
                }
            }
        }
    }

    override suspend fun removeResponse(dataRequestId: Int, complete: Boolean) {
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

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun processQueuedPacket(queuedPacket: QueuedPacket) {
        val (packet, pending) = queuedPacket
        try {
            val response = sendPacket(packet, pending)
            Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting" }
            val status = withTimeout(TIMEOUT) { response.await() }
            Logger.d { "queueJob packet id=${packet.id.toUInt()} status $status" }
            if (status != AwaitedSendStatus.ACCEPTED) changeStatus(packet.id, MessageStatus.ERROR)
            // External status paths only complete the response; the queue worker owns reservation removal so an ID
            // cannot be reused while its original packet can still be transmitted.
            removePendingResponse(packet.id, pending)
        } catch (e: TimeoutCancellationException) {
            Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
            // Complete an awaiting caller before removing the response. Its timeout starts here, after the packet is
            // actually sent, rather than while it waits behind the existing FIFO backlog.
            pending.deferred.complete(AwaitedSendStatus.TIMED_OUT)
            // A response the transport already accepted must not be reclassified by the timeout.
            if (pending.deferred.getCompleted() != AwaitedSendStatus.ACCEPTED) {
                changeStatus(packet.id, MessageStatus.ERROR)
            }
            removePendingResponse(packet.id, pending)
        } catch (e: CancellationException) {
            // A later queued packet makes finishPacketQueueGeneration restart the worker instead of draining the
            // interrupted entry. Complete and remove this reservation before propagating cancellation. A response
            // the transport already accepted keeps its ACCEPTED status: shutdown preemption cannot reclassify it.
            pending.deferred.complete(AwaitedSendStatus.TRANSPORT_STOPPED)
            if (pending.deferred.getCompleted() != AwaitedSendStatus.ACCEPTED) {
                changeStatus(packet.id, MessageStatus.ERROR)
            }
            removePendingResponse(packet.id, pending)
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            Logger.d { "queueJob packet id=${packet.id.toUInt()} failed" }
            pending.deferred.complete(AwaitedSendStatus.SEND_FAILED)
            changeStatus(packet.id, MessageStatus.ERROR)
            removePendingResponse(packet.id, pending)
        }
        // Queue shutdown can clear the same entry concurrently; identity-checked removal keeps every path idempotent.
    }

    private suspend fun finishPacketQueueGeneration(generation: Long) = withContext(NonCancellable) {
        // Keep completion, replacement, and disconnect draining atomic with new admissions. queueGeneration
        // advances only under queueMutex: stopPacketQueue() drains every pending response, while
        // startPacketQueueLocked() advances it only after the previous queueJob is inactive. Therefore a stale
        // worker has already lost ownership to a path that drained or replaced it and must not clear that
        // replacement
        // job.
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
        changeStatusesNow(failedPacketIds, MessageStatus.ERROR)
    }

    private suspend fun stopAndDrainPacketQueueLocked(): List<Int> {
        queueStopped = true
        queuedPackets.clear()
        return completePendingResponses(AwaitedSendStatus.TRANSPORT_STOPPED)
    }

    private fun changeStatus(packetId: Int, m: MessageStatus) =
        scope.handledLaunch { changeStatusesNow(listOf(packetId), m) }

    /** Resolves and updates all known packet IDs within one shared bound, including during non-cancellable teardown. */
    private suspend fun changeStatusesNow(packetIds: Collection<Int>, status: MessageStatus) {
        val remaining = packetIds.filterTo(mutableSetOf()) { it != 0 }
        withTimeoutOrNull(1.seconds) {
            while (remaining.isNotEmpty()) {
                remaining.toList().forEach { packetId ->
                    val packet = packetRepository.value.getPacketById(packetId) ?: return@forEach
                    if (packet.status != status) packetRepository.value.updateMessageStatus(packet, status)
                    remaining.remove(packetId)
                }
                if (remaining.isNotEmpty()) delay(100.milliseconds)
            }
        }
        if (remaining.isNotEmpty()) {
            Logger.w { "Could not apply $status to ${remaining.size} unresolved packet IDs within 1 second" }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendPacket(packet: MeshPacket, pending: PendingResponse): Deferred<AwaitedSendStatus> {
        try {
            requireConnected()
            // Serialize dispatch publication with response callbacks. Even a transport that schedules a callback
            // immediately cannot complete this response before wasDispatched reflects the admission result.
            responseMutex.withLock { pending.recordDispatch(dispatchToRadio(ToRadio(packet = packet))) }
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

    private suspend fun completePendingResponses(status: AwaitedSendStatus): List<Int> = responseMutex.withLock {
        val completedPacketIds =
            queueResponse.mapNotNull { (packetId, pending) ->
                packetId.takeIf { pending.deferred.complete(status) }
            }
        queueResponse.clear()
        completedPacketIds
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
