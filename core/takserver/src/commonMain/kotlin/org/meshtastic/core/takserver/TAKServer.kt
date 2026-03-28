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
@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.random.Random
import kotlinx.coroutines.isActive as coroutineIsActive

class TAKServer(private val dispatchers: CoroutineDispatchers, private val port: Int = DEFAULT_TAK_PORT) {
    private var serverSocket: ServerSocket? = null
    private var selectorManager: SelectorManager? = null
    private var running = false
    private var serverScope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val connectionsMutex = Mutex()

    private val connections = mutableMapOf<String, TAKClientConnection>()

    var onMessage: ((CoTMessage) -> Unit)? = null

    suspend fun start(scope: CoroutineScope): Result<Unit> = try {
        serverScope = scope
        selectorManager = SelectorManager(dispatchers.default)
        serverSocket = aSocket(selectorManager!!).tcp().bind(hostname = "127.0.0.1", port = port)

        running = true
        acceptJob = scope.launch(dispatchers.io) { acceptLoop() }
        Result.success(Unit)
    } catch (e: Exception) {
        Logger.e(e) { "Failed to bind TAK Server to 127.0.0.1:$port" }
        Result.failure(e)
    }

    private suspend fun acceptLoop() {
        val scope = serverScope ?: return
        while (running && scope.coroutineIsActive) {
            try {
                val clientSocket = serverSocket?.accept()
                if (clientSocket != null) {
                    handleConnection(clientSocket)
                }
                delay(TAK_ACCEPT_LOOP_DELAY_MS)
            } catch (e: Exception) {
                Logger.w(e) { "TAK server accept loop iteration failed" }
                delay(TAK_ACCEPT_LOOP_DELAY_MS)
            }
        }
    }

    private fun handleConnection(clientSocket: Socket) {
        val scope = serverScope ?: return
        val connectionId = Random.nextInt().toString(TAK_HEX_RADIX)
        val endpoint = clientSocket.remoteAddress.toString()
        val clientInfo = TAKClientInfo(id = connectionId, endpoint = endpoint)

        val connection =
            TAKClientConnection(
                socket = clientSocket,
                clientInfo = clientInfo,
                onEvent = { event -> handleConnectionEvent(connectionId, event) },
                scope = scope,
            )

        scope.launch {
            connectionsMutex.withLock { connections[connectionId] = connection }
            connection.start()
        }
    }

    private fun handleConnectionEvent(connectionId: String, event: TAKConnectionEvent) {
        when (event) {
            is TAKConnectionEvent.Message -> {
                onMessage?.invoke(event.cotMessage)
            }
            is TAKConnectionEvent.Disconnected -> {
                serverScope?.launch { connectionsMutex.withLock { connections.remove(connectionId) } }
            }
            else -> {}
        }
    }

    fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null
        serverScope?.launch {
            val toClose = connectionsMutex.withLock {
                val current = connections.values.toList()
                connections.clear()
                current
            }
            toClose.forEach { it.close() }
        }

        serverSocket?.close()
        serverSocket = null

        selectorManager?.close()
        selectorManager = null
        serverScope = null
    }

    suspend fun broadcast(cotMessage: CoTMessage) {
        val currentConnections = connectionsMutex.withLock { connections.values.toList() }
        currentConnections.forEach { connection ->
            try {
                connection.send(cotMessage)
            } catch (e: Exception) {
                Logger.w(e) { "Failed to broadcast CoT to TAK client ${connection.clientInfo.id}" }
                connection.close()
            }
        }
    }

    suspend fun connectionCount(): Int = connectionsMutex.withLock { connections.size }

    suspend fun hasConnections(): Boolean = connectionCount() > 0
}
