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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

/**
 * [WebSocketRadioTransport] against a minimal server speaking firmware's `/api/v1/ws` shape: one binary message per
 * `ToRadio` or `FromRadio`, client frames masked as RFC 6455 requires.
 */
class WebSocketRadioTransportTest {
    private val server = ServerSocket(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callback = RecordingCallback()

    @AfterTest
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    @Test
    fun `bytes go out as one binary message and a binary message comes back as one FromRadio`() = runBlocking {
        val received = CompletableDeferred<ByteArray>()
        val reply = byteArrayOf(0x0A, 0x02, 0x08, 0x01)
        thread {
            server.accept().use { socket ->
                handshake(socket)
                received.complete(readMaskedBinary(socket.getInputStream()))
                socket.getOutputStream().write(byteArrayOf(0x82.toByte(), reply.size.toByte()) + reply)
                socket.getOutputStream().flush()
                // The server leaves; the client should report a transient disconnect.
                Thread.sleep(SERVER_LINGER_MS)
            }
        }
        val transport = WebSocketRadioTransport(callback, scope, "ws://127.0.0.1:${server.localPort}/api/v1/ws")
        transport.start()

        withTimeout(TIMEOUT) { callback.connected.await() }
        val toRadio = byteArrayOf(0x18, 0x2A)
        assertEquals(true, transport.handleSendToRadio(toRadio))
        assertContentEquals(toRadio, withTimeout(TIMEOUT) { received.await() })
        assertContentEquals(reply, withTimeout(TIMEOUT) { callback.fromRadio.receive() })
        assertFalse(withTimeout(TIMEOUT) { callback.disconnected.await() })
        transport.close()
    }

    @Test
    fun `a send before the socket is open is refused`() {
        val transport = WebSocketRadioTransport(callback, scope, "ws://127.0.0.1:${server.localPort}/api/v1/ws")
        assertFalse(transport.handleSendToRadio(byteArrayOf(1)))
    }

    @Test
    fun `a 4001 close from a radio that gave the slot to another client stops the reconnects`() = runBlocking {
        thread {
            server.accept().use { socket ->
                handshake(socket)
                // Firmware's WS_CLOSE_REPLACED: a close frame carrying status 4001.
                socket.getOutputStream().write(byteArrayOf(0x88.toByte(), 0x02, 0x0F, 0xA1.toByte()))
                socket.getOutputStream().flush()
                Thread.sleep(SERVER_LINGER_MS)
            }
        }
        val transport = WebSocketRadioTransport(callback, scope, "ws://127.0.0.1:${server.localPort}/api/v1/ws")
        transport.start()

        assertEquals(true, withTimeout(TIMEOUT) { callback.disconnected.await() })
        assertEquals(TransportDisconnectReason.ReplacedByAnotherClient, callback.reason)
        transport.close()
    }

    private fun handshake(socket: Socket) {
        val input = socket.getInputStream()
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) head.append(input.read().toChar())
        val key =
            head.lines().first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }.substringAfter(':').trim()
        val accept =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()))
        val response =
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n"
        socket.getOutputStream().write(response.toByteArray())
        socket.getOutputStream().flush()
    }

    /** One client frame, which must be a masked binary message short enough for a 7-bit length. */
    private fun readMaskedBinary(input: InputStream): ByteArray {
        val b0 = input.read()
        val b1 = input.read()
        assertEquals(0x82, b0, "a final binary frame")
        assertEquals(0x80, b1 and 0x80, "a masked client frame")
        val length = b1 and 0x7F
        val mask = input.readNBytes(4)
        return input
            .readNBytes(length)
            .mapIndexed { i, b -> (b.toInt() xor mask[i % 4].toInt()).toByte() }
            .toByteArray()
    }

    private class RecordingCallback : RadioTransportCallback {
        val connected = CompletableDeferred<Unit>()
        val disconnected = CompletableDeferred<Boolean>()
        val fromRadio = Channel<ByteArray>(Channel.UNLIMITED)

        override fun onConnect() {
            connected.complete(Unit)
        }

        @Volatile var reason: TransportDisconnectReason? = null

        override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
            this.reason = reason
            disconnected.complete(isPermanent)
        }

        override fun handleFromRadio(bytes: ByteArray) {
            fromRadio.trySend(bytes)
        }
    }

    private companion object {
        const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val SERVER_LINGER_MS = 200L
        val TIMEOUT = 10.seconds
    }
}
