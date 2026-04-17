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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.network.transport.TcpTransport
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.concurrent.Volatile

/**
 * TCP radio transport — thin adapter over the shared [TcpTransport] from `core:network`.
 *
 * Implements [RadioTransport] directly via composition over [TcpTransport], delegating send/receive to the transport
 * and calling [RadioTransportCallback] for lifecycle events. This avoids the previous inheritance from
 * [StreamTransport] which created a dead [StreamFrameCodec] and required overriding `sendBytes` as a no-op.
 */
open class TcpRadioTransport(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val address: String,
) : RadioTransport {

    companion object {
        const val SERVICE_PORT = StreamFrameCodec.DEFAULT_TCP_PORT
    }

    /** Guards against a double [RadioTransportCallback.onDisconnect] when [close] triggers [TcpTransport.stop]. */
    @Volatile private var closing = false

    private val transport =
        TcpTransport(
            dispatchers = dispatchers,
            scope = scope,
            listener =
            object : TcpTransport.Listener {
                override fun onConnected() {
                    callback.onConnect()
                }

                override fun onDisconnected() {
                    if (closing) return // close() will fire the permanent disconnect itself
                    // TCP disconnects are transient (not permanent) — the transport will auto-reconnect.
                    callback.onDisconnect(isPermanent = false)
                }

                override fun onPacketReceived(bytes: ByteArray) {
                    callback.handleFromRadio(bytes)
                }
            },
            logTag = "TcpRadioTransport[$address]",
        )

    override fun start() {
        transport.start(address)
    }

    override suspend fun close() {
        Logger.d { "[$address] Closing TCP transport" }
        closing = true
        transport.stop()
        callback.onDisconnect(isPermanent = true)
    }

    override fun keepAlive() {
        Logger.d { "[$address] TCP keepAlive" }
        scope.handledLaunch { transport.sendHeartbeat() }
    }

    override fun handleSendToRadio(p: ByteArray) {
        scope.handledLaunch { transport.sendPacket(p) }
    }
}
