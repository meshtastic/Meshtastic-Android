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

package org.meshtastic.feature.firmware.ota

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readLine
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WifiOtaTransportTest {

    @Test
    fun `connect succeeds when TCP socket is established`() = runTest {
        val server = TestTcpOtaServer.start()
        val transport = WifiOtaTransport(deviceIpAddress = LOCALHOST, port = server.port)

        try {
            val result = transport.connect()

            assertTrue(result.isSuccess, "connect() must succeed: ${result.exceptionOrNull()}")
            assertNotNull(server.awaitConnection())
        } finally {
            transport.close()
            server.close()
        }
    }

    @Test
    fun `connect fails when device is unreachable`() = runTest {
        val server = TestTcpOtaServer.start()
        val port = server.port
        server.close()

        val transport = WifiOtaTransport(deviceIpAddress = LOCALHOST, port = port)
        try {
            val result = transport.connect()

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        } finally {
            transport.close()
        }
    }

    @Test
    fun `startOta sends OTA command and succeeds on OK response`() = runTest {
        val (transport, server, connection) = createConnectedTransport()

        try {
            val startJob = async(Dispatchers.Default) { transport.startOta(1024L, "abc123hash") }

            assertEquals("OTA 1024 abc123hash", connection.readLine())
            connection.sendResponse("OK")

            assertTrue(startJob.await().isSuccess)
        } finally {
            transport.close()
            server.close()
        }
    }

    @Test
    fun `startOta reports erasing status before succeeding`() = runTest {
        val (transport, server, connection) = createConnectedTransport()
        val statuses = mutableListOf<OtaHandshakeStatus>()

        try {
            val startJob =
                async(Dispatchers.Default) { transport.startOta(2048L, "hash256") { status -> statuses += status } }

            assertEquals("OTA 2048 hash256", connection.readLine())
            connection.sendResponse("ERASING")
            connection.sendResponse("OK")

            assertTrue(startJob.await().isSuccess)
            assertEquals(1, statuses.size)
            assertIs<OtaHandshakeStatus.Erasing>(statuses.single())
        } finally {
            transport.close()
            server.close()
        }
    }

    @Test
    fun `startOta fails on hash rejected response`() = runTest {
        val (transport, server, connection) = createConnectedTransport()

        try {
            val startJob = async(Dispatchers.Default) { transport.startOta(1024L, "bad-hash") }

            assertEquals("OTA 1024 bad-hash", connection.readLine())
            connection.sendResponse("ERR Hash Rejected")

            val result = startJob.await()
            assertTrue(result.isFailure)
            assertIs<OtaProtocolException.HashRejected>(result.exceptionOrNull())
        } finally {
            transport.close()
            server.close()
        }
    }

    @Test
    fun `streamFirmware sends 1024-byte chunks and waits for final OK`() = runTest {
        val (transport, server, connection) = createConnectedTransport()
        val firmware = ByteArray(2500) { (it % 251).toByte() }
        val progressValues = mutableListOf<Float>()

        try {
            val startJob = async(Dispatchers.Default) { transport.startOta(firmware.size.toLong(), "firmware-hash") }
            assertEquals("OTA 2500 firmware-hash", connection.readLine())
            connection.sendResponse("OK")
            assertTrue(startJob.await().isSuccess)

            val streamJob =
                async(Dispatchers.Default) {
                    transport.streamFirmware(firmware, WifiOtaTransport.RECOMMENDED_CHUNK_SIZE) { progress ->
                        progressValues += progress
                    }
                }

            assertContentEquals(firmware, connection.readExactly(firmware.size))
            connection.sendResponse("ACK")
            connection.sendResponse("OK")

            assertTrue(streamJob.await().isSuccess)
            assertEquals(3, progressValues.size)
            assertEquals(1024f / 2500f, progressValues[0], 0.0001f)
            assertEquals(2048f / 2500f, progressValues[1], 0.0001f)
            assertEquals(1.0f, progressValues[2], 0.0001f)
        } finally {
            transport.close()
            server.close()
        }
    }

    @Test
    fun `streamFirmware fails on hash mismatch verification error`() = runTest {
        val (transport, server, connection) = createConnectedTransport()
        val firmware = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        try {
            val startJob = async(Dispatchers.Default) { transport.startOta(firmware.size.toLong(), "firmware-hash") }
            assertEquals("OTA 4 firmware-hash", connection.readLine())
            connection.sendResponse("OK")
            assertTrue(startJob.await().isSuccess)

            val streamJob =
                async(Dispatchers.Default) {
                    transport.streamFirmware(firmware, WifiOtaTransport.RECOMMENDED_CHUNK_SIZE) {}
                }

            assertContentEquals(firmware, connection.readExactly(firmware.size))
            connection.sendResponse("ERR Hash Mismatch")

            val result = streamJob.await()
            assertTrue(result.isFailure)
            assertIs<OtaProtocolException.VerificationFailed>(result.exceptionOrNull())
        } finally {
            transport.close()
            server.close()
        }
    }

    @Test
    fun `close resets transport and closes TCP connection`() = runTest {
        val (transport, server, _) = createConnectedTransport()

        try {
            transport.close()

            val result = transport.startOta(1L, "hash")
            assertTrue(result.isFailure)
            assertIs<OtaProtocolException.ConnectionFailed>(result.exceptionOrNull())
        } finally {
            server.close()
        }
    }

    private suspend fun createConnectedTransport(): Triple<WifiOtaTransport, TestTcpOtaServer, TestTcpOtaConnection> {
        val server = TestTcpOtaServer.start()
        val transport = WifiOtaTransport(deviceIpAddress = LOCALHOST, port = server.port)
        val result = transport.connect()
        assertTrue(result.isSuccess, "connect() must succeed: ${result.exceptionOrNull()}")
        return Triple(transport, server, server.awaitConnection())
    }

    private class TestTcpOtaServer
    private constructor(
        private val selectorManager: SelectorManager,
        private val serverSocket: ServerSocket,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val acceptedConnection = CompletableDeferred<TestTcpOtaConnection>()

        val port: Int = serverSocket.localAddress.port()

        init {
            scope.launch {
                runCatching {
                    val socket = serverSocket.accept()
                    acceptedConnection.complete(
                        TestTcpOtaConnection(
                            socket = socket,
                            readChannel = socket.openReadChannel(),
                            writeChannel = socket.openWriteChannel(autoFlush = true),
                        ),
                    )
                }
                    .onFailure { acceptedConnection.completeExceptionally(it) }
            }
        }

        suspend fun awaitConnection(): TestTcpOtaConnection = acceptedConnection.await()

        suspend fun close() {
            if (acceptedConnection.isCompleted && !acceptedConnection.isCancelled) {
                runCatching { acceptedConnection.await().close() }
            }
            runCatching { serverSocket.close() }
            runCatching { selectorManager.close() }
            scope.cancel()
        }

        companion object {
            suspend fun start(): TestTcpOtaServer {
                val selectorManager = SelectorManager(Dispatchers.Default)
                val serverSocket = aSocket(selectorManager).tcp().bind(hostname = LOCALHOST, port = 0)
                return TestTcpOtaServer(selectorManager, serverSocket)
            }
        }
    }

    private class TestTcpOtaConnection(
        private val socket: Socket,
        private val readChannel: ByteReadChannel,
        private val writeChannel: ByteWriteChannel,
    ) {
        suspend fun readLine(): String? = readChannel.readLine()

        suspend fun sendResponse(text: String) {
            writeChannel.writeStringUtf8("$text\n")
            writeChannel.flush()
        }

        suspend fun readExactly(byteCount: Int): ByteArray {
            val bytes = ByteArray(byteCount)
            var offset = 0
            while (offset < byteCount) {
                readChannel.awaitContent()
                val bytesRead = readChannel.readAvailable(bytes, offset, byteCount - offset)
                if (bytesRead == -1) break
                offset += bytesRead
            }
            return bytes.copyOf(offset)
        }

        suspend fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val LOCALHOST = "127.0.0.1"
    }
}
