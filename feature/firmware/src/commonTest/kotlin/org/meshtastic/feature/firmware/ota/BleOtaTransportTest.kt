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
import org.meshtastic.core.ble.MeshtasticBleConstants.OTA_WRITE_CHARACTERISTIC
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
        seedOtaCharacteristics: Boolean = true,
    ): Triple<BleOtaTransport, FakeBleScanner, FakeBleConnection> {
        if (seedOtaCharacteristics) {
            // Seed at the choke point instead of every connect() site — the new
            // service.requireOtaCharacteristics() validation in BleOtaTransport.connect() rejects
            // services missing these, so default to present; negative tests opt out.
            connection.service.addCharacteristic(OTA_NOTIFY_CHARACTERISTIC)
            connection.service.addCharacteristic(OTA_WRITE_CHARACTERISTIC)
        }
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

    @Test
    fun `connect fails when OTA characteristics are missing`() = runTest {
        val (transport, scanner) = createTransport(seedOtaCharacteristics = false)

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.connect()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertIs<OtaProtocolException.ConnectionFailed>(exception)
        val message = exception.message.orEmpty()
        assertTrue(message.contains("OTA service"))
        assertTrue(message.contains("TX notify characteristic"))
        assertTrue(message.contains("OTA write characteristic"))
    }

    @Test
    fun `connect fails when notification observation fails before subscription`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val failure = IllegalStateException("observe failed before CCCD")
        connection.service.observeBeforeSubscriptionExceptionByCharacteristic[OTA_NOTIFY_CHARACTERISTIC] = failure
        val (transport) = createTransport(scanner, connection)

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.connect()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertIs<OtaProtocolException.ConnectionFailed>(exception)
        val cause = assertIs<IllegalStateException>(exception.cause)
        assertEquals(failure.message, cause.message)
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

        // 4-byte firmware, chunkSize 4, payload 512 → one chunk = one write expecting one response.
        // The terminal OK on the last (only) chunk completes the transfer.
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
    fun `streamFirmware reuses discovered OTA service for chunk writes`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connection.maxWriteValueLength = 200
        scanner.emitDevice(FakeBleDevice(address))
        transport.connect().getOrThrow()
        val profileCallsAfterConnect = connection.profileCalls

        emitResponse(connection, "OK")
        transport.startOta(600L, "hash").getOrThrow()

        emitResponse(connection, "ACK")
        emitResponse(connection, "ACK")
        emitResponse(connection, "OK")

        val result = transport.streamFirmware(ByteArray(600) { it.toByte() }, 512) {}

        assertTrue(result.isSuccess, "streamFirmware failed: ${result.exceptionOrNull()}")
        assertEquals(1, profileCallsAfterConnect, "connect should discover the OTA profile once")
        assertEquals(
            profileCallsAfterConnect,
            connection.profileCalls,
            "writes should reuse the discovered OTA service",
        )
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

    @Test
    fun `streamFirmware clamps chunk to negotiated write payload and acks once per chunk`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        // Negotiated payload (200) smaller than the requested chunk (512) — the real MTU-512 case is payload 509.
        connection.maxWriteValueLength = 200
        scanner.emitDevice(FakeBleDevice(address))
        transport.connect().getOrThrow()

        emitResponse(connection, "OK")
        transport.startOta(600L, "hash")

        // The transport must clamp 512 → 200, yielding three 200-byte chunks, each ONE write expecting ONE
        // response. Before the fix the 512-byte chunk fragmented into multiple writes and the loop waited for a
        // response per fragment, desyncing against the device's one-response-per-chunk cadence (10s ACK-timeout hang).
        emitResponse(connection, "ACK")
        emitResponse(connection, "ACK")
        emitResponse(connection, "OK")

        val progress = mutableListOf<Float>()
        val result = transport.streamFirmware(ByteArray(600) { it.toByte() }, 512) { progress.add(it) }

        assertTrue(result.isSuccess, "streamFirmware failed: ${result.exceptionOrNull()}")
        assertEquals(1.0f, progress.last())

        val dataWrites = connection.service.writes.filter { it.writeType == BleWriteType.WITHOUT_RESPONSE }
        assertEquals(3, dataWrites.size, "expected exactly one write per 200-byte chunk")
        assertTrue(dataWrites.all { it.data.size <= 200 }, "no write may exceed the negotiated payload")
    }

    @Test
    fun `streamFirmware surfaces a final-chunk error instead of reporting success`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connection.maxWriteValueLength = 200
        scanner.emitDevice(FakeBleDevice(address))
        transport.connect().getOrThrow()

        emitResponse(connection, "OK")
        transport.startOta(600L, "hash")

        // First two chunks ack; the device then rejects the final image hash. Must fail, never report success.
        emitResponse(connection, "ACK")
        emitResponse(connection, "ACK")
        emitResponse(connection, "ERR Hash Mismatch")

        val result = transport.streamFirmware(ByteArray(600) { it.toByte() }, 512) {}

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.VerificationFailed>(result.exceptionOrNull())
    }

    @Test
    fun `streamFirmware succeeds when the last chunk is ACKed then a separate terminal OK arrives`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "OK")
        transport.startOta(8L, "hash")

        // Last chunk is ACKed (not OK); the device then sends a separate terminal OK. Exercises the post-loop
        // verification wait, which must complete successfully.
        emitResponse(connection, "ACK")
        emitResponse(connection, "ACK")
        emitResponse(connection, "OK")

        val result = transport.streamFirmware(ByteArray(8) { it.toByte() }, 4) {}

        assertTrue(result.isSuccess, "streamFirmware failed: ${result.exceptionOrNull()}")
    }

    @Test
    fun `streamFirmware fails when a terminal error arrives after the last chunk is ACKed`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "OK")
        transport.startOta(8L, "hash")

        // All chunks ACKed, then the device rejects the image post-transfer. Exercises the post-loop error branch —
        // a late error must surface as failure, never success.
        emitResponse(connection, "ACK")
        emitResponse(connection, "ACK")
        emitResponse(connection, "ERR Hash Mismatch")

        val result = transport.streamFirmware(ByteArray(8) { it.toByte() }, 4) {}

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.VerificationFailed>(result.exceptionOrNull())
    }

    @Test
    fun `streamFirmware fails when a non-final chunk receives OK`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        emitResponse(connection, "OK")
        transport.startOta(8L, "hash")

        // A premature OK on the first of two chunks: the device sends OK only at completion, so this signals a
        // size disagreement and must fail rather than be treated as an ACK.
        emitResponse(connection, "OK")

        val result = transport.streamFirmware(ByteArray(8) { it.toByte() }, 4) {}

        assertTrue(result.isFailure)
        assertIs<OtaProtocolException.TransferFailed>(result.exceptionOrNull())
    }

    @Test
    fun `streamFirmware fails immediately on empty firmware`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val (transport) = createTransport(scanner, connection)
        connectTransport(transport, scanner, connection)

        // Empty image: must fail right away rather than skipping the loop and waiting out VERIFICATION_TIMEOUT. No
        // device response is buffered, so a regression (waiting for a response) would surface as a Timeout, not this.
        val result = transport.streamFirmware(ByteArray(0), 512) {}

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
