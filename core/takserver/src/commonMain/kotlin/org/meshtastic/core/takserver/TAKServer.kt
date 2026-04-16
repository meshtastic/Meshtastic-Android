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

/**
 * Platform-agnostic contract for the Meshtastic TAK server.
 *
 * The production implementation on Android / JVM runs a TLS (mTLS) listener on port
 * [DEFAULT_TAK_PORT] (8089) using the bundled server identity. This matches the
 * Meshtastic-Apple (iOS) implementation so that a single exported `.zip` data package
 * is valid for ATAK on Android AND iTAK on iOS without re-configuration.
 *
 * The interface deliberately hides the platform socket / TLS primitives so that
 * `commonMain` code (`TAKServerManagerImpl`, DI, tests) can depend on it without
 * pulling `javax.net.ssl.*` into the common source set.
 */
interface TAKServer {

    /** Observable count of currently-connected TAK clients (ATAK/iTAK). */
    val connectionCount: StateFlow<Int>

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

    /** Broadcast raw CoT XML to every currently-connected client.
     *  Used for mesh-originated messages that should be forwarded verbatim
     *  without re-parsing through the app's CoTXmlParser (which strips
     *  shape detail elements like strokeColor, fillColor, vertices, etc.). */
    suspend fun broadcastRawXml(xml: String)

    /** Returns true if at least one TAK client is currently connected. */
    suspend fun hasConnections(): Boolean
}

/**
 * Platform factory for [TAKServer]. The JVM/Android implementation lives in
 * `jvmAndroidMain` and uses JSSE (`SSLServerSocket`) with the bundled
 * `server.p12` identity and `ca.pem` client trust store.
 */
expect fun createTAKServer(
    dispatchers: CoroutineDispatchers,
    port: Int = DEFAULT_TAK_PORT,
): TAKServer
