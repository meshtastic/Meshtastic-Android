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
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.model.Node

interface TAKServerManager {
    val isRunning: StateFlow<Boolean>
    val connectionCount: StateFlow<Int>
    val inboundMessages: SharedFlow<CoTMessage>

    /** Start the TAK server using [scope]. Port is fixed at [TAKServer] construction time. */
    fun start(scope: CoroutineScope)

    fun stop()

    fun broadcastNode(node: Node, team: String = DEFAULT_TAK_TEAM_NAME, role: String = DEFAULT_TAK_ROLE_NAME)

    fun broadcast(cotMessage: CoTMessage)
}

class TAKServerManagerImpl(private val takServer: TAKServer) : TAKServerManager {

    private var scope: CoroutineScope? = null
    private val lastBroadcastPositionsMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Mirror TAKServer's event-driven connection count — no polling needed
    override val connectionCount: StateFlow<Int> = takServer.connectionCount

    private val _inboundMessages = MutableSharedFlow<CoTMessage>()
    override val inboundMessages: SharedFlow<CoTMessage> = _inboundMessages.asSharedFlow()

    // Unbounded channel preserves FIFO ordering of inbound CoT messages under load.
    // onMessage is a non-suspend callback, so we trySend (always succeeds for UNLIMITED)
    // and a single consumer coroutine drains into _inboundMessages in order.
    private var inboundChannel: Channel<CoTMessage>? = null
    private var inboundDrainJob: Job? = null

    private var lastBroadcastPositions = mutableMapOf<Int, Int>()

    override fun start(scope: CoroutineScope) {
        this.scope = scope
        if (_isRunning.value) {
            Logger.w { "TAKServerManager already running" }
            return
        }

        scope.launch {
            // Wire up inbound message handler BEFORE starting so no messages are lost.
            val channel = Channel<CoTMessage>(Channel.UNLIMITED)
            inboundChannel = channel
            inboundDrainJob = scope.launch { channel.consumeAsFlow().collect { _inboundMessages.emit(it) } }
            takServer.onMessage = { cotMessage -> channel.trySend(cotMessage) }

            val result = takServer.start(scope)
            if (result.isSuccess) {
                _isRunning.value = true
                Logger.i { "TAK Server started" }
            } else {
                Logger.e(result.exceptionOrNull()) { "Failed to start TAK Server" }
                // Clear onMessage if start failed so we don't hold a reference unnecessarily
                takServer.onMessage = null
                inboundDrainJob?.cancel()
                inboundDrainJob = null
                channel.close()
                inboundChannel = null
            }
        }
    }

    override fun stop() {
        takServer.stop()
        takServer.onMessage = null
        inboundChannel?.close()
        inboundChannel = null
        inboundDrainJob?.cancel()
        inboundDrainJob = null
        _isRunning.value = false
        scope = null
        Logger.i { "TAK Server stopped" }
    }

    override fun broadcastNode(node: Node, team: String, role: String) {
        if (!_isRunning.value) return
        val currentScope = scope ?: return

        currentScope.launch {
            if (!takServer.hasConnections()) return@launch

            val position = node.validPosition
            if (position == null) {
                broadcastNodeInfoOnly(node, team, role)
                return@launch
            }

            val shouldBroadcast =
                lastBroadcastPositionsMutex.withLock {
                    val last = lastBroadcastPositions[node.num]
                    if (position.time == last) {
                        false
                    } else {
                        lastBroadcastPositions[node.num] = position.time
                        true
                    }
                }
            if (!shouldBroadcast) return@launch

            val cotMessage =
                position.toCoTMessage(
                    uid = node.user.id,
                    callsign = node.user.toTakCallsign(),
                    team = team,
                    role = role,
                    battery = node.deviceMetrics.battery_level ?: 100,
                )

            takServer.broadcast(cotMessage)
        }
    }

    private fun broadcastNodeInfoOnly(node: Node, team: String, role: String) {
        val currentScope = scope ?: return
        val cotMessage =
            node.user.toCoTMessage(
                position = null,
                team = team,
                role = role,
                battery = node.deviceMetrics.battery_level ?: 100,
            )

        currentScope.launch {
            if (!takServer.hasConnections()) return@launch
            takServer.broadcast(cotMessage)
        }
    }

    override fun broadcast(cotMessage: CoTMessage) {
        scope?.launch { takServer.broadcast(cotMessage) }
    }
}
