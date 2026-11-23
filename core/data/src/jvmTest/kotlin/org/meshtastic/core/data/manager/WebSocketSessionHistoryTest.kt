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

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.TransportState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each end is the error the SDK's WebSocketTransport publishes against a peer that ends the session the way firmware's
 * WebSocket phone API does: close 4001 when another client takes the slot, 1009 for an oversized message, 1013 when out
 * of heap, an ESP32 drop without a close frame, and a refused upgrade (404 where the firmware has no such endpoint).
 */
class WebSocketSessionHistoryTest {
    private fun TestScope.history(url: String = "ws://192.168.1.20/api/v1/ws") = WebSocketSessionHistory(url) {
        currentTime
    }

    private fun WebSocketSessionHistory.session(end: TransportState.Error, lastingMs: Long = 0L, scope: TestScope) =
        run {
            onOpened()
            scope.testScheduler.advanceTimeBy(lastingMs)
            onEnded(end)
        }

    @Test
    fun `five sessions that drop right after opening give the slot up`() = runTest {
        val drop = sessionEndFor(PeerScript.Drop)
        val history = history()
        val ends =
            List(5) {
                history.beforeConnect()
                history.session(drop, lastingMs = 2_000L, scope = this)
            }

        assertEquals(List(4) { SessionEnd(recoverable = true) }, ends.take(4))
        assertEquals(SessionEnd(false, TransportDisconnectReason.WebSocketSlotContested), ends.last())
    }

    @Test
    fun `a session that lasts past 30 s starts the quick drop count again`() = runTest {
        val drop = sessionEndFor(PeerScript.Drop)
        val history = history()
        repeat(4) {
            history.beforeConnect()
            history.session(drop, scope = this)
        }
        history.beforeConnect()
        history.session(drop, lastingMs = 30_000L, scope = this)

        history.beforeConnect()
        assertEquals(SessionEnd(recoverable = true), history.session(drop, scope = this))
    }

    @Test
    fun `a 4001 close means another client took the slot`() = runTest {
        val end = history().session(sessionEndFor(PeerScript.CloseWith(4001)), scope = this)

        assertEquals(SessionEnd(false, TransportDisconnectReason.ReplacedByAnotherClient), end)
    }

    @Test
    fun `a 1009 close means a message was too big`() = runTest {
        val end = history().session(sessionEndFor(PeerScript.CloseWith(1009)), scope = this)

        assertEquals(SessionEnd(false, TransportDisconnectReason.WebSocketMessageTooBig), end)
    }

    @Test
    fun `a 1013 close holds the next connect for 30 s`() = runTest {
        val history = history()
        history.session(sessionEndFor(PeerScript.CloseWith(1013)), scope = this)

        val before = currentTime
        history.beforeConnect()
        assertEquals(30_000L, currentTime - before)
    }

    @Test
    fun `a 1013 close waits only for what is left of the 30 s`() = runTest {
        val history = history()
        history.session(sessionEndFor(PeerScript.CloseWith(1013)), scope = this)
        testScheduler.advanceTimeBy(12_000L)

        val before = currentTime
        history.beforeConnect()
        assertEquals(18_000L, currentTime - before)
    }

    @Test
    fun `a failed dial names its cause`() = runTest {
        suspend fun dial(end: TransportState.Error, url: String = "ws://192.168.1.20/api/v1/ws") = history(url).run {
            beforeConnect()
            onEnded(end).reason
        }

        assertEquals(
            TransportDisconnectReason.WebSocketNotSupported,
            dial(sessionEndFor(PeerScript.RefuseUpgrade(404))),
        )
        assertEquals(
            TransportDisconnectReason.WebSocketHandshakeRejected,
            dial(sessionEndFor(PeerScript.RefuseUpgrade(404), path = "/proxy"), url = "ws://192.168.1.20/proxy"),
        )
        assertEquals(
            TransportDisconnectReason.WebSocketHandshakeRejected,
            dial(sessionEndFor(PeerScript.RefuseUpgrade(403))),
        )
        assertEquals(TransportDisconnectReason.WebSocketTlsFailed, dial(sessionEndFor(PeerScript.PlainTextToTls)))
        val closedPort = ScriptedWebSocketPeer(PeerScript.Drop).use { it.port }
        assertEquals(
            TransportDisconnectReason.WebSocketConnectFailed,
            dial(transportEnd("ws://127.0.0.1:$closedPort/api/v1/ws")),
        )
    }
}
