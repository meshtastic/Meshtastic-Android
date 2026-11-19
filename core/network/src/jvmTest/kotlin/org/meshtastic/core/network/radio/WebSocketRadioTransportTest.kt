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
package org.meshtastic.core.network.radio

import io.ktor.client.plugins.websocket.WebSocketException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.network.transport.ReconnectBackoff
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import java.io.InputStream
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * [WebSocketRadioTransport] against a minimal server speaking firmware's `/api/v1/ws` shape: one binary message per
 * `ToRadio` or `FromRadio`, client frames masked as RFC 6455 requires. Every wait blocks on the peer's next action,
 * with a timeout only as a failure bound.
 */
class WebSocketRadioTransportTest {
    private val server = ServerSocket(0).apply { soTimeout = ACCEPT_TIMEOUT_MS }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callback = RecordingCallback()
    private val transports = mutableListOf<WebSocketRadioTransport>()
    private val sockets = mutableListOf<Socket>()
    private val url = "ws://127.0.0.1:${server.localPort}/api/v1/ws"

    @AfterTest
    fun tearDown() {
        runBlocking { transports.forEach { it.close() } }
        scope.cancel()
        sockets.forEach { it.close() }
        server.close()
    }

    @Test
    fun `bytes go out as one binary message and a binary message comes back as one FromRadio`() = runBlocking {
        val transport = transport()
        val peer = accept().apply { handshake() }
        assertEquals(Event.Connected, nextEvent())

        val toRadio = byteArrayOf(0x18, 0x2A)
        assertTrue(transport.handleSendToRadio(toRadio))
        assertContentEquals(toRadio, io { peer.readFrame() }?.payload)

        val reply = byteArrayOf(0x0A, 0x02, 0x08, 0x01)
        io { peer.sendText("ignored") }
        io { peer.sendBinary(reply) }
        assertContentEquals(reply, withTimeout(TIMEOUT) { callback.fromRadio.receive() })
        assertTrue(callback.fromRadio.tryReceive().isFailure, "a text frame is dropped, not delivered")

        io {
            peer.sendClose(NORMAL)
            peer.awaitClientClose()
        }
        assertEquals(Event.Disconnected(isPermanent = false, reason = null), nextEvent())
    }

