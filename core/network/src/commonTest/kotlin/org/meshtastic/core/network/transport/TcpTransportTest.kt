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
@file:Suppress("MagicNumber")

package org.meshtastic.core.network.transport

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TcpTransportTest {

    /**
     * THE SPIKE. The whole migration rests on this Ktor assumption: a `withTimeoutOrNull` that cancels a parked
     * `readAvailable` must leave the channel usable for the next read — that is what reproduces the old
     * `Socket.soTimeout` resumable inactivity timeout. If this fails, switch [TcpTransport]'s read loop to the watchdog
     * fallback (see plan).
     */
    @Test
    fun `read channel survives a withTimeoutOrNull read timeout and resumes`() = runTest {
        withContext(Dispatchers.Default) {
            val selector = SelectorManager(Dispatchers.Default)
            val server = aSocket(selector).tcp().bind(hostname = LOCALHOST, port = 0)
            val port = server.localAddress.port()
            try {
                val acceptJob = async { server.accept() }
                val client = aSocket(selector).tcp().connect(InetSocketAddress(LOCALHOST, port))
                val serverConn = acceptJob.await()

                val clientRead = client.openReadChannel()
                val serverWrite = serverConn.openWriteChannel(autoFlush = true)
                val buf = ByteArray(64)

                // 1st read: server is silent, so this must time out (null), cancelling the parked read.
                val firstRead = withTimeoutOrNull(200) { clientRead.readAvailable(buf) }
                assertNull(firstRead, "expected the idle read to time out")

                // 2nd read on the SAME channel: server now sends a byte — the channel must still deliver it.
                serverWrite.writeFully(byteArrayOf(0x42))
                val secondRead = withTimeout(2_000) { clientRead.readAvailable(buf) }
                assertEquals(1, secondRead, "channel was torn down by the previous read-timeout cancellation")
                assertEquals(0x42.toByte(), buf[0])

                client.close()
                serverConn.close()
            } finally {
                server.close()
                selector.close()
            }
        }
    }

    /** End-to-end: connect, receive a framed packet from the peer, decode it through [StreamFrameCodec]. */
    @Test
    fun `transport decodes a framed packet sent by the peer`() = runTest {
        withContext(Dispatchers.Default) {
            val server = TestTcpServer.start()
            val connected = CompletableDeferred<Unit>()
            val received = CompletableDeferred<ByteArray>()
            val transport =
                TcpTransport(
                    dispatchers = testDispatchers(),
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                    listener =
                    object : TcpTransport.Listener {
                        override fun onConnected() {
                            connected.complete(Unit)
                        }

                        override fun onDisconnected() = Unit

                        override fun onPacketReceived(bytes: ByteArray) {
                            received.complete(bytes)
                        }
                    },
                )

            try {
                transport.start("$LOCALHOST:${server.port}")
                val conn = withTimeout(5_000) { server.awaitConnection() }
                withTimeout(5_000) { connected.await() }

                // The transport sends 4 wake bytes (0x94) on connect; drain them so they do not pollute asserts.
                conn.drain(4)

                val payload = byteArrayOf(0x10, 0x20, 0x30)
                conn.writeFramed(payload)

                val decoded = withTimeout(5_000) { received.await() }
                assertContentEquals(payload, decoded)
                assertTrue(transport.isConnected)
            } finally {
                transport.stop()
                server.close()
            }
        }
    }

    private fun testDispatchers() =
        CoroutineDispatchers(io = Dispatchers.Default, main = Dispatchers.Default, default = Dispatchers.Default)

    private class TestTcpServer
    private constructor(
        private val selector: SelectorManager,
        private val socket: ServerSocket,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val accepted = CompletableDeferred<TestTcpConnection>()
        val port: Int = socket.localAddress.port()

        init {
            scope.launch {
                runCatching {
                    val s = socket.accept()
                    accepted.complete(TestTcpConnection(s.openReadChannel(), s.openWriteChannel(autoFlush = true)))
                }
                    .onFailure { accepted.completeExceptionally(it) }
            }
        }

        suspend fun awaitConnection(): TestTcpConnection = accepted.await()

        fun close() {
            runCatching { socket.close() }
            runCatching { selector.close() }
            scope.cancel()
        }

        companion object {
            suspend fun start(): TestTcpServer {
                val selector = SelectorManager(Dispatchers.Default)
                return TestTcpServer(selector, aSocket(selector).tcp().bind(hostname = LOCALHOST, port = 0))
            }
        }
    }

    private class TestTcpConnection(
        private val read: io.ktor.utils.io.ByteReadChannel,
        private val write: ByteWriteChannel,
    ) {
        /** Reads and discards exactly [count] bytes. */
        suspend fun drain(count: Int) {
            val buf = ByteArray(count)
            var off = 0
            while (off < count) {
                read.awaitContent()
                val n = read.readAvailable(buf, off, count - off)
                if (n == -1) break
                off += n
            }
        }

        /** Writes a Meshtastic stream frame: [START1][START2][len MSB][len LSB][payload]. */
        suspend fun writeFramed(payload: ByteArray) {
            val frame =
                byteArrayOf(
                    StreamFrameCodec.START1,
                    StreamFrameCodec.START2,
                    (payload.size shr 8).toByte(),
                    (payload.size and 0xff).toByte(),
                ) + payload
            write.writeFully(frame)
            write.flush()
        }
    }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
    }
}
