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

package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.core.testing.FakeBleService
import org.meshtastic.core.testing.FakeBleWrite
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class SecureDfuTransportTest {

    private val address = "00:11:22:33:44:55"
    private val dfuAddress = "00:11:22:33:44:56"

    // -----------------------------------------------------------------------
    // Phase 1: Buttonless DFU trigger
    // -----------------------------------------------------------------------

    @Test
    fun `triggerButtonlessDfu writes reboot opcode through BleService`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val transport =
            SecureDfuTransport(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.triggerButtonlessDfu()

        assertTrue(result.isSuccess)
        // Find the buttonless write (ignore any observation-triggered writes)
        val buttonlessWrites =
            connection.service.writes.filter { it.characteristic.uuid == SecureDfuUuids.BUTTONLESS_NO_BONDS }
        assertEquals(1, buttonlessWrites.size, "Should have exactly one buttonless DFU write")
        val write = buttonlessWrites.single()
        assertContentEquals(byteArrayOf(0x01), write.data)
        assertEquals(BleWriteType.WITH_RESPONSE, write.writeType)
        assertEquals(1, connection.disconnectCalls)
    }

    @Test
    fun `triggerButtonlessDfu falls back to legacy DFU service when secure FE59 is missing`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection().apply { missingServices += SecureDfuUuids.SERVICE }
        val transport =
            SecureDfuTransport(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

        scanner.emitDevice(FakeBleDevice(address))

        val result = transport.triggerButtonlessDfu()

        assertTrue(result.isSuccess, "Legacy fallback should succeed when FE59 is absent")
        // No write should have hit the secure characteristic.
        assertTrue(
            connection.service.writes.none { it.characteristic.uuid == SecureDfuUuids.BUTTONLESS_NO_BONDS },
            "Should not write to secure buttonless characteristic when FE59 is missing",
        )
        // Exactly one write of 0x01 (START_DFU) should have hit the legacy control point.
        val legacyWrites = connection.service.writes.filter { it.characteristic.uuid == LegacyDfuUuids.CONTROL_POINT }
        assertEquals(1, legacyWrites.size, "Should have exactly one legacy DFU trigger write")
        assertContentEquals(byteArrayOf(0x01, 0x04), legacyWrites.single().data)
        assertEquals(BleWriteType.WITH_RESPONSE, legacyWrites.single().writeType)
        assertEquals(1, connection.disconnectCalls)
    }

    @Test
    fun `connectToDfuMode succeeds using shared BleService observation`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val transport =
            SecureDfuTransport(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

        scanner.emitDevice(FakeBleDevice(dfuAddress))

        val result = transport.connectToDfuMode()

        assertTrue(result.isSuccess)
    }

    // -----------------------------------------------------------------------
    // Abort & close
    // -----------------------------------------------------------------------

    @Test
    fun `abort writes ABORT opcode through BleService`() = runTest {
        val connection = FakeBleConnection()
        val transport =
            SecureDfuTransport(
                scanner = FakeBleScanner(),
                connectionFactory = FakeBleConnectionFactory(connection),
                address = address,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

        transport.abort()

        val write = connection.service.writes.single()
        assertEquals(SecureDfuUuids.CONTROL_POINT, write.characteristic.uuid)
        assertContentEquals(byteArrayOf(DfuOpcode.ABORT), write.data)
        assertEquals(BleWriteType.WITH_RESPONSE, write.writeType)
    }

    // -----------------------------------------------------------------------
    // Phase 3: Init packet transfer
    // -----------------------------------------------------------------------

    @Test
    fun `transferInitPacket sends PRN 0 not 10`() = runTest {
        val env = createConnectedTransport()

        val initPacket = ByteArray(128) { it.toByte() }
        val initCrc = DfuCrc32.calculate(initPacket)
        env.configureResponder(DfuResponder(totalSize = initPacket.size, totalCrc = initCrc))

        val result = env.transport.transferInitPacket(initPacket)

        assertTrue(result.isSuccess, "transferInitPacket failed: ${result.exceptionOrNull()}")

        // Find the SET_PRN write
        val prnWrite = env.controlPointWrites().first { it.data[0] == DfuOpcode.SET_PRN }

        // PRN value is bytes [1..2] as little-endian 16-bit integer
        val prnValue = (prnWrite.data[1].toInt() and 0xFF) or ((prnWrite.data[2].toInt() and 0xFF) shl 8)
        assertEquals(0, prnValue, "Init packet PRN should be 0, not $prnValue")
    }

    @Test
    fun `transferFirmware sends PRN 10`() = runTest {
        val env = createConnectedTransport()

        val firmware = ByteArray(256) { it.toByte() }
        val firmwareCrc = DfuCrc32.calculate(firmware)
        env.configureResponder(DfuResponder(totalSize = firmware.size, totalCrc = firmwareCrc, firmwareData = firmware))

        val progressValues = mutableListOf<Float>()
        val result = env.transport.transferFirmware(firmware) { progressValues.add(it) }

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")

        // Find the SET_PRN write
        val prnWrite = env.controlPointWrites().first { it.data[0] == DfuOpcode.SET_PRN }

        val prnValue = (prnWrite.data[1].toInt() and 0xFF) or ((prnWrite.data[2].toInt() and 0xFF) shl 8)
        assertEquals(10, prnValue, "Firmware PRN should be 10")
    }

    @Test
    fun `transferFirmware reports progress`() = runTest {
        val env = createConnectedTransport()

        val firmware = ByteArray(256) { it.toByte() }
        val firmwareCrc = DfuCrc32.calculate(firmware)
        env.configureResponder(DfuResponder(totalSize = firmware.size, totalCrc = firmwareCrc, firmwareData = firmware))

        val progressValues = mutableListOf<Float>()
        val result = env.transport.transferFirmware(firmware) { progressValues.add(it) }

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")
        assertTrue(progressValues.isNotEmpty(), "Should report at least one progress value")
        assertEquals(1.0f, progressValues.last(), "Final progress should be 1.0")
    }

    // -----------------------------------------------------------------------
    // Resume logic
    // -----------------------------------------------------------------------

    @Test
    fun `resume - device has complete data - just execute`() = runTest {
        val env = createConnectedTransport()

        val initPacket = ByteArray(128) { it.toByte() }
        val initCrc = DfuCrc32.calculate(initPacket)

        // SELECT returns: device already has all bytes with matching CRC
        env.configureResponder(
            DfuResponder(
                totalSize = initPacket.size,
                totalCrc = initCrc,
                selectOffset = initPacket.size,
                selectCrc = initCrc,
            ),
        )

        val result = env.transport.transferInitPacket(initPacket)

        assertTrue(result.isSuccess, "transferInitPacket failed: ${result.exceptionOrNull()}")

        // Should NOT have sent any CREATE command — only SET_PRN, SELECT, and EXECUTE
        val opcodes = env.controlPointOpcodes()
        assertTrue(
            DfuOpcode.CREATE !in opcodes,
            "Should not send CREATE when device already has complete data. Opcodes: ${opcodes.hexList()}",
        )
        assertTrue(DfuOpcode.EXECUTE in opcodes, "Should send EXECUTE for complete data")
    }

    @Test
    fun `resume - CRC mismatch - restart from offset 0`() = runTest {
        val env = createConnectedTransport()

        val initPacket = ByteArray(128) { it.toByte() }
        val initCrc = DfuCrc32.calculate(initPacket)

        // SELECT returns: device has bytes but CRC is wrong
        env.configureResponder(
            DfuResponder(
                totalSize = initPacket.size,
                totalCrc = initCrc,
                selectOffset = 64,
                selectCrc = 0xDEADBEEF.toInt(), // Wrong CRC
            ),
        )

        val result = env.transport.transferInitPacket(initPacket)

        assertTrue(result.isSuccess, "transferInitPacket failed: ${result.exceptionOrNull()}")

        // Should have sent CREATE (restarting from 0)
        val opcodes = env.controlPointOpcodes()
        assertTrue(DfuOpcode.CREATE in opcodes, "Should send CREATE when CRC mismatches (restart from 0)")
    }

    @Test
    fun `resume - object boundary - execute last then continue`() = runTest {
        val env = createConnectedTransport()

        // Firmware with 2 objects worth of data (maxObjectSize=4096)
        val firmware = ByteArray(8192) { it.toByte() }
        val firmwareCrc = DfuCrc32.calculate(firmware)
        val firstObjectCrc = DfuCrc32.calculate(firmware, length = 4096)

        // SELECT returns: device is at object boundary (4096 bytes, exactly 1 full object)
        env.configureResponder(
            DfuResponder(
                totalSize = firmware.size,
                totalCrc = firmwareCrc,
                selectOffset = 4096,
                selectCrc = firstObjectCrc,
                maxObjectSize = 4096,
                firmwareData = firmware,
            ),
        )

        val progressValues = mutableListOf<Float>()
        val result = env.transport.transferFirmware(firmware) { progressValues.add(it) }

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")

        // Should have sent EXECUTE first (for the resumed first object), then CREATE (for the second)
        val opcodes = env.controlPointOpcodes()
        assertTrue(DfuOpcode.EXECUTE in opcodes, "Should send EXECUTE for first object")
        assertTrue(DfuOpcode.CREATE in opcodes, "Should send CREATE for second object")
    }

    // -----------------------------------------------------------------------
    // Execute retry on INVALID_OBJECT
    // -----------------------------------------------------------------------

    @Test
    fun `execute retry on INVALID_OBJECT for final object`() = runTest {
        val env = createConnectedTransport()

        val firmware = ByteArray(256) { it.toByte() }
        val firmwareCrc = DfuCrc32.calculate(firmware)

        var executeCount = 0
        env.configureResponder(
            DfuResponder(totalSize = firmware.size, totalCrc = firmwareCrc, firmwareData = firmware) { opcode ->
                if (opcode == DfuOpcode.EXECUTE) {
                    executeCount++
                    if (executeCount == 1) {
                        // First EXECUTE returns INVALID_OBJECT
                        buildDfuFailure(DfuOpcode.EXECUTE, DfuResultCode.INVALID_OBJECT)
                    } else {
                        buildDfuSuccess(DfuOpcode.EXECUTE)
                    }
                } else {
                    null // Default handling
                }
            },
        )

        val result = env.transport.transferFirmware(firmware) {}

        assertTrue(
            result.isSuccess,
            "transferFirmware should succeed after INVALID_OBJECT retry: ${result.exceptionOrNull()}",
        )
        assertEquals(2, executeCount, "Should have tried EXECUTE twice")
    }

    // -----------------------------------------------------------------------
    // Checksum validation
    // -----------------------------------------------------------------------

    @Test
    fun `transferFirmware fails on CRC mismatch after object`() = runTest {
        val env = createConnectedTransport()

        // Use exactly 200 bytes: with default MTU=20 that's 10 packets.
        // PRN=10 fires at packet 10 but pos==until so the PRN wait is skipped,
        // and the explicit CALCULATE_CHECKSUM will get the wrong CRC.
        val firmware = ByteArray(200) { it.toByte() }

        // Use a wrong CRC so the checksum after transfer won't match.
        env.configureResponder(DfuResponder(totalSize = firmware.size, totalCrc = 0xDEADBEEF.toInt()))

        val result = env.transport.transferFirmware(firmware) {}

        assertTrue(result.isFailure, "Should fail on CRC mismatch")
        val exception = result.exceptionOrNull()
        assertIs<DfuException.ChecksumMismatch>(exception, "Should throw ChecksumMismatch, got: $exception")
    }

    // -----------------------------------------------------------------------
    // Packet writing: MTU and write type
    // -----------------------------------------------------------------------

    @Test
    fun `transferInitPacket writes packet data WITHOUT_RESPONSE to PACKET characteristic`() = runTest {
        val env = createConnectedTransport()

        val initPacket = ByteArray(64) { it.toByte() }
        val initCrc = DfuCrc32.calculate(initPacket)
        env.configureResponder(DfuResponder(totalSize = initPacket.size, totalCrc = initCrc))

        val result = env.transport.transferInitPacket(initPacket)

        assertTrue(result.isSuccess, "transferInitPacket failed: ${result.exceptionOrNull()}")

        // Check PACKET writes
        val packetWrites = env.packetWrites()
        assertTrue(packetWrites.isNotEmpty(), "Should have written packet data")
        packetWrites.forEach { write ->
            assertEquals(BleWriteType.WITHOUT_RESPONSE, write.writeType, "Packet data should use WITHOUT_RESPONSE")
        }

        // Reconstruct the written data
        val writtenData = packetWrites.flatMap { it.data.toList() }.toByteArray()
        assertContentEquals(initPacket, writtenData, "Written packet data should match init packet")
    }

    @Test
    fun `packet writes respect MTU size`() = runTest {
        val env = createConnectedTransport(mtu = 64)

        val initPacket = ByteArray(200) { it.toByte() }
        val initCrc = DfuCrc32.calculate(initPacket)
        env.configureResponder(DfuResponder(totalSize = initPacket.size, totalCrc = initCrc))

        val result = env.transport.transferInitPacket(initPacket)

        assertTrue(result.isSuccess, "transferInitPacket failed: ${result.exceptionOrNull()}")

        val packetWrites = env.packetWrites()
        packetWrites.forEach { write ->
            assertTrue(write.data.size <= 64, "Packet write size ${write.data.size} exceeds MTU of 64")
        }
        val writtenData = packetWrites.flatMap { it.data.toList() }.toByteArray()
        assertContentEquals(initPacket, writtenData)
    }

    @Test
    fun `default MTU is 20 bytes when connection returns null`() = runTest {
        val env = createConnectedTransport(mtu = null)

        val initPacket = ByteArray(64) { it.toByte() }
        val initCrc = DfuCrc32.calculate(initPacket)
        env.configureResponder(DfuResponder(totalSize = initPacket.size, totalCrc = initCrc))

        val result = env.transport.transferInitPacket(initPacket)

        assertTrue(result.isSuccess, "transferInitPacket failed: ${result.exceptionOrNull()}")

        val packetWrites = env.packetWrites()
        packetWrites.forEach { write ->
            assertTrue(
                write.data.size <= 20,
                "Packet write size ${write.data.size} should not exceed default MTU of 20",
            )
        }
    }

    // -----------------------------------------------------------------------
    // Multi-object firmware transfer
    // -----------------------------------------------------------------------

    @Test
    fun `transferFirmware splits data into objects of maxObjectSize`() = runTest {
        val env = createConnectedTransport()

        // 6000 bytes with maxObjectSize=4096 → 2 objects (4096 + 1904)
        val firmware = ByteArray(6000) { it.toByte() }
        val firmwareCrc = DfuCrc32.calculate(firmware)
        env.configureResponder(
            DfuResponder(
                totalSize = firmware.size,
                totalCrc = firmwareCrc,
                maxObjectSize = 4096,
                firmwareData = firmware,
            ),
        )

        val progressValues = mutableListOf<Float>()
        val result = env.transport.transferFirmware(firmware) { progressValues.add(it) }

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")

        // Should have 2 CREATE commands
        val createWrites = env.controlPointWrites().filter { it.data[0] == DfuOpcode.CREATE }
        assertEquals(2, createWrites.size, "Should send 2 CREATE commands for 6000 bytes / 4096 max")

        // First CREATE should request 4096 bytes, second should request 1904
        val firstSize = createWrites[0].data.drop(2).toByteArray().readIntLe(0)
        val secondSize = createWrites[1].data.drop(2).toByteArray().readIntLe(0)
        assertEquals(4096, firstSize, "First object size should be 4096")
        assertEquals(1904, secondSize, "Second object size should be 1904")

        // Progress should end at 1.0
        assertEquals(1.0f, progressValues.last())
        assertEquals(2, progressValues.size, "Should have 2 progress reports (one per object)")
    }

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    /** A test environment holding a connected transport and its backing fakes. */
    private class TestEnv(val transport: SecureDfuTransport, val service: AutoRespondingBleService) {
        fun configureResponder(responder: DfuResponder) {
            service.responder = responder
            service.firmwareData = responder.firmwareData
        }

        fun controlPointWrites(): List<FakeBleWrite> =
            service.delegate.writes.filter { it.characteristic.uuid == SecureDfuUuids.CONTROL_POINT }

        fun controlPointOpcodes(): List<Byte> = controlPointWrites().map { it.data[0] }

        fun packetWrites(): List<FakeBleWrite> =
            service.delegate.writes.filter { it.characteristic.uuid == SecureDfuUuids.PACKET }
    }

    /**
     * A [BleService] wrapper that delegates to [FakeBleService] but intercepts writes to CONTROL_POINT and immediately
     * emits a DFU notification response. This solves the coroutine ordering problem where `sendCommand()` writes then
     * suspends on `notificationChannel.receive()` — the response must be in the channel before the receive.
     *
     * Because [FakeBleConnection.profile] runs with [kotlinx.coroutines.Dispatchers.Unconfined], the notification
     * emitted here propagates immediately through the observation flow into the transport's `notificationChannel`.
     */
    private class AutoRespondingBleService(val delegate: FakeBleService) : BleService {
        var responder: DfuResponder? = null

        /**
         * The cumulative firmware offset the simulated device is at. This must match the absolute position the
         * transport expects from CALCULATE_CHECKSUM responses.
         *
         * Updated by:
         * - SELECT: set to the responder's [DfuResponder.selectOffset] (initial state)
         * - CREATE: reset to [executedOffset] (device discards partial object data)
         * - PACKET writes: incremented by write size
         * - EXECUTE: [executedOffset] advances to current value (object committed)
         */
        private var accumulatedPacketBytes = 0

        /** The offset of the last executed (committed) object boundary. */
        private var executedOffset = 0

        /** Tracks packets since last PRN response for flow control simulation. */
        private var packetsSincePrn = 0

        /** Current PRN interval — set when SET_PRN is received. 0 = disabled. */
        private var prnInterval = 0

        /** Current object size target from the last CREATE command. */
        private var currentObjectSize = 0

        /** Bytes written in the current object (resets on CREATE). */
        private var currentObjectBytesWritten = 0

        /** The firmware data being transferred, for computing partial CRCs in PRN responses. */
        var firmwareData: ByteArray? = null

        override fun hasCharacteristic(characteristic: BleCharacteristic) = delegate.hasCharacteristic(characteristic)

        override fun observe(characteristic: BleCharacteristic): Flow<ByteArray> = delegate.observe(characteristic)

        override suspend fun read(characteristic: BleCharacteristic): ByteArray = delegate.read(characteristic)

        override fun preferredWriteType(characteristic: BleCharacteristic): BleWriteType =
            delegate.preferredWriteType(characteristic)

        override suspend fun write(characteristic: BleCharacteristic, data: ByteArray, writeType: BleWriteType) {
            delegate.write(characteristic, data, writeType)

            if (characteristic.uuid == SecureDfuUuids.PACKET) {
                accumulatedPacketBytes += data.size
                currentObjectBytesWritten += data.size
                packetsSincePrn++

                // Simulate device-side PRN flow control: emit a ChecksumResult notification
                // every prnInterval packets, just like a real BLE DFU target would.
                // Skip if this is the last packet in the current object (pos == until),
                // matching the transport's `pos < until` guard.
                val objectComplete = currentObjectBytesWritten >= currentObjectSize
                if (prnInterval > 0 && packetsSincePrn >= prnInterval && !objectComplete) {
                    packetsSincePrn = 0
                    val crc =
                        firmwareData?.let { DfuCrc32.calculate(it, length = minOf(accumulatedPacketBytes, it.size)) }
                            ?: 0
                    delegate.emitNotification(
                        SecureDfuUuids.CONTROL_POINT,
                        buildChecksumResponse(accumulatedPacketBytes, crc),
                    )
                }
                return
            }

            if (characteristic.uuid == SecureDfuUuids.CONTROL_POINT && data.isNotEmpty()) {
                val opcode = data[0]

                // Capture the PRN interval from SET_PRN commands
                if (opcode == DfuOpcode.SET_PRN && data.size >= 3) {
                    prnInterval = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                    packetsSincePrn = 0
                }

                // On SELECT, initialize the device's offset to the responder's selectOffset.
                // On a real device, SELECT returns the cumulative state (all executed objects +
                // any partial current object). We do NOT set executedOffset here — that only
                // advances on EXECUTE, because selectOffset may include non-executed partial
                // data that the device will discard on CREATE.
                if (opcode == DfuOpcode.SELECT) {
                    val resp = responder
                    if (resp != null) {
                        accumulatedPacketBytes = resp.selectOffset
                        currentObjectBytesWritten = 0
                        packetsSincePrn = 0
                    }
                }

                // On CREATE, the device discards any partial (non-executed) data and starts a
                // fresh object. Reset accumulatedPacketBytes to the last executed boundary.
                // This correctly handles:
                // - Fresh transfer: executedOffset=0 → accumulatedPacketBytes resets to 0
                // - CRC mismatch restart: executedOffset=0 → resets to 0 (discards bad data)
                // - Multi-object: executedOffset=4096 → resets to 4096 (keeps executed data)
                if (opcode == DfuOpcode.CREATE && data.size >= 6) {
                    accumulatedPacketBytes = executedOffset
                    currentObjectSize = data.drop(2).toByteArray().readIntLe(0)
                    currentObjectBytesWritten = 0
                    packetsSincePrn = 0
                }

                // On EXECUTE, the device commits the current object. Advance executedOffset
                // to the current accumulated position.
                if (opcode == DfuOpcode.EXECUTE) {
                    executedOffset = accumulatedPacketBytes
                }

                val resp = responder ?: return
                val response = resp.respond(opcode, accumulatedPacketBytes)
                if (response != null) {
                    delegate.emitNotification(SecureDfuUuids.CONTROL_POINT, response)
                }
            }
        }
    }

    /**
     * A [BleConnection] wrapper that uses [AutoRespondingBleService] instead of the plain [FakeBleService], so writes
     * to CONTROL_POINT automatically trigger notification responses before the transport's `awaitNotification()`
     * suspends.
     */
    private class AutoRespondingBleConnection(
        private val delegate: FakeBleConnection,
        val autoService: AutoRespondingBleService,
    ) : BleConnection {
        override val device: BleDevice?
            get() = delegate.device

        override val deviceFlow: StateFlow<BleDevice?>
            get() = delegate.deviceFlow

        override val connectionState: StateFlow<BleConnectionState>
            get() = delegate.connectionState

        override suspend fun connect(device: BleDevice) = delegate.connect(device)

        override suspend fun connectAndAwait(device: BleDevice, timeout: Duration) =
            delegate.connectAndAwait(device, timeout)

        override suspend fun disconnect() = delegate.disconnect()

        override suspend fun <T> profile(
            serviceUuid: kotlin.uuid.Uuid,
            timeout: Duration,
            setup: suspend CoroutineScope.(BleService) -> T,
        ): T = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).setup(autoService)

        override fun maximumWriteValueLength(writeType: BleWriteType): Int? =
            delegate.maximumWriteValueLength(writeType)
    }

    /**
     * Encapsulates the DFU protocol response logic. For each opcode written to CONTROL_POINT, produces the correct
     * notification bytes.
     */
    private class DfuResponder(
        private val totalSize: Int,
        private val totalCrc: Int,
        val selectOffset: Int = 0,
        private val selectCrc: Int = 0,
        private val maxObjectSize: Int = DEFAULT_MAX_OBJECT_SIZE,
        /** The firmware data for computing partial CRCs (needed for CALCULATE_CHECKSUM). */
        val firmwareData: ByteArray? = null,
        private val customHandler: ((Byte) -> ByteArray?)? = null,
    ) {
        fun respond(opcode: Byte, accumulatedPacketBytes: Int): ByteArray? {
            // Check custom handler first
            customHandler?.invoke(opcode)?.let {
                return it
            }

            return when (opcode) {
                DfuOpcode.SET_PRN -> buildDfuSuccess(DfuOpcode.SET_PRN)
                DfuOpcode.SELECT -> buildSelectResponse(maxObjectSize, selectOffset, selectCrc)
                DfuOpcode.CREATE -> buildDfuSuccess(DfuOpcode.CREATE)
                DfuOpcode.CALCULATE_CHECKSUM -> {
                    val crc =
                        firmwareData?.let { DfuCrc32.calculate(it, length = minOf(accumulatedPacketBytes, it.size)) }
                            ?: totalCrc
                    buildChecksumResponse(accumulatedPacketBytes, crc)
                }
                DfuOpcode.EXECUTE -> buildDfuSuccess(DfuOpcode.EXECUTE)
                DfuOpcode.ABORT -> buildDfuSuccess(DfuOpcode.ABORT)
                else -> null
            }
        }
    }

    /**
     * Creates a [SecureDfuTransport] already connected to DFU mode with an [AutoRespondingBleService] ready to handle
     * DFU commands.
     */
    private suspend fun createConnectedTransport(mtu: Int? = null): TestEnv {
        val scanner = FakeBleScanner()
        val fakeConnection = FakeBleConnection()
        fakeConnection.maxWriteValueLength = mtu
        val autoService = AutoRespondingBleService(fakeConnection.service)
        val autoConnection = AutoRespondingBleConnection(fakeConnection, autoService)
        val factory =
            object : BleConnectionFactory {
                override fun create(scope: CoroutineScope, tag: String): BleConnection = autoConnection
            }

        val transport =
            SecureDfuTransport(
                scanner = scanner,
                connectionFactory = factory,
                address = address,
                dispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

        scanner.emitDevice(FakeBleDevice(dfuAddress))
        val connectResult = transport.connectToDfuMode()
        assertTrue(connectResult.isSuccess, "connectToDfuMode failed: ${connectResult.exceptionOrNull()}")

        return TestEnv(transport, autoService)
    }

    // -----------------------------------------------------------------------
    // DFU response builders
    // -----------------------------------------------------------------------

    companion object {
        private const val DEFAULT_MAX_OBJECT_SIZE = 4096

        fun buildDfuSuccess(opcode: Byte): ByteArray =
            byteArrayOf(DfuOpcode.RESPONSE_CODE, opcode, DfuResultCode.SUCCESS)

        fun buildDfuFailure(opcode: Byte, resultCode: Byte): ByteArray =
            byteArrayOf(DfuOpcode.RESPONSE_CODE, opcode, resultCode)

        fun buildSelectResponse(maxSize: Int, offset: Int, crc32: Int): ByteArray {
            val response = ByteArray(15)
            response[0] = DfuOpcode.RESPONSE_CODE
            response[1] = DfuOpcode.SELECT
            response[2] = DfuResultCode.SUCCESS
            intToLeBytes(maxSize).copyInto(response, 3)
            intToLeBytes(offset).copyInto(response, 7)
            intToLeBytes(crc32).copyInto(response, 11)
            return response
        }

        fun buildChecksumResponse(offset: Int, crc32: Int): ByteArray {
            val response = ByteArray(11)
            response[0] = DfuOpcode.RESPONSE_CODE
            response[1] = DfuOpcode.CALCULATE_CHECKSUM
            response[2] = DfuResultCode.SUCCESS
            intToLeBytes(offset).copyInto(response, 3)
            intToLeBytes(crc32).copyInto(response, 7)
            return response
        }

        fun List<Byte>.hexList(): String = map { "0x${it.toUByte().toString(16)}" }.toString()
    }
}
