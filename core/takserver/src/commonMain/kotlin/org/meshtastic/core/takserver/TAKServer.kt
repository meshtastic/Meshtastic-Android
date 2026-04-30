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
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _connectionCount = MutableStateFlow(0)
    val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    var onMessage: ((CoTMessage) -> Unit)? = null

    suspend fun start(scope: CoroutineScope): Result<Unit> {
        // Double-start guard: prevents SelectorManager / ServerSocket leaks
        if (running) {
            Logger.w { "TAK Server already running on port $port" }
            return Result.success(Unit)
        }

        return try {
            serverScope = scope
            // Close any stale SelectorManager before creating a new one
            selectorManager?.close()
            selectorManager = SelectorManager(dispatchers.default)
            serverSocket = aSocket(selectorManager!!).tcp().bind(hostname = "127.0.0.1", port = port)

            running = true
            acceptJob = scope.launch(dispatchers.io) { acceptLoop() }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bind TAK Server to 127.0.0.1:$port" }
            Result.failure(e)
        }
    }

    private suspend fun acceptLoop() {
        val scope = serverScope ?: return
        while (running && scope.coroutineIsActive) {
            try {
                val clientSocket = serverSocket?.accept()
                if (clientSocket != null) {
                    handleConnection(clientSocket)
                }
                // No delay on the success path — accept() is already suspending
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "TAK server accept loop iteration failed" }
                // Back-off only in the error path
                delay(TAK_ACCEPT_LOOP_DELAY_MS)
            }
        }
    }

    private fun handleConnection(clientSocket: Socket) {
        val scope = serverScope ?: return
        val endpoint = clientSocket.remoteAddress.toString()

        if (!clientSocket.remoteAddress.isLoopback()) {
            Logger.w { "TAK server rejected non-loopback connection from $endpoint" }
            clientSocket.close()
            return
        }

        val connectionId = Random.nextInt().toString(TAK_HEX_RADIX)
        val clientInfo = TAKClientInfo(id = connectionId, endpoint = endpoint)

        val connection =
            TAKClientConnection(
                socket = clientSocket,
                clientInfo = clientInfo,
                onEvent = { event -> handleConnectionEvent(connectionId, event) },
                scope = scope,
            )

        scope.launch {
            connectionsMutex.withLock {
                connections[connectionId] = connection
                _connectionCount.value = connections.size
            }
            connection.start()
        }
    }

    private fun handleConnectionEvent(connectionId: String, event: TAKConnectionEvent) {
        when (event) {
            is TAKConnectionEvent.Message -> {
                onMessage?.invoke(event.cotMessage)
            }

            is TAKConnectionEvent.Disconnected -> {
                serverScope?.launch {
                    connectionsMutex.withLock {
                        connections.remove(connectionId)
                        _connectionCount.value = connections.size
                    }
                }
            }

            is TAKConnectionEvent.Error -> {
                Logger.w(event.error) { "TAK client connection error: $connectionId" }
                serverScope?.launch {
                    connectionsMutex.withLock {
                        connections.remove(connectionId)
                        _connectionCount.value = connections.size
                    }
                }
            }

            is TAKConnectionEvent.Connected -> {
                /* no-op: logged by TAKClientConnection.start() */
            }

            is TAKConnectionEvent.ClientInfoUpdated -> {
                /* no-op: TAKClientConnection tracks updated info locally */
            }
        }
    }

    fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null

        // Close connections synchronously — TAKClientConnection.close() is non-suspending,
        // so we don't need to launch into the (possibly-cancelled) serverScope.
        val toClose: List<TAKClientConnection>
        // We can't use Mutex.withLock here (non-suspending context) so we swap & clear under a
        // best-effort copy — worst case a connection added concurrently is closed by socket teardown.
        toClose = connections.values.toList()
        connections.clear()
        _connectionCount.value = 0
        toClose.forEach { it.close() }

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

    suspend fun hasConnections(): Boolean = connectionsMutex.withLock { connections.isNotEmpty() }
}

/**
 * Returns true if this [SocketAddress] represents a loopback address (IPv4 127.x.x.x or IPv6 ::1).
 *
 * Ktor's [SocketAddress.toString] returns strings like "/127.0.0.1:4242" (JVM) or "127.0.0.1:4242" on other platforms,
 * so we strip any leading slash and check prefixes without parsing the host. This keeps the check in commonMain without
 * an expect/actual.
 */
private fun SocketAddress.isLoopback(): Boolean {
    val addr = toString().removePrefix("/")
    return addr.startsWith("127.") || addr.startsWith("::1") || addr.startsWith("[::1]")
}
