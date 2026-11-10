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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.TakCertificateStore

/**
 * Platform-agnostic contract for the Meshtastic TAK server.
 *
 * The production implementation on Android / JVM runs a TLS (mTLS) listener on port [DEFAULT_TAK_PORT] (8089) using a
 * per-install server identity issued by a private CA on first start (see [TakCertificateProvider]). The exported `.zip`
 * data package carries that install's client identity and CA, so it is only valid against this install.
 *
 * The interface deliberately hides the platform socket / TLS primitives so that `commonMain` code
 * (`TAKServerManagerImpl`, DI, tests) can depend on it without pulling `javax.net.ssl.*` into the common source set.
 */
interface TAKServer {

    /** Whether this platform provides a local TAK listener. */
    val isSupported: Boolean
        get() = true

    /** The per-install certificates this listener authenticates with. */
    val certificates: TakCertificateProvider

    /** Observable count of currently-connected TAK clients (ATAK/iTAK). */
    val connectionCount: StateFlow<Int>

    /**
     * Observable count of connections rejected since the listener started because the client did not present this
     * install's certificate. Surfaces the "ATAK still has the old data package" migration state in the UI.
     */
    val rejectedConnectionCount: StateFlow<Int>

    /** Callback invoked on the IO dispatcher for every inbound CoT message from a client. */
    var onMessage: ((CoTMessage, TAKClientInfo?) -> Unit)?

    /** Callback invoked when a TAK client connects. Use to drain queued messages. */
    var onClientConnected: (() -> Unit)?

    /** Bind the listener and begin accepting connections. Idempotent if already running. */
    suspend fun start(scope: CoroutineScope): Result<Unit>

    /** Stop the listener, close all client sockets, and release OS resources. */
    fun stop()

    /** Broadcast a CoT message to every currently-connected client. */
    suspend fun broadcast(cotMessage: CoTMessage)

    /**
     * Broadcast raw CoT XML to every currently-connected client. Used for mesh-originated messages that should be
     * forwarded verbatim without re-parsing through the app's CoTXmlParser (which strips shape detail elements like
     * strokeColor, fillColor, vertices, etc.).
     */
    suspend fun broadcastRawXml(xml: String)

    /** Returns true if at least one TAK client is currently connected. */
    suspend fun hasConnections(): Boolean
}

/**
 * Platform factory for [TAKServer]. The JVM/Android implementation lives in `jvmAndroidMain` and uses JSSE
 * (`SSLServerSocket`) with the per-install identity kept in [certificateStore].
 */
expect fun createTAKServer(
    dispatchers: CoroutineDispatchers,
    certificateStore: TakCertificateStore,
    port: Int = DEFAULT_TAK_PORT,
): TAKServer
