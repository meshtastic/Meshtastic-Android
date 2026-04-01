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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_NOTIFY_CHARACTERISTIC
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BleOtaTransportTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    private fun createTransport(
        scanner: FakeBleScanner = FakeBleScanner(),
        connection: FakeBleConnection = FakeBleConnection(),
    ): Triple<BleOtaTransport, FakeBleScanner, FakeBleConnection> {
        val transport =
            BleOtaTransport(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
        return Triple(transport, scanner, connection)
    }

    /**
     * Connect and prepare the transport for OTA operations. Must be called before [startOta] or [streamFirmware] tests.
     */
    private suspend fun connectTransport(
        transport: BleOtaTransport,
        scanner: FakeBleScanner,
        connection: FakeBleConnection,
    ) {
        connection.maxWriteValueLength = 512
        scanner.emitDevice(FakeBleDevice(address))
        val result = transport.connect()
        assertTrue(result.isSuccess, "connect() must succeed: ${result.exceptionOrNull()}")
    }

    /**
     * Emit a text response on the OTA notify characteristic. Because the notification observer from [connect] runs on
     * [Dispatchers.Unconfined], the emission is delivered synchronously to [BleOtaTransport.responseChannel].
     */
    private fun emitResponse(connection: FakeBleConnection, text: String) {
        connection.service.emitNotification(OTA_NOTIFY_CHARACTERISTIC, text.encodeToByteArray())
    }

    // -----------------------------------------------------------------------
    // connect()
    // -----------------------------------------------------------------------

    @Test
    fun `connect succeeds when device is found`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.connect()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `connect succeeds when device advertises MAC plus one`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)

        // MAC+1 of AA:BB:CC:DD:EE:FF wraps last byte: FF→00
        scanner.emitDevice(FakeBleDevice("AA:BB:CC:DD:EE:00"))

        val result = transport.connect()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `connect fails when connectAndAwait returns Disconnected`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        connection.failNextN = 1
        val (transport) = createTransport(scanner, connection)

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.connect()

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.ConnectionFailed>(result.exceptionOrNull())
    }

    // -----------------------------------------------------------------------
    // startOta()
    // -----------------------------------------------------------------------

    @Test
    fun `startOta sends command and succeeds on OK response`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        // Pre-buffer "OK" response — the notification collector runs on Unconfined,
        // so it will synchronously push to responseChannel before startOta reads it.
        emitResponse(connection, "OK")

        val result = transport.startOta(1024L, "abc123hash")

        assertTrue(result.isSuccess)

        // Verify command was written
        val commandWrites = connection.service.writes.filter { it.writeType == BleWriteType.WITH_RESPONSE }
        assertTrue(commandWrites.isNotEmpty(), "Should have written at least one command packet")
        val commandText = commandWrites.map { it.data.decodeToString() }.joinToString("")
        assertTrue(commandText.contains("OTA 1024 abc123hash"), "Command should contain OTA start message")
    }

    @Test
    fun `startOta handles ERASING then OK sequence`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        val handshakeStatuses = mutableListOf<OtaHandshakeStatus>()

        // Pre-buffer both responses
        emitResponse(connection, "ERASING")
        emitResponse(connection, "OK")

        val result = transport.startOta(2048L, "hash256") { status -> handshakeStatuses.add(status) }

        assertTrue(result.isSuccess)
        assertEquals(1, handshakeStatuses.size)
        assertIs<OtaHandshakeStatus.Erasing>(handshakeStatuses[0])
    }

    @Test
    fun `startOta fails on Hash Rejected error`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "ERR Hash Rejected")

        val result = transport.startOta(1024L, "badhash")

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.HashRejected>(result.exceptionOrNull())
    }

    @Test
    fun `startOta fails on generic error`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "ERR Something went wrong")

        val result = transport.startOta(1024L, "somehash")

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.CommandFailed>(result.exceptionOrNull())
    }

    // -----------------------------------------------------------------------
    // streamFirmware()
    // -----------------------------------------------------------------------

    @Test
    fun `streamFirmware sends data and succeeds with final OK`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        // Complete OTA handshake
        emitResponse(connection, "OK")
        transport.startOta(4L, "hash")

        val progressValues = mutableListOf<Float>()
        val firmwareData = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        // For a 4-byte firmware with chunkSize=4 and maxWriteValueLength=512:
        // 1 chunk → 1 packet → 1 ACK expected.
        // Then the code checks if it's the last packet of the last chunk —
        // if OK is received with isLastPacketOfChunk=true and nextSentBytes>=totalBytes,
        // it returns early.
        emitResponse(connection, "OK")

        val result = transport.streamFirmware(firmwareData, 4) { progress -> progressValues.add(progress) }

        assertTrue(result.isSuccess, "streamFirmware failed: ${result.exceptionOrNull()}")
        assertTrue(progressValues.isNotEmpty(), "Should have reported progress")
        assertEquals(1.0f, progressValues.last())
    }

    @Test
    fun `streamFirmware handles multi-chunk transfer`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "OK")
        transport.startOta(8L, "hash")

        val progressValues = mutableListOf<Float>()
        val firmwareData = ByteArray(8) { it.toByte() }

        // chunkSize=4, maxWriteValueLength=512
        // Chunk 1 (bytes 0-3): 1 packet → 1 ACK
        // Chunk 2 (bytes 4-7): 1 packet → 1 OK (last chunk, last packet → early return)
        emitResponse(connection, "ACK")
        emitResponse(connection, "OK")

        val result = transport.streamFirmware(firmwareData, 4) { progress -> progressValues.add(progress) }

        assertTrue(result.isSuccess, "streamFirmware failed: ${result.exceptionOrNull()}")
        assertTrue(progressValues.size >= 2, "Should have at least 2 progress reports, got $progressValues")
        assertEquals(1.0f, progressValues.last())
    }

    @Test
    fun `streamFirmware fails on connection lost`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        // Start OTA
        emitResponse(connection, "OK")
        transport.startOta(4L, "hash")

        // Simulate connection loss — disconnect sets isConnected=false via connectionState flow
        connection.disconnect()

        val result = transport.streamFirmware(byteArrayOf(0x01, 0x02, 0x03, 0x04), 4) {}

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.TransferFailed>(result.exceptionOrNull())
    }

    @Test
    fun `streamFirmware fails on Hash Mismatch error`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "OK")
        transport.startOta(4L, "hash")

        emitResponse(connection, "ERR Hash Mismatch")

        val result = transport.streamFirmware(byteArrayOf(0x01, 0x02, 0x03, 0x04), 4) {}

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.VerificationFailed>(result.exceptionOrNull())
    }

    @Test
    fun `streamFirmware fails on generic transfer error`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "OK")
        transport.startOta(4L, "hash")

        emitResponse(connection, "ERR Flash write failed")

        val result = transport.streamFirmware(byteArrayOf(0x01, 0x02, 0x03, 0x04), 4) {}

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.TransferFailed>(result.exceptionOrNull())
    }

    // -----------------------------------------------------------------------
    // close()
    // -----------------------------------------------------------------------

    @Test
    fun `close disconnects BLE connection`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)

        scanner.emitDevice(FakeBleDevice(address))
        transport.connect()

        transport.close()

        assertEquals(1, connection.disconnectCalls)
    }

    // -----------------------------------------------------------------------
    // writeData chunking
    // -----------------------------------------------------------------------

    @Test
    fun `startOta splits command across MTU-sized packets`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connection.maxWriteValueLength = 10
        scanner.emitDevice(FakeBleDevice(address))
        transport.connect().getOrThrow()

        // "OTA 1024 abc123hash\n" is 21 bytes — with maxLen=10, needs 3 packets, so 3 OK responses
        emitResponse(connection, "OK")
        emitResponse(connection, "OK")
        emitResponse(connection, "OK")

        val result = transport.startOta(1024L, "abc123hash")

        assertTrue(result.isSuccess, "startOta failed: ${result.exceptionOrNull()}")

        // Verify the command was split into multiple writes
        val commandWrites = connection.service.writes.filter { it.writeType == BleWriteType.WITH_RESPONSE }
        assertTrue(
            commandWrites.size > 1,
            "Command should be split into multiple MTU-sized packets, got ${commandWrites.size}",
        )

        // Verify reassembled command content
        val reassembled = commandWrites.map { it.data.decodeToString() }.joinToString("")
        assertEquals("OTA 1024 abc123hash\n", reassembled)
    }
}
