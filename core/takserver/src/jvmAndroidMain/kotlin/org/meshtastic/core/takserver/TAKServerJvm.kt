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
import kotlinx.coroutines.withContext
import org.meshtastic.core.di.CoroutineDispatchers
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import javax.net.ssl.SSLServerSocket
import kotlin.random.Random
import kotlinx.coroutines.isActive as coroutineIsActive

/**
 * JSSE-backed TLS TAK server. Matches the Meshtastic-Apple (iOS) implementation:
 *
 *  - Binds `127.0.0.1:8089` (loopback only — no remote device can reach the server)
 *  - TLS 1.2+ with the bundled server.p12 identity
 *  - Mutual TLS: clients MUST present a certificate chaining to the bundled ca.pem
 *  - `SO_REUSEADDR` on the listen socket so an app restart doesn't hit
 *    `BindException: Address already in use` while the previous socket is in
 *    `TIME_WAIT`
 *  - Per-connection [TAKClientConnection] running on [CoroutineDispatchers.io]
 *
 * If the bundled certificates fail to load (e.g. packaging regression), the server
 * refuses to start rather than silently falling back to plain TCP — that failure mode
 * would produce exactly the symptom the user was debugging ("ATAK never connects").
 */
internal class TAKServerJvm(
    private val dispatchers: CoroutineDispatchers,
    private val port: Int = DEFAULT_TAK_PORT,
) : TAKServer {

    private var serverSocket: ServerSocket? = null
    private var running = false
    private var serverScope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val connectionsMutex = Mutex()
    private val connections = mutableMapOf<String, TAKClientConnection>()

    private val _connectionCount = MutableStateFlow(0)
    override val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    override var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)? = null
    override var onClientConnected: (() -> Unit)? = null

    override suspend fun start(scope: CoroutineScope): Result<Unit> {
        if (running) {
            Logger.w { "TAK Server already running on port $port" }
            return Result.success(Unit)
        }

        val sslContext = TakCertLoader.getServerSslContext()
            ?: return Result.failure(
                IllegalStateException(
                    "TAK Server: bundled TLS certificates could not be loaded; refusing to start",
                )
            )

        return try {
            serverScope = scope

            // Bind on the IO dispatcher — bind() can briefly block.
            val boundSocket = withContext(dispatchers.io) {
                val factory = sslContext.serverSocketFactory
                // Use the address-specific overload so we bind to loopback only.
                val loopback = InetAddress.getByName("127.0.0.1")
                // backlog of 4 is plenty for local TAK clients
                val tls = factory.createServerSocket(port, 4, loopback) as SSLServerSocket
                configureTlsServerSocket(tls)
                tls
            }
            serverSocket = boundSocket
            running = true
            Logger.i { "TAK Server listening on 127.0.0.1:$port (TLS, mTLS enforced)" }

            acceptJob = scope.launch(dispatchers.io) { acceptLoop() }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to bind TAK Server to 127.0.0.1:$port" }
            running = false
            serverSocket?.runCatching { close() }
            serverSocket = null
            Result.failure(e)
        }
    }

    private fun configureTlsServerSocket(tls: SSLServerSocket) {
        // Minimum TLS 1.2 — matches iOS.
        val protocols = tls.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
        if (protocols.isNotEmpty()) {
            tls.enabledProtocols = protocols.toTypedArray()
        }
        // Require client certificate (mTLS) — matches
        // `sec_protocol_options_set_peer_authentication_required` on iOS.
        tls.needClientAuth = true
        // Enable address reuse so restart doesn't hit TIME_WAIT on the port.
        tls.reuseAddress = true
    }

    private suspend fun acceptLoop() {
        val scope = serverScope ?: return
        while (running && scope.coroutineIsActive) {
            try {
                val clientSocket = withContext(dispatchers.io) {
                    serverSocket?.accept()
                }
                if (clientSocket != null) {
                    handleConnection(clientSocket)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Bind was lost or the socket was closed under us — back off, then retry.
                if (running) {
                    Logger.w(e) { "TAK server accept loop iteration failed: ${e.message}" }
                }
                delay(TAK_ACCEPT_LOOP_DELAY_MS)
            }
        }
    }

    private fun handleConnection(clientSocket: Socket) {
        val scope = serverScope ?: return
        val endpoint = clientSocket.remoteSocketAddress?.toString() ?: "unknown"

        if (clientSocket.inetAddress?.isLoopbackAddress != true) {
            Logger.w { "TAK server rejected non-loopback connection from $endpoint" }
            runCatching { clientSocket.close() }
            return
        }

        val connectionId = Random.nextInt().toString(TAK_HEX_RADIX)
        val clientInfo = TAKClientInfo(id = connectionId, endpoint = endpoint)
        Logger.i { "TAK client connected: id=$connectionId endpoint=$endpoint" }

        val connection =
            TAKClientConnection(
                socket = clientSocket,
                clientInfo = clientInfo,
                onEvent = { event -> handleConnectionEvent(connectionId, event) },
                scope = scope,
                ioDispatcher = dispatchers.io,
            )

        // Launch on IO so socket reads/writes don't queue behind CPU work on Default
        scope.launch(dispatchers.io) {
            connectionsMutex.withLock {
                connections[connectionId] = connection
                _connectionCount.value = connections.size
                Logger.i { "TAK connection count now ${connections.size}" }
            }
            connection.start()
        }
    }

    private fun handleConnectionEvent(connectionId: String, event: TAKConnectionEvent) {
        when (event) {
            is TAKConnectionEvent.Message -> {
                onMessage?.invoke(event.cotMessage, event.clientInfo)
            }
            is TAKConnectionEvent.Disconnected -> {
                Logger.i { "TAK client disconnected: id=$connectionId" }
                serverScope?.launch(dispatchers.io) {
                    connectionsMutex.withLock {
                        connections.remove(connectionId)
                        _connectionCount.value = connections.size
                        Logger.i { "TAK connection count now ${connections.size}" }
                    }
                }
            }
            is TAKConnectionEvent.Error -> {
                Logger.w(event.error) { "TAK client connection error: $connectionId" }
                serverScope?.launch(dispatchers.io) {
                    connectionsMutex.withLock {
                        connections.remove(connectionId)
                        _connectionCount.value = connections.size
                        Logger.i { "TAK connection count now ${connections.size}" }
                    }
                }
            }
            is TAKConnectionEvent.Connected -> {
                onClientConnected?.invoke()
            }
            is TAKConnectionEvent.ClientInfoUpdated -> {
                /* no-op: TAKClientConnection tracks updated info locally */
            }
        }
    }

    override fun stop() {
        running = false
        acceptJob?.cancel()
        acceptJob = null

        val toClose: List<TAKClientConnection>
        // Non-suspending stop path — best-effort copy; any connection added concurrently
        // will get closed when its socket is torn down by accept() returning null.
        toClose = connections.values.toList()
        connections.clear()
        _connectionCount.value = 0
        toClose.forEach { it.close() }

        serverSocket?.runCatching { close() }
        serverSocket = null
        serverScope = null
        Logger.i { "TAK Server stopped" }
    }

    override suspend fun broadcast(cotMessage: CoTMessage) {
        val currentConnections = connectionsMutex.withLock { connections.values.toList() }
        if (currentConnections.isEmpty()) {
            Logger.d { "broadcast ${cotMessage.type}: no TAK clients connected, dropping" }
            return
        }
        Logger.d { "broadcast ${cotMessage.type} to ${currentConnections.size} TAK client(s)" }
        currentConnections.forEach { connection ->
            try {
                connection.send(cotMessage)
            } catch (e: Exception) {
                Logger.w(e) { "Failed to broadcast CoT to TAK client ${connection.clientInfo.id}" }
                connection.close()
            }
        }
    }

    override suspend fun broadcastRawXml(xml: String) {
        val currentConnections = connectionsMutex.withLock { connections.values.toList() }
        if (currentConnections.isEmpty()) return
        Logger.d { "broadcastRawXml to ${currentConnections.size} TAK client(s)" }
        currentConnections.forEach { connection ->
            try {
                connection.sendRawXml(xml)
            } catch (e: Exception) {
                Logger.w(e) { "Failed to broadcast raw XML to TAK client ${connection.clientInfo.id}" }
                connection.close()
            }
        }
    }

    override suspend fun hasConnections(): Boolean =
        connectionsMutex.withLock { connections.isNotEmpty() }
}

/**
 * `actual` factory for the KMP `expect fun createTAKServer` declared in `commonMain`.
 * Both the Desktop JVM target and the Android target share this source set, so both
 * run the same JSSE-based TLS listener.
 *
 * Also wires [TAKDataPackageGenerator]'s bundled-cert provider so that the exported
 * `.zip` data package contains the real `server.p12` / `client.p12` bytes from the
 * classpath rather than an empty fallback.
 */
actual fun createTAKServer(dispatchers: CoroutineDispatchers, port: Int): TAKServer {
    TAKDataPackageGenerator.bundledCertBytesProvider = TakCertBundledBytesProvider
    return TAKServerJvm(dispatchers = dispatchers, port = port)
}

/** Bridges [TakCertLoader] bytes into [TAKDataPackageGenerator] via the commonMain interface. */
private object TakCertBundledBytesProvider : BundledCertBytesProvider {
    override fun serverP12Bytes(): ByteArray? = TakCertLoader.getServerP12Bytes()
    override fun clientP12Bytes(): ByteArray? = TakCertLoader.getClientP12Bytes()
}
