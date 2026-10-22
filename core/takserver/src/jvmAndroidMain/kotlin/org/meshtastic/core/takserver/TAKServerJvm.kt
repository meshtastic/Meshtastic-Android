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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.TakCertificateStore
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.locks.ReentrantLock
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.Volatile
import kotlin.concurrent.withLock
import kotlin.random.Random
import kotlinx.coroutines.isActive as coroutineIsActive

/** Listen-socket backlog; a handful of pending connections is plenty for local TAK clients. */
private const val SERVER_SOCKET_BACKLOG = 4

/**
 * Upper bound on the TLS handshake. A local process can open the port and send nothing; without this the handshake
 * would pin an IO thread indefinitely, and a handful of such sockets would starve the app-wide IO dispatcher.
 */
private const val TLS_HANDSHAKE_TIMEOUT_MS = 10_000

/**
 * JSSE-backed TLS TAK server:
 * - Binds `127.0.0.1:8089` (loopback only — no remote device can reach the server)
 * - TLS 1.2+ with this install's server identity from [TakCertificateManager]
 * - Mutual TLS: clients MUST present the one client certificate this install issued
 * - `SO_REUSEADDR` on the listen socket so an app restart doesn't hit `BindException: Address already in use` while the
 *   previous socket is in `TIME_WAIT`
 * - Per-connection [TAKClientConnection] running on [CoroutineDispatchers.io]
 *
 * If the certificates cannot be generated or loaded, the server refuses to start rather than silently falling back to
 * plain TCP, since that failure mode would produce exactly the symptom the user was debugging ("ATAK never connects").
 */
