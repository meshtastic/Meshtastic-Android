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
package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** A CoT message received from a connected TAK client, paired with the client's identity. */
data class InboundCoTMessage(val cotMessage: CoTMessage, val clientInfo: TAKClientInfo? = null)

interface TAKServerManager {
    val isRunning: StateFlow<Boolean>
    val connectionCount: StateFlow<Int>
    val inboundMessages: SharedFlow<InboundCoTMessage>

    /** Start the TAK server using [scope]. Port is fixed at [TAKServer] construction time. */
    fun start(scope: CoroutineScope)

    fun stop()

    fun broadcast(cotMessage: CoTMessage)

    /** Broadcast raw XML verbatim to TAK clients, bypassing CoTMessage parsing. */
    fun broadcastRawXml(xml: String)
}

class TAKServerManagerImpl(private val takServer: TAKServer) : TAKServerManager {

    private var scope: CoroutineScope? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Mirror TAKServer's event-driven connection count — no polling needed
    override val connectionCount: StateFlow<Int> = takServer.connectionCount

    private val _inboundMessages = MutableSharedFlow<InboundCoTMessage>()
    override val inboundMessages: SharedFlow<InboundCoTMessage> = _inboundMessages.asSharedFlow()

    // Offline message queue — buffers mesh-originated CoT messages when no TAK
    // clients are connected, then drains them when a client reconnects. Entries
    // expire after OFFLINE_QUEUE_TTL to avoid delivering stale situational data.
    private data class QueuedMessage(val cotMessage: CoTMessage, val enqueuedAt: kotlin.time.Instant)
    private val offlineQueue = ArrayDeque<QueuedMessage>()
    private val offlineQueueMutex = Mutex()

    companion object {
        private val OFFLINE_QUEUE_TTL = 5.minutes
        private const val OFFLINE_QUEUE_MAX_SIZE = 50
    }

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        if (_isRunning.value) {
            Logger.w { "TAKServerManager already running" }
            return
        }

        scope.launch {
            // Wire up inbound message handler BEFORE starting so no messages are lost
            takServer.onMessage = { cotMessage, clientInfo ->
                scope.launch { _inboundMessages.emit(InboundCoTMessage(cotMessage, clientInfo)) }
            }
            takServer.onClientConnected = { drainOfflineQueue() }

            val result = takServer.start(scope)
            if (result.isSuccess) {
                _isRunning.value = true
                Logger.i { "TAK Server started" }
            } else {
                Logger.e(result.exceptionOrNull()) { "Failed to start TAK Server" }
                // Clear onMessage if start failed so we don't hold a reference unnecessarily
                takServer.onMessage = null
            }
        }
    }

    override fun stop() {
        takServer.stop()
        takServer.onMessage = null
        _isRunning.value = false
        scope = null
        Logger.i { "TAK Server stopped" }
    }

    override fun broadcast(cotMessage: CoTMessage) {
        scope?.launch {
            if (takServer.hasConnections()) {
                takServer.broadcast(cotMessage)
            } else {
                // No TAK clients connected — queue for delivery when one reconnects
                offlineQueueMutex.withLock {
                    // Evict expired entries
                    val cutoff = Clock.System.now() - OFFLINE_QUEUE_TTL
                    while (offlineQueue.isNotEmpty() && offlineQueue.first().enqueuedAt < cutoff) {
                        offlineQueue.removeFirst()
                    }
                    // Cap size to prevent unbounded growth
                    if (offlineQueue.size >= OFFLINE_QUEUE_MAX_SIZE) {
                        offlineQueue.removeFirst()
                    }
                    offlineQueue.addLast(QueuedMessage(cotMessage, Clock.System.now()))
                }
            }
        }
    }

    override fun broadcastRawXml(xml: String) {
        scope?.launch { takServer.broadcastRawXml(xml) }
    }

    /** Drain any queued messages to the newly connected TAK client. Called by the server
     *  when a TAK client connects (Connected event). */
    internal fun drainOfflineQueue() {
        scope?.launch {
            val messages = offlineQueueMutex.withLock {
                val cutoff = Clock.System.now() - OFFLINE_QUEUE_TTL
                val valid = offlineQueue.filter { it.enqueuedAt >= cutoff }.map { it.cotMessage }
                offlineQueue.clear()
                valid
            }
            if (messages.isNotEmpty()) {
                Logger.i { "Draining ${messages.size} queued message(s) to reconnected TAK client" }
                messages.forEach { takServer.broadcast(it) }
            }
        }
    }
}
