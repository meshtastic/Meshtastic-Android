/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("MagicNumber", "LargeClass", "TooManyFunctions")

package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
class LegacyDfuTransportTest {

    private val address = "00:11:22:33:44:55"
    private val dfuAddress = "00:11:22:33:44:56"

    // -----------------------------------------------------------------------
    // Phase 2: connectToDfuMode
    // -----------------------------------------------------------------------

    @Test
    fun `connectToDfuMode succeeds when bootloader exposes 1530 service`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val transport =
            LegacyDfuTransport(scanner, FakeBleConnectionFactory(connection), address, Dispatchers.Unconfined)

        scanner.emitDevice(FakeBleDevice(dfuAddress))

        val result = transport.connectToDfuMode()

        assertTrue(result.isSuccess, "connectToDfuMode failed: ${result.exceptionOrNull()}")
    }

    @Test
    fun `connectToDfuMode fails fast on unsupported old DFU Version lt 5`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        // Pre-seed the version characteristic with a SDK 6 version (0x0004 LE).
        connection.service.enqueueRead(LEGACY_DFU_VERSION_UUID, byteArrayOf(0x04, 0x00))
        val transport =
            LegacyDfuTransport(scanner, FakeBleConnectionFactory(connection), address, Dispatchers.Unconfined)

        scanner.emitDevice(FakeBleDevice(dfuAddress))

        val result = transport.connectToDfuMode()

        assertTrue(result.isFailure)
        assertIs<LegacyDfuException.UnsupportedBootloader>(result.exceptionOrNull())
    }

    @Test
    fun `connectToDfuMode accepts modern DFU Version 8`() = runTest {
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        connection.service.enqueueRead(LEGACY_DFU_VERSION_UUID, byteArrayOf(0x08, 0x00))
        val transport =
            LegacyDfuTransport(scanner, FakeBleConnectionFactory(connection), address, Dispatchers.Unconfined)

        scanner.emitDevice(FakeBleDevice(dfuAddress))

        val result = transport.connectToDfuMode()

        assertTrue(result.isSuccess)
    }

    @Test
    fun `connectToDfuMode accepts missing DFU Version characteristic`() = runTest {
        // Default FakeBleService.read returns empty bytes when nothing is enqueued — treated as "absent".
        val scanner = FakeBleScanner()
        val connection = FakeBleConnection()
        val transport =
            LegacyDfuTransport(scanner, FakeBleConnectionFactory(connection), address, Dispatchers.Unconfined)

        scanner.emitDevice(FakeBleDevice(dfuAddress))

        val result = transport.connectToDfuMode()

        assertTrue(
            result.isSuccess,
            "Missing DFU Version should be treated as modern (proceed): ${result.exceptionOrNull()}",
        )
    }

    // -----------------------------------------------------------------------
    // Phase 3: transferInitPacket preflight
    // -----------------------------------------------------------------------

    @Test
    fun `transferInitPacket rejects oversized init packet looks like Secure-shaped dat`() = runTest {
        val transport =
            LegacyDfuTransport(
                FakeBleScanner(),
                FakeBleConnectionFactory(FakeBleConnection()),
                address,
                Dispatchers.Unconfined,
            )
        val oversized = ByteArray(LegacyDfuTransport.MAX_REASONABLE_LEGACY_INIT_SIZE + 1) { 0x42 }

        val result = transport.transferInitPacket(oversized)

        assertTrue(result.isFailure)
        assertIs<LegacyDfuException.InitPacketNotLegacy>(result.exceptionOrNull())
    }

    @Test
    fun `transferInitPacket accepts typical 14 byte legacy init`() = runTest {
        val transport =
            LegacyDfuTransport(
                FakeBleScanner(),
                FakeBleConnectionFactory(FakeBleConnection()),
                address,
                Dispatchers.Unconfined,
            )
        val init = ByteArray(14) { it.toByte() }

        val result = transport.transferInitPacket(init)

        assertTrue(result.isSuccess)
    }

    // -----------------------------------------------------------------------
    // Phase 4: transferFirmware happy path
    // -----------------------------------------------------------------------

    @Test
    fun `transferFirmware happy path writes correct opcode and packet sequence`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.HappyPath

        val init = ByteArray(14) { it.toByte() }
        val firmware = ByteArray(80) { (0xA0 + it).toByte() } // 4 packets at MTU=20

        env.transport.transferInitPacket(init).getOrThrow()
        val progress = mutableListOf<Float>()
        val result = env.transport.transferFirmware(firmware) { progress.add(it) }

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")

        // Control-point opcode order:
        // START_DFU, INIT_DFU_PARAMS_START, INIT_DFU_PARAMS_COMPLETE,
        // PACKET_RECEIPT_NOTIF_REQ, RECEIVE_FIRMWARE_IMAGE, VALIDATE, ACTIVATE_AND_RESET
        val cpOpcodes = env.controlPointWrites().map { it.data[0] }
        assertEquals(
            listOf(
                LegacyDfuOpcode.START_DFU,
                LegacyDfuOpcode.INIT_DFU_PARAMS,
                LegacyDfuOpcode.INIT_DFU_PARAMS,
                LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ,
                LegacyDfuOpcode.RECEIVE_FIRMWARE_IMAGE,
                LegacyDfuOpcode.VALIDATE,
                LegacyDfuOpcode.ACTIVATE_AND_RESET,
            ),
            cpOpcodes,
        )

        // First INIT_DFU_PARAMS sub-opcode is START (0x00), second is COMPLETE (0x01).
        val initParamsWrites = env.controlPointWrites().filter { it.data[0] == LegacyDfuOpcode.INIT_DFU_PARAMS }
        assertEquals(LegacyDfuOpcode.INIT_PARAMS_START, initParamsWrites[0].data[1])
        assertEquals(LegacyDfuOpcode.INIT_PARAMS_COMPLETE, initParamsWrites[1].data[1])

        // START_DFU includes APP image type byte.
        val startWrite = env.controlPointWrites().single { it.data[0] == LegacyDfuOpcode.START_DFU }
        assertContentEquals(byteArrayOf(LegacyDfuOpcode.START_DFU, LegacyDfuImageType.APPLICATION), startWrite.data)

        // Packet writes should contain: [12B image sizes] then [14B init in chunks] then [firmware in chunks].
        val packetBytes = env.packetWrites().flatMap { it.data.toList() }.toByteArray()
        // Image sizes payload (first 12 bytes): app size = firmware.size, others 0.
        val imageSizes = packetBytes.copyOfRange(0, 12)
        assertContentEquals(legacyImageSizesPayload(appSize = firmware.size), imageSizes)
        // Init follows.
        assertContentEquals(init, packetBytes.copyOfRange(12, 12 + init.size))
        // Firmware follows.
        assertContentEquals(firmware, packetBytes.copyOfRange(12 + init.size, packetBytes.size))

        // Final progress should be 1.0.
        assertEquals(1f, progress.last())

        // Packet writes use WITHOUT_RESPONSE.
        env.packetWrites().forEach { assertEquals(BleWriteType.WITHOUT_RESPONSE, it.writeType) }
    }

    // -----------------------------------------------------------------------
    // Phase 4: error paths
    // -----------------------------------------------------------------------

    @Test
    fun `transferFirmware fails with ProtocolError when device rejects START_DFU`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.RejectStart

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(40)) {}

        assertTrue(result.isFailure)
        val ex = assertIs<LegacyDfuException.ProtocolError>(result.exceptionOrNull())
        assertEquals(LegacyDfuOpcode.START_DFU, ex.requestOpcode)
        assertEquals(LegacyDfuStatus.NOT_SUPPORTED, ex.status)
    }

    @Test
    fun `transferFirmware fails with ProtocolError when device rejects INIT_DFU_PARAMS`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.RejectInit

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(40)) {}

        assertTrue(result.isFailure)
        val ex = assertIs<LegacyDfuException.ProtocolError>(result.exceptionOrNull())
        assertEquals(LegacyDfuOpcode.INIT_DFU_PARAMS, ex.requestOpcode)
        assertEquals(LegacyDfuStatus.OPERATION_FAILED, ex.status)
    }

    @Test
    fun `transferFirmware fails with ProtocolError when device rejects VALIDATE`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.RejectValidate

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(40)) {}

        assertTrue(result.isFailure)
        val ex = assertIs<LegacyDfuException.ProtocolError>(result.exceptionOrNull())
        assertEquals(LegacyDfuOpcode.VALIDATE, ex.requestOpcode)
        assertEquals(LegacyDfuStatus.CRC_ERROR, ex.status)
    }

    @Test
    fun `transferFirmware fails with PacketReceiptMismatch when device under-reports`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.PrnUnderReport

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        // Send (PRN_INTERVAL_PACKETS + 1) packets of 20 bytes — guarantees a PRN window fires
        // before the firmware completes, so the under-reported byte count surfaces as a mismatch.
        val firmwareSize = (LegacyDfuTransport.PRN_INTERVAL_PACKETS + 1) * 20
        val result = env.transport.transferFirmware(ByteArray(firmwareSize)) {}

        assertTrue(result.isFailure)
        assertIs<LegacyDfuException.PacketReceiptMismatch>(result.exceptionOrNull())
    }

    @Test
    fun `transferFirmware tolerates ACTIVATE_AND_RESET write failure - disconnect race`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.HappyPath
        // After VALIDATE response is sent, ACTIVATE write should be treated as success even if the device throws.
        env.responder.failOnActivateWrite = true

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(40)) {}

        assertTrue(
            result.isSuccess,
            "ACTIVATE write failure must be treated as success; got: ${result.exceptionOrNull()}",
        )
    }

    // -----------------------------------------------------------------------
    // Abort
    // -----------------------------------------------------------------------

    @Test
    fun `abort writes RESET opcode through control point`() = runTest {
        val connection = FakeBleConnection()
        val transport =
            LegacyDfuTransport(FakeBleScanner(), FakeBleConnectionFactory(connection), address, Dispatchers.Unconfined)

        transport.abort()

        val write = connection.service.writes.single()
        assertEquals(LegacyDfuUuids.CONTROL_POINT, write.characteristic.uuid)
        assertContentEquals(byteArrayOf(LegacyDfuOpcode.RESET), write.data)
        assertEquals(BleWriteType.WITH_RESPONSE, write.writeType)
    }

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------

    private class TestEnv(val transport: LegacyDfuTransport, val service: AutoRespondingLegacyService) {
        val responder = service.responder

        fun controlPointWrites(): List<FakeBleWrite> =
            service.delegate.writes.filter { it.characteristic.uuid == LegacyDfuUuids.CONTROL_POINT }

        fun packetWrites(): List<FakeBleWrite> =
            service.delegate.writes.filter { it.characteristic.uuid == LEGACY_DFU_PACKET_UUID }
    }

    private suspend fun createConnectedTransport(mtu: Int? = null): TestEnv {
        val scanner = FakeBleScanner()
        val fakeConnection = FakeBleConnection().apply { maxWriteValueLength = mtu }
        val autoService = AutoRespondingLegacyService(fakeConnection.service)
        val wrappedConnection = AutoRespondingBleConnection(fakeConnection, autoService)
        val factory =
            object : BleConnectionFactory {
                override fun create(scope: CoroutineScope, tag: String): BleConnection = wrappedConnection
            }
        val transport = LegacyDfuTransport(scanner, factory, address, Dispatchers.Unconfined)

        scanner.emitDevice(FakeBleDevice(dfuAddress))
        transport.connectToDfuMode().getOrThrow()
        return TestEnv(transport, autoService)
    }

    /**
     * Drives the simulated bootloader response stream. After each `write()` to control point or packet, this service
     * synthesises the appropriate notification(s) on the control-point characteristic so the transport's pending
     * `awaitResponse` / `awaitPacketReceipt` calls unblock.
     */
    private class AutoRespondingLegacyService(val delegate: FakeBleService) : BleService {
        val responder = LegacyResponder()

        override fun hasCharacteristic(c: BleCharacteristic) = delegate.hasCharacteristic(c)

        override fun observe(c: BleCharacteristic): Flow<ByteArray> = delegate.observe(c)

        override suspend fun read(c: BleCharacteristic): ByteArray = delegate.read(c)

        override fun preferredWriteType(c: BleCharacteristic): BleWriteType = delegate.preferredWriteType(c)

        override suspend fun write(c: BleCharacteristic, data: ByteArray, writeType: BleWriteType) {
            delegate.write(c, data, writeType)
            val response = responder.onWrite(c.uuid, data) ?: return
            response.forEach { delegate.emitNotification(LegacyDfuUuids.CONTROL_POINT, it) }
        }
    }

    /** What the simulated bootloader is meant to do for this test case. */
    enum class LegacyResponderScheme {
        HappyPath,
        RejectStart,
        RejectInit,
        RejectValidate,
        PrnUnderReport,
    }

    /**
     * Synthesises Legacy DFU notifications based on the transport's current write. Behaviour depends on [scheme]:
     * - `HappyPath`: returns Success for every control-point opcode that expects a response, plus accurate PRN receipts
     *   as packets accumulate.
     * - Reject* variants: return Failure with the indicated status for the targeted opcode.
     * - `PrnUnderReport`: at the first PRN window, report bytesReceived = actual − 1 to trigger PacketReceiptMismatch.
     *
     * Image-sizes write (12B on packet) is the trigger for the START_DFU response — matching the real protocol where
     * the device responds *after* it sees both the opcode and the size payload.
     */
    class LegacyResponder {
        var scheme: LegacyResponderScheme = LegacyResponderScheme.HappyPath
        var failOnActivateWrite: Boolean = false

        private var packetBytesReceived = 0L
        private var packetsSinceLastPrn = 0
        private var firmwareTransferStarted = false
        private var imageSizesWritten = false
        private var expectedFirmwareSize: Int = 0

        fun onWrite(uuid: kotlin.uuid.Uuid, data: ByteArray): List<ByteArray>? = when (uuid) {
            LegacyDfuUuids.CONTROL_POINT -> handleControlWrite(data)
            LEGACY_DFU_PACKET_UUID -> handlePacketWrite(data)
            else -> null
        }

        @Suppress("ReturnCount")
        private fun handleControlWrite(data: ByteArray): List<ByteArray>? {
            if (data.isEmpty()) return null
            val opcode = data[0]
            return when (opcode) {
                LegacyDfuOpcode.START_DFU -> null // response comes after image sizes packet write
                LegacyDfuOpcode.INIT_DFU_PARAMS -> {
                    if (data.size >= 2 && data[1] == LegacyDfuOpcode.INIT_PARAMS_COMPLETE) {
                        listOf(initResponse())
                    } else {
                        null
                    }
                }
                LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ -> null
                LegacyDfuOpcode.RECEIVE_FIRMWARE_IMAGE -> {
                    firmwareTransferStarted = true
                    null
                }
                LegacyDfuOpcode.VALIDATE -> listOf(validateResponse())
                LegacyDfuOpcode.ACTIVATE_AND_RESET -> {
                    if (failOnActivateWrite) {
                        throw RuntimeException("Simulated disconnect during ACTIVATE write")
                    }
                    null
                }
                LegacyDfuOpcode.RESET -> null
                else -> null
            }
        }

        private fun handlePacketWrite(data: ByteArray): List<ByteArray>? {
            // First packet write is the 12-byte image sizes payload (after START_DFU).
            if (!imageSizesWritten) {
                imageSizesWritten = true
                // Parse appSize from bytes 8..11 (LE u32).
                if (data.size >= 12) {
                    expectedFirmwareSize =
                        (data[8].toInt() and 0xFF) or
                        ((data[9].toInt() and 0xFF) shl 8) or
                        ((data[10].toInt() and 0xFF) shl 16) or
                        ((data[11].toInt() and 0xFF) shl 24)
                }
                return listOf(startResponse())
            }

            if (firmwareTransferStarted) {
                packetBytesReceived += data.size
                packetsSinceLastPrn++
                val responses = mutableListOf<ByteArray>()
                val firmwareDone = packetBytesReceived >= expectedFirmwareSize
                if (packetsSinceLastPrn >= LegacyDfuTransport.PRN_INTERVAL_PACKETS && !firmwareDone) {
                    packetsSinceLastPrn = 0
                    val reported =
                        if (scheme == LegacyResponderScheme.PrnUnderReport) {
                            packetBytesReceived - 1
                        } else {
                            packetBytesReceived
                        }
                    responses += packetReceipt(reported)
                }
                if (firmwareDone) {
                    responses += success(LegacyDfuOpcode.RECEIVE_FIRMWARE_IMAGE)
                }
                return responses.takeIf { it.isNotEmpty() }
            }
            // Init-packet writes between START and COMPLETE: silent.
            return null
        }

        private fun startResponse(): ByteArray = when (scheme) {
            LegacyResponderScheme.RejectStart ->
                byteArrayOf(LegacyDfuOpcode.RESPONSE_CODE, LegacyDfuOpcode.START_DFU, LegacyDfuStatus.NOT_SUPPORTED)
            else -> success(LegacyDfuOpcode.START_DFU)
        }

        private fun initResponse(): ByteArray = when (scheme) {
            LegacyResponderScheme.RejectInit ->
                byteArrayOf(
                    LegacyDfuOpcode.RESPONSE_CODE,
                    LegacyDfuOpcode.INIT_DFU_PARAMS,
                    LegacyDfuStatus.OPERATION_FAILED,
                )
            else -> success(LegacyDfuOpcode.INIT_DFU_PARAMS)
        }

        private fun validateResponse(): ByteArray = when (scheme) {
            LegacyResponderScheme.RejectValidate ->
                byteArrayOf(LegacyDfuOpcode.RESPONSE_CODE, LegacyDfuOpcode.VALIDATE, LegacyDfuStatus.CRC_ERROR)
            else -> success(LegacyDfuOpcode.VALIDATE)
        }

        private fun packetReceipt(bytesReceived: Long): ByteArray = byteArrayOf(
            LegacyDfuOpcode.PACKET_RECEIPT,
            (bytesReceived and 0xFF).toByte(),
            ((bytesReceived ushr 8) and 0xFF).toByte(),
            ((bytesReceived ushr 16) and 0xFF).toByte(),
            ((bytesReceived ushr 24) and 0xFF).toByte(),
        )

        private fun success(opcode: Byte): ByteArray =
            byteArrayOf(LegacyDfuOpcode.RESPONSE_CODE, opcode, LegacyDfuStatus.SUCCESS)
    }

    /** BleConnection wrapper that swaps in the auto-responding service for `profile()` calls. */
    private class AutoRespondingBleConnection(
        private val delegate: FakeBleConnection,
        val autoService: AutoRespondingLegacyService,
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
        ): T = CoroutineScope(Dispatchers.Unconfined).setup(autoService)

        override fun maximumWriteValueLength(writeType: BleWriteType): Int? =
            delegate.maximumWriteValueLength(writeType)
    }
}
