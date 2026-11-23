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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.testing.FakeLockdownCoordinator
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.sdk.ConnectionState.DeviceSleeping
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/** A WebSocket session the app opens through [SdkPhoneApiSessions] against a peer scripted as firmware. */
class SdkWebSocketSessionTest {

    @Test
    fun `a radio that gives its WebSocket to another client ends the session for good with that reason`() =
        runBlocking {
            // Firmware's WS_CLOSE_REPLACED.
            assertEquals(false to TransportDisconnectReason.ReplacedByAnotherClient, endOf(PeerScript.CloseWith(4001)))
        }

    @Test
    fun `a radio without the WebSocket phone API is named as such`() = runBlocking {
        assertEquals(true to TransportDisconnectReason.WebSocketNotSupported, endOf(PeerScript.RefuseUpgrade(404)))
    }

    /** The `recoverable` flag and reason the app session reports when [script] ends it. */
    private suspend fun endOf(script: PeerScript): Pair<Boolean, TransportDisconnectReason?> =
        ScriptedWebSocketPeer(script).use { peer ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val ended = CompletableDeferred<Pair<Boolean, TransportDisconnectReason?>>()
            val fake =
                FakeRadioInterfaceService(scope).apply {
                    setDeviceAddress("tcp:sdk")
                    connect()
                }
            val radio =
                object : RadioInterfaceService by fake {
                    override fun onPhoneApiSessionEnded(
                        recoverable: Boolean,
                        errorMessage: String?,
                        handshakeTimedOut: Boolean,
                        reason: TransportDisconnectReason?,
                        receiveOverflowed: Boolean,
                        deviceSleeping: DeviceSleeping?,
                    ) {
                        ended.complete(recoverable to reason)
                    }
                }
            val nodeManager =
                NodeManagerImpl(FakeNodeRepository(), mock(MockMode.autofill), radio, scope.asServiceScope())
            val sessions =
                SdkPhoneApiSessions(
                    lazy { radioSessionProjection(nodeManager, radio, scope) },
                    lazy { mock(MockMode.autofill) },
                    lazy { FakeLockdownCoordinator() },
                )
            val transport = sessions.webSocket("ws://127.0.0.1:${peer.port}/api/v1/ws", radio)
            try {
                transport.start()
                withTimeout(10.seconds) { ended.await() }
            } finally {
                transport.close()
                scope.cancel()
            }
        }
}