internal class TAKServerJvm(
    private val dispatchers: CoroutineDispatchers,
    private val certificateManager: TakCertificateManager,
    private val port: Int = DEFAULT_TAK_PORT,
) : TAKServer {

    private var serverSocket: ServerSocket? = null
    private val restartMutex = Mutex()

    @Volatile private var running = false
    private var serverScope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val connectionsLock = ReentrantLock()
    private val connections = mutableMapOf<String, TAKClientConnection>()

    /**
     * Sockets accepted but not yet past [verifyIssuedClient]; closed by [stop] so a stalled handshake cannot outlive
     * it.
     */
    private val pendingSockets = mutableSetOf<Socket>()

    private val _connectionCount = MutableStateFlow(0)
    override val connectionCount: StateFlow<Int> = _connectionCount.asStateFlow()

    private val _rejectedConnectionCount = MutableStateFlow(0)
    override val rejectedConnectionCount: StateFlow<Int> = _rejectedConnectionCount.asStateFlow()

    /** The port the listener is bound to, or null when not running. Lets tests bind port 0 and discover the result. */
    internal val boundPort: Int?
        get() = serverSocket?.localPort?.takeIf { it > 0 }

    override var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)? = null
    override var onClientConnected: (() -> Unit)? = null

    override suspend fun start(scope: CoroutineScope): Result<Unit> {
        if (running) {
            Logger.w { "TAK Server already running on port $port" }
            return Result.success(Unit)
        }

        return try {
            // Key generation on first start can take a few seconds (three RSA-2048 keys); keep it off Main.
            val sslContext = withContext(dispatchers.io) { certificateManager.getOrCreate().sslContext }
            serverScope = scope

            // Bind on the IO dispatcher — bind() can briefly block.
            val boundSocket =
                withContext(dispatchers.io) {
                    val factory = sslContext.serverSocketFactory
                    // Use the address-specific overload so we bind to loopback only.
                    val loopback = InetAddress.getByName("127.0.0.1")
                    val tls = factory.createServerSocket(port, SERVER_SOCKET_BACKLOG, loopback) as SSLServerSocket
                    configureTlsServerSocket(tls)
                    tls
                }
            serverSocket = boundSocket
            running = true
            _rejectedConnectionCount.value = 0
            Logger.i { "TAK Server listening on 127.0.0.1:${boundSocket.localPort} (TLS, mTLS enforced)" }

            acceptJob = scope.launch(dispatchers.io) { acceptLoop() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Failed to start TAK Server on 127.0.0.1:$port" }
            running = false
            try {
                serverSocket?.close()
            } catch (_: Exception) {}
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
        // Require a client certificate (mTLS). The trust manager only accepts chains to this install's CA;
        // verifyIssuedClient() then pins the leaf itself.
        tls.needClientAuth = true
        // Enable address reuse so restart doesn't hit TIME_WAIT on the port.
        tls.reuseAddress = true
    }

    private suspend fun acceptLoop() {
        val scope = serverScope ?: return
        while (running && scope.coroutineIsActive) {
            try {
                val clientSocket = withContext(dispatchers.io) { serverSocket?.accept() }
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
            try {
                clientSocket.close()
            } catch (_: Exception) {}
            return
        }

        val connectionId = Random.nextInt().toString(TAK_HEX_RADIX)
        val clientInfo = TAKClientInfo(id = connectionId, endpoint = endpoint)
        Logger.i { "TAK client connecting: id=$connectionId endpoint=$endpoint" }

        val connection =
            TAKClientConnection(
                socket = clientSocket,
                clientInfo = clientInfo,
                onEvent = { event -> handleConnectionEvent(connectionId, event) },
                scope = scope,
                ioDispatcher = dispatchers.io,
            )

        connectionsLock.withLock { pendingSockets += clientSocket }
        // Launch on IO so socket reads/writes don't queue behind CPU work on Default
        scope.launch(dispatchers.io) {
            // Completing the handshake here (not on the accept loop) keeps a slow or hostile client from
            // stalling accepts, and lets us reject anything but our own issued client certificate.
            val accepted = verifyIssuedClient(clientSocket, endpoint)
            connectionsLock.withLock { pendingSockets -= clientSocket }
            if (!accepted) return@launch
            connectionsLock.withLock {
                connections[connectionId] = connection
                _connectionCount.value = connections.size
                // An issued client getting in means the current package is in use, so the re-import notice is stale.
                _rejectedConnectionCount.value = 0
                Logger.i { "TAK client accepted: id=$connectionId, connection count now ${connections.size}" }
            }
            connection.start()
        }
    }

    /**
     * Completes the TLS handshake under [TLS_HANDSHAKE_TIMEOUT_MS] and accepts the socket only if the peer presented
     * exactly the client certificate this install issued. Anything else (no certificate, one from an earlier build,
     * another install's identity, our own server certificate) is counted in [rejectedConnectionCount] and closed.
     */
    private fun verifyIssuedClient(clientSocket: Socket, endpoint: String): Boolean {
        val accepted =
            try {
                val tls = clientSocket as SSLSocket
                tls.soTimeout = TLS_HANDSHAKE_TIMEOUT_MS
                tls.startHandshake()
                val issued = certificateManager.isIssuedClient(tls.session.peerCertificates.firstOrNull())
                // TAKClientConnection reads block indefinitely between keepalives; restore the default.
                if (issued) tls.soTimeout = 0
                issued
            } catch (e: Exception) {
                Logger.w { "TAK server TLS handshake failed for $endpoint: ${e.message}" }
                false
            }
        if (!accepted) {
            Logger.w { "TAK server rejected $endpoint: client certificate is not the one issued by this install" }
            if (running) _rejectedConnectionCount.update { it + 1 }
            try {
                clientSocket.close()
            } catch (_: Exception) {}
        }
        return accepted
    }

    override val certificates: TakCertificateProvider =
        object : TakCertificateProvider {
            override suspend fun dataPackageCredentials(): TakDataPackageCredentials? = try {
                withContext(dispatchers.io) {
                    val material = certificateManager.getOrCreate().material
                    TakDataPackageCredentials(
                        clientP12 = material.clientP12,
                        truststoreP12 = material.truststoreP12,
                        password = material.password,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "TAK: could not load certificates for the data package" }
                null
            }

            override suspend fun regenerate(): Result<Unit> = restartMutex.withLock {
                try {
                    withContext(dispatchers.io) { certificateManager.regenerate() }
                    // A running listener still holds the old SSLContext (and old client sessions): rebind on the
                    // same scope so the new identity takes effect immediately.
                    val scope = serverScope
                    if (running && scope != null) {
                        stop()
                        start(scope).getOrThrow()
                    }
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "TAK: certificate regeneration failed" }
                    Result.failure(e)
                }
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
                    connectionsLock.withLock {
                        connections.remove(connectionId)
                        _connectionCount.value = connections.size
                        Logger.i { "TAK connection count now ${connections.size}" }
                    }
                }
            }

            is TAKConnectionEvent.Error -> {
                Logger.w(event.error) { "TAK client connection error: $connectionId" }
                serverScope?.launch(dispatchers.io) {
                    connectionsLock.withLock {
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

        // Guard the snapshot+clear with the same lock used by the coroutine accept/disconnect
        // paths to avoid concurrent modification or a stale connectionCount during shutdown.
        val (toClose, pending) =
            connectionsLock.withLock {
                val snapshot = connections.values.toList()
                connections.clear()
                _connectionCount.value = 0
                val stillHandshaking = pendingSockets.toList()
                pendingSockets.clear()
                snapshot to stillHandshaking
            }
        toClose.forEach { it.close() }
        pending.forEach {
            try {
                it.close()
            } catch (_: Exception) {}
        }

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverScope = null
        Logger.i { "TAK Server stopped" }
    }

    override suspend fun broadcast(cotMessage: CoTMessage) {
        val currentConnections = connectionsLock.withLock { connections.values.toList() }
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
        val currentConnections = connectionsLock.withLock { connections.values.toList() }
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

    override suspend fun hasConnections(): Boolean = connectionsLock.withLock { connections.isNotEmpty() }
}

/**
 * `actual` factory for the KMP `expect fun createTAKServer` declared in `commonMain`. Both the Desktop JVM target and
 * the Android target share this source set, so both run the same JSSE-based TLS listener backed by the per-install PKI
 * kept in [certificateStore].
 */
actual fun createTAKServer(
    dispatchers: CoroutineDispatchers,
    certificateStore: TakCertificateStore,
    port: Int,
): TAKServer =
    TAKServerJvm(dispatchers = dispatchers, certificateManager = TakCertificateManager(certificateStore), port = port)
