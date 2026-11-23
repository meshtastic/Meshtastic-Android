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
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.TransportState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Expected outcomes are main's WebSocketRadioTransport: 4001 and 1009 stop for good, five quick drops without a close
 * frame mean a contested slot, 1013 waits 30 s, and a failed dial names its cause. Messages are the SDK's
 * WebSocketTransport text for each case.
 */
class WebSocketSessionHistoryTest {
    private fun TestScope.history() = WebSocketSessionHistory { currentTime }

    private fun error(message: String, recoverable: Boolean = true) =
        TransportState.Error(MeshtasticException.Transport(message), recoverable)

    @Test
    fun `five sessions that drop right after opening give the slot up`() = runTest {
        val history = history()
        val ends =
            List(5) {
                history.beforeConnect()
                history.onOpened()
                testScheduler.advanceTimeBy(2_000L)
                history.onEnded(error("WebSocket dropped without a close frame"))
            }

        assertEquals(List(4) { SessionEnd(recoverable = true) }, ends.take(4))
        assertEquals(SessionEnd(false, TransportDisconnectReason.WebSocketSlotContested), ends.last())
    }

    @Test
    fun `a session that lasts past 30 s starts the quick drop count again`() = runTest {
        val history = history()
        repeat(4) {
            history.beforeConnect()
            history.onOpened()
            history.onEnded(error("WebSocket dropped without a close frame"))
        }
        history.beforeConnect()
        history.onOpened()
        testScheduler.advanceTimeBy(30_000L)
        history.onEnded(error("WebSocket dropped without a close frame"))

        history.beforeConnect()
        history.onOpened()
        assertEquals(SessionEnd(recoverable = true), history.onEnded(error("WebSocket dropped without a close frame")))
    }

    @Test
    fun `a 4001 close means another client took the slot`() = runTest {
        val end =
            history().run {
                onOpened()
                onEnded(error("WebSocket closed: another client took the node's only WebSocket slot (4001)", false))
            }

        assertEquals(SessionEnd(false, TransportDisconnectReason.ReplacedByAnotherClient), end)
    }

    @Test
    fun `a 1013 close holds the next connect for 30 s`() = runTest {
        val history = history()
        history.onOpened()
        history.onEnded(error("WebSocket closed: the node is out of memory, try again later (1013)"))

        val before = currentTime
        history.beforeConnect()
        assertEquals(30_000L, currentTime - before)
    }

    @Test
    fun `a 1013 close waits only for what is left of the 30 s`() = runTest {
        val history = history()
        history.onOpened()
        history.onEnded(error("WebSocket closed: the node is out of memory, try again later (1013)"))
        testScheduler.advanceTimeBy(12_000L)

        val before = currentTime
        history.beforeConnect()
        assertEquals(18_000L, currentTime - before)
    }

    @Test
    fun `a failed dial names its cause`() = runTest {
        val history = history()
        suspend fun dial(message: String) = history.run {
            beforeConnect()
            onEnded(error(message)).reason
        }

        assertEquals(
            TransportDisconnectReason.WebSocketNotSupported,
            dial(
                "WebSocket upgrade refused by 192.168.1.20:80 with HTTP 404; " +
                    "the node's firmware may not support the WebSocket phone API at /api/v1/ws",
            ),
        )
        assertEquals(
            TransportDisconnectReason.WebSocketHandshakeRejected,
            dial(
                "WebSocket upgrade refused by 192.168.1.20:80 with HTTP 403; " +
                    "the node's firmware may not support the WebSocket phone API at /api/v1/ws",
            ),
        )
        assertEquals(
            TransportDisconnectReason.WebSocketTlsFailed,
            dial("TLS certificate for 192.168.1.20:443 is not trusted; try ws:// or meshtasticd's plain HTTPPort"),
        )
        assertEquals(
            TransportDisconnectReason.WebSocketConnectFailed,
            dial("could not reach 192.168.1.20:80: Connection refused"),
        )
    }
}