    @Test
    fun `a send before the socket is open is refused`() = runBlocking {
        val transport = transport()
        // The client has dialled, but the upgrade is not answered yet.
        val peer = accept()
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))

        io { peer.handshake() }
        assertEquals(Event.Connected, nextEvent())
        assertTrue(transport.handleSendToRadio(byteArrayOf(1)))
    }

    @Test
    fun `a ToRadio over firmware's limit is refused`() = runBlocking {
        val transport = transport()
        accept().handshake()
        assertEquals(Event.Connected, nextEvent())

        assertFalse(transport.handleSendToRadio(ByteArray(WebSocketRadioTransport.MAX_MESSAGE_BYTES + 1)))
        assertTrue(transport.handleSendToRadio(ByteArray(WebSocketRadioTransport.MAX_MESSAGE_BYTES)))
    }

    @Test
    fun `a dropped socket reconnects`() = runBlocking {
        transport()
        val first = accept().apply { handshake() }
        assertEquals(Event.Connected, nextEvent())

        first.socket.close()
        assertEquals(Event.Disconnected(isPermanent = false, reason = null), nextEvent())

        accept().handshake()
        assertEquals(Event.Connected, nextEvent())
    }

    @Test
    fun `a failed first connect is reported and retried`() = runBlocking {
        transport()
        // Hang up before answering the upgrade.
        accept().socket.close()
        assertEquals(
            Event.Disconnected(isPermanent = false, reason = TransportDisconnectReason.WebSocketConnectFailed),
            nextEvent(),
        )

        accept().handshake()
        assertEquals(Event.Connected, nextEvent())
    }

    @Test
    fun `a 404 for the upgrade says the radio refused the WebSocket`() = runBlocking {
        transport()
        val peer = accept()
        io { peer.reject("404 Not Found") }

        assertEquals(
            Event.Disconnected(isPermanent = false, reason = TransportDisconnectReason.WebSocketHandshakeRejected),
            nextEvent(),
        )
    }

    @Test
    fun `a 4001 close from a radio that gave the slot to another client stops the reconnects`() = runBlocking {
        transport()
        val peer = accept().apply { handshake() }
        assertEquals(Event.Connected, nextEvent())

        io {
            peer.sendClose(WebSocketRadioTransport.CLOSE_REPLACED.toInt())
            peer.awaitClientClose()
        }

        assertEquals(
            Event.Disconnected(isPermanent = true, reason = TransportDisconnectReason.ReplacedByAnotherClient),
            nextEvent(),
        )
        assertNoDial()
    }

    @Test
    fun `a 1009 close is permanent because a retry would replay the message`() = runBlocking {
        transport()
        val peer = accept().apply { handshake() }
        assertEquals(Event.Connected, nextEvent())

        io {
            peer.sendClose(TOO_BIG)
            peer.awaitClientClose()
        }

        assertEquals(
            Event.Disconnected(isPermanent = true, reason = TransportDisconnectReason.WebSocketMessageTooBig),
            nextEvent(),
        )
        assertNoDial()
    }

    @Test
    fun `a 1013 close waits out the try-again-later delay before redialling`() = runBlocking {
        val holdOff = 700.milliseconds
        transport(timing = FAST.copy(tryAgainLaterDelay = holdOff))
        val peer = accept().apply { handshake() }
        assertEquals(Event.Connected, nextEvent())

        val closedAt = TimeSource.Monotonic.markNow()
        io {
            peer.sendClose(TRY_AGAIN_LATER)
            peer.awaitClientClose()
        }
        assertEquals(Event.Disconnected(isPermanent = false, reason = null), nextEvent())

        accept().handshake()
        assertTrue(closedAt.elapsedNow() >= holdOff, "redialled after ${closedAt.elapsedNow()}, before $holdOff")
        assertEquals(Event.Connected, nextEvent())
    }

    @Test
    fun `a link with nothing inbound is dropped and redialled`() = runBlocking {
        transport(timing = FAST.copy(inactivityTimeout = 500.milliseconds))
        // The peer answers the upgrade and then goes silent without closing: a half-open socket.
        accept().handshake()
        assertEquals(Event.Connected, nextEvent())

        assertEquals(Event.Disconnected(isPermanent = false, reason = null), nextEvent())
        accept().handshake()
        assertEquals(Event.Connected, nextEvent())
    }

    @Test
    fun `sessions that keep dropping right after opening stop fighting for the slot`() = runBlocking {
        transport()
        repeat(WebSocketRadioTransport.MAX_QUICK_DROPS) { drop ->
            val peer = accept().apply { handshake() }
            assertEquals(Event.Connected, nextEvent())
            // ESP32 aborts a displaced client without a close frame when it cannot write.
            peer.socket.close()
            val last = drop == WebSocketRadioTransport.MAX_QUICK_DROPS - 1
            val expected =
                if (last) {
                    Event.Disconnected(isPermanent = true, reason = TransportDisconnectReason.WebSocketSlotContested)
                } else {
                    Event.Disconnected(isPermanent = false, reason = null)
                }
            assertEquals(expected, nextEvent())
        }
        assertNoDial()
    }

    @Test
    fun `close during a live session suppresses callbacks and stops redialling`() = runBlocking {
        val transport = transport()
        val peer = accept().apply { handshake() }
        assertEquals(Event.Connected, nextEvent())

        transport.close()
        io { peer.awaitClientClose() }

        val late = callback.events.tryReceive()
        assertTrue(late.isFailure, "no callback after close, got ${late.getOrNull()}")
        assertNoDial()
    }

    @Test
    fun `a send after close is refused`() = runBlocking {
        val transport = transport()
        accept().handshake()
        assertEquals(Event.Connected, nextEvent())

        transport.close()

        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
    }

    @Test
    fun `a failed dial is classified for the user`() {
        assertEquals(
            TransportDisconnectReason.WebSocketHandshakeRejected,
            WebSocketException("Handshake exception, expected status code 101 but was 404").toWebSocketFailure(),
        )
        assertEquals(TransportDisconnectReason.WebSocketTlsFailed, CertificateException("x").toWebSocketFailure())
        assertEquals(TransportDisconnectReason.WebSocketConnectFailed, ConnectException("x").toWebSocketFailure())
        // The browser engine's error event: no status, so not a refused upgrade.
        assertEquals(
            TransportDisconnectReason.WebSocketConnectFailed,
            WebSocketException("[object Event]").toWebSocketFailure(),
        )
    }

    private fun transport(timing: WebSocketTiming = FAST): WebSocketRadioTransport =
        WebSocketRadioTransport(callback, scope, url, timing).also {
            transports += it
            it.start()
        }

    private suspend fun accept(): Peer = io { Peer(server.accept().also { sockets += it }) }

    private suspend fun nextEvent(): Event = withTimeout(TIMEOUT) { callback.events.receive() }

    /** The backoff in [FAST] redials within tens of milliseconds, so a quiet half second means no redial. */
    private suspend fun assertNoDial(): Unit = io {
        server.soTimeout = NO_DIAL_WAIT_MS
        assertFailsWith<SocketTimeoutException> { server.accept().also { sockets += it } }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private class Frame(val opcode: Int, val payload: ByteArray)

    private class Peer(val socket: Socket) {
        private val input: InputStream = socket.getInputStream()
        private val output = socket.getOutputStream()

        fun handshake() {
            val head = readHead()
            assertTrue(head.startsWith("GET /api/v1/ws "), head.lineSequence().first())
            val key =
                head.lines().first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }.substringAfter(':').trim()
            val accept =
                Base64.getEncoder()
                    .encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()))
            write(
                "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n",
            )
        }

        fun reject(status: String) {
            readHead()
            write("HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
        }

        fun sendBinary(bytes: ByteArray) = send(OPCODE_BINARY, bytes)

        fun sendText(text: String) = send(OPCODE_TEXT, text.toByteArray())

        fun sendClose(code: Int) = send(OPCODE_CLOSE, byteArrayOf((code shr 8).toByte(), code.toByte()))

        /** One masked client frame, or null once the client has hung up. */
        fun readFrame(): Frame? {
            val b0 = input.read()
            val b1 = input.read()
            if (b0 < 0 || b1 < 0) return null
            assertEquals(0x80, b1 and 0x80, "a masked client frame")
            val length =
                when (val short = b1 and 0x7F) {
                    126 -> (input.read() shl 8) or input.read()
                    127 -> error("unexpected 64-bit frame length")
                    else -> short
                }
            val mask = input.readNBytes(4)
            val payload = input.readNBytes(length).mapIndexed { i, b -> (b.toInt() xor mask[i % 4].toInt()).toByte() }
            return Frame(b0 and 0x0F, payload.toByteArray())
        }

        /** Blocks until the client sends its close frame or hangs up. */
        fun awaitClientClose() {
            while (true) {
                val frame = readFrame() ?: return
                if (frame.opcode == OPCODE_CLOSE) return
            }
        }

        private fun readHead(): String {
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val c = input.read()
                check(c >= 0) { "client hung up during the upgrade" }
                head.append(c.toChar())
            }
            return head.toString()
        }

        private fun send(opcode: Int, payload: ByteArray) {
            output.write(byteArrayOf((0x80 or opcode).toByte(), payload.size.toByte()) + payload)
            output.flush()
        }

        private fun write(text: String) {
            output.write(text.toByteArray())
            output.flush()
        }
    }

    private sealed interface Event {
        data object Connected : Event

        data class Disconnected(val isPermanent: Boolean, val reason: TransportDisconnectReason?) : Event
    }

    private class RecordingCallback : RadioTransportCallback {
        val events = Channel<Event>(Channel.UNLIMITED)
        val fromRadio = Channel<ByteArray>(Channel.UNLIMITED)

        override fun onConnect() {
            events.trySend(Event.Connected)
        }

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
            events.trySend(Event.Disconnected(isPermanent, reason))
        }

        override fun handleFromRadio(bytes: ByteArray) {
            fromRadio.trySend(bytes)
        }
    }

    private companion object {
        const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val OPCODE_TEXT = 0x1
        const val OPCODE_BINARY = 0x2
        const val OPCODE_CLOSE = 0x8
        const val NORMAL = 1000
        const val TOO_BIG = 1009
        const val TRY_AGAIN_LATER = 1013
        const val ACCEPT_TIMEOUT_MS = 10_000
        const val NO_DIAL_WAIT_MS = 500
        val TIMEOUT: Duration = 10.seconds
        val FAST =
            WebSocketTiming(
                tryAgainLaterDelay = 10.milliseconds,
                newBackoff = { ReconnectBackoff(minMillis = 10, maxMillis = 50) },
            )
    }
}
