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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.model.Node
import org.meshtastic.core.takserver.CoTConversion.toCoTMessage

interface TAKServerManager {
    val isRunning: StateFlow<Boolean>
    val connectionCount: StateFlow<Int>
    val inboundMessages: SharedFlow<CoTMessage>

    fun start(scope: CoroutineScope, port: Int = DEFAULT_TAK_PORT)

    fun stop()

    fun broadcastNode(node: Node, team: String = DEFAULT_TAK_TEAM_NAME, role: String = DEFAULT_TAK_ROLE_NAME)

    fun broadcast(cotMessage: CoTMessage)
}

class TAKServerManagerImpl(private val takServer: TAKServer) : TAKServerManager {

    private var scope: CoroutineScope? = null
    private val lastBroadcastPositionsMutex = Mutex()

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _connectionCount = MutableStateFlow(0)
    override val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    private val _inboundMessages = MutableSharedFlow<CoTMessage>()
    override val inboundMessages: SharedFlow<CoTMessage> = _inboundMessages.asSharedFlow()

    private var lastBroadcastPositions = mutableMapOf<Int, Int>()

    override fun start(scope: CoroutineScope, port: Int) {
        this.scope = scope
        if (_isRunning.value) {
            Logger.w { "TAKServerManager already running" }
            return
        }

        scope.launch {
            val result = takServer.start(scope)
            if (result.isSuccess) {
                _isRunning.value = true
                Logger.i { "TAK Server started on port $port" }

                takServer.onMessage = { cotMessage -> scope.launch { _inboundMessages.emit(cotMessage) } }

                monitorConnections()
            } else {
                Logger.e(result.exceptionOrNull()) { "Failed to start TAK Server" }
            }
        }
    }

    private fun monitorConnections() {
        scope?.launch {
            while (_isRunning.value && isActive) {
                _connectionCount.value = takServer.connectionCount()
                delay(TAK_CONNECTION_POLL_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        takServer.stop()
        _isRunning.value = false
        _connectionCount.value = 0
        Logger.i { "TAK Server stopped" }
    }

    override fun broadcastNode(node: Node, team: String, role: String) {
        if (!_isRunning.value) return

        scope?.launch {
            if (!takServer.hasConnections()) return@launch

            val position = node.validPosition
            if (position == null) {
                broadcastNodeInfoOnly(node, team, role)
                return@launch
            }

            val lastPositionTime = lastBroadcastPositionsMutex.withLock { lastBroadcastPositions[node.num] }
            if (position.time == lastPositionTime) return@launch
            lastBroadcastPositionsMutex.withLock { lastBroadcastPositions[node.num] = position.time }

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
        val cotMessage =
            node.user.toCoTMessage(
                position = null,
                team = team,
                role = role,
                battery = node.deviceMetrics.battery_level ?: 100,
            )

        scope?.launch {
            if (!takServer.hasConnections()) return@launch
            takServer.broadcast(cotMessage)
        }
    }

    override fun broadcast(cotMessage: CoTMessage) {
        scope?.launch { takServer.broadcast(cotMessage) }
    }
}
