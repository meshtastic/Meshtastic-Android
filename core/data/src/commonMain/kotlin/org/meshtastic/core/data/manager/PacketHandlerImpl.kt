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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.model.util.toOneLineString
import org.meshtastic.core.model.util.toPIIString
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
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
    private val serviceBroadcasts: ServiceBroadcasts,
    private val radioInterfaceService: RadioInterfaceService,
    private val meshLogRepository: Lazy<MeshLogRepository>,
    private val serviceRepository: ServiceRepository,
    @Named("ServiceScope") private val scope: CoroutineScope,
) : PacketHandler {

    companion object {
        private val TIMEOUT = 5.seconds
    }

    private var queueJob: Job? = null

    private val queueMutex = Mutex()
    private val queuedPackets = mutableListOf<MeshPacket>()

    // Unbounded channel preserves FIFO ordering of fire-and-forget sendToRadio(MeshPacket)
    // calls. The non-suspend entry point does trySend (always succeeds for UNLIMITED) and
    // a single consumer coroutine enqueues packets under queueMutex in arrival order.
    private val outboundChannel = Channel<MeshPacket>(Channel.UNLIMITED)

    // Set to true by stopPacketQueue() under queueMutex. Checked by startPacketQueueLocked()
    // and the queue processor's finally block to prevent restarting a stopped queue.
    private var queueStopped = false

    private val responseMutex = Mutex()
    private val queueResponse = mutableMapOf<Int, CompletableDeferred<Boolean>>()

    init {
        // Single consumer serializes enqueues from the non-suspend sendToRadio(MeshPacket)
        // entry point, preserving FIFO across rapid concurrent callers.
        scope.launch {
            outboundChannel.consumeAsFlow().collect { packet ->
                queueMutex.withLock {
                    queueStopped = false // Allow queue to resume after a disconnect/reconnect cycle.
                    queuedPackets.add(packet)
                    startPacketQueueLocked()
                }
            }
        }
    }

    override fun sendToRadio(p: ToRadio) {
        Logger.d { "Sending to radio ${p.toPIIString()}" }
        val b = p.encode()

        radioInterfaceService.sendToRadio(b)
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
    }

    override fun sendToRadio(packet: MeshPacket) {
        // Non-suspend entry point — order-preserving via unbounded channel drained by
        // a single consumer coroutine. trySend on UNLIMITED never fails for capacity.
        outboundChannel.trySend(packet)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun sendToRadioAndAwait(packet: MeshPacket): Boolean {
        // Pre-register the deferred so the queue processor and QueueStatus handler
        // can find it immediately — no polling required.
        val deferred = CompletableDeferred<Boolean>()
        responseMutex.withLock { queueResponse[packet.id] = deferred }
        queueMutex.withLock {
            queueStopped = false // Allow queue to resume after a disconnect/reconnect cycle.
            queuedPackets.add(packet)
            startPacketQueueLocked()
        }
        return try {
            withTimeout(TIMEOUT) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Logger.d { "sendToRadioAndAwait packet id=${packet.id.toUInt()} timeout" }
            false
        } catch (e: CancellationException) {
            throw e // Preserve structured concurrency cancellation propagation.
        } catch (e: Exception) {
            Logger.d { "sendToRadioAndAwait packet id=${packet.id.toUInt()} failed: ${e.message}" }
            false
        } finally {
            responseMutex.withLock { queueResponse.remove(packet.id) }
        }
    }

    override fun stopPacketQueue() {
        // Run async so callers (non-suspend) don't block, but all mutations are
        // serialized under the same mutexes used by the queue processor and senders.
        scope.launch {
            Logger.i { "Stopping packet queueJob" }
            queueMutex.withLock {
                queueStopped = true
                queueJob?.cancel()
                queueJob = null
                queuedPackets.clear()
            }
            responseMutex.withLock {
                queueResponse.values.forEach { if (!it.isCompleted) it.complete(false) }
                queueResponse.clear()
            }
        }
    }

    override fun handleQueueStatus(queueStatus: QueueStatus) {
        Logger.d { "[queueStatus] ${queueStatus.toOneLineString()}" }
        val (success, isFull, requestId) = with(queueStatus) { Triple(res == 0, free == 0, mesh_packet_id) }
        if (success && isFull) return

        scope.launch {
            responseMutex.withLock {
                if (requestId != 0) {
                    queueResponse.remove(requestId)?.complete(success)
                } else {
                    queueResponse.values.firstOrNull { !it.isCompleted }?.complete(success)
                }
            }
        }
    }

    override fun removeResponse(dataRequestId: Int, complete: Boolean) {
        scope.launch { responseMutex.withLock { queueResponse.remove(dataRequestId)?.complete(complete) } }
    }

    /**
     * Starts the packet queue processor. Must be called while holding [queueMutex] to ensure the check-then-start is
     * atomic — preventing two concurrent callers from launching duplicate processors.
     */
    private fun startPacketQueueLocked() {
        if (queueStopped) return
        if (queueJob?.isActive == true) return
        queueJob =
            scope.handledLaunch {
                try {
                    while (serviceRepository.connectionState.value == ConnectionState.Connected) {
                        val packet = queueMutex.withLock { queuedPackets.removeFirstOrNull() } ?: break
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            val response = sendPacket(packet)
                            Logger.d { "queueJob packet id=${packet.id.toUInt()} waiting" }
                            val success = withTimeout(TIMEOUT) { response.await() }
                            Logger.d { "queueJob packet id=${packet.id.toUInt()} success $success" }
                        } catch (e: TimeoutCancellationException) {
                            Logger.d { "queueJob packet id=${packet.id.toUInt()} timeout" }
                            // Clean up the deferred for this packet. sendToRadioAndAwait callers
                            // also clean up in their own finally block (idempotent remove).
                            responseMutex.withLock { queueResponse.remove(packet.id) }
                        } catch (e: CancellationException) {
                            throw e // Preserve structured concurrency cancellation propagation.
                        } catch (e: Exception) {
                            Logger.d { "queueJob packet id=${packet.id.toUInt()} failed" }
                            responseMutex.withLock { queueResponse.remove(packet.id) }
                        }
                        // Deferred cleanup is now handled in the catch blocks above.
                        // handleQueueStatus (normal success) and stopPacketQueue (bulk cleanup)
                        // also remove entries, and these removals are idempotent.
                    }
                } finally {
                    // Hold queueMutex so that clearing queueJob and the restart decision are
                    // atomic with respect to new senders calling startPacketQueueLocked().
                    queueMutex.withLock {
                        queueJob = null
                        if (!queueStopped && queuedPackets.isNotEmpty()) {
                            startPacketQueueLocked()
                        }
                    }
                }
            }
    }

    private fun changeStatus(packetId: Int, m: MessageStatus) = scope.handledLaunch {
        if (packetId != 0) {
            getDataPacketById(packetId)?.let { p ->
                if (p.status == m) return@handledLaunch
                packetRepository.value.updateMessageStatus(p, m)
                serviceBroadcasts.broadcastMessageStatus(packetId, m)
            }
        }
    }

    private suspend fun getDataPacketById(packetId: Int): DataPacket? = withTimeoutOrNull(1.seconds) {
        var dataPacket: DataPacket? = null
        while (dataPacket == null) {
            dataPacket = packetRepository.value.getPacketById(packetId)
            if (dataPacket == null) delay(100.milliseconds)
        }
        dataPacket
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendPacket(packet: MeshPacket): CompletableDeferred<Boolean> {
        // Reuse a deferred pre-registered by sendToRadioAndAwait, or create a new one.
        val deferred = responseMutex.withLock { queueResponse.getOrPut(packet.id) { CompletableDeferred() } }
        try {
            if (serviceRepository.connectionState.value != ConnectionState.Connected) {
                throw RadioNotConnectedException()
            }
            sendToRadio(ToRadio(packet = packet))
        } catch (ex: RadioNotConnectedException) {
            Logger.w(ex) { "sendToRadio skipped: Not connected to radio" }
            deferred.complete(false)
        } catch (ex: Exception) {
            Logger.e(ex) { "sendToRadio error: ${ex.message}" }
            deferred.complete(false)
        }
        return deferred
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
