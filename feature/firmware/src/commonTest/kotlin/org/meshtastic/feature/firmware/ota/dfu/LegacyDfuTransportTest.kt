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
@file:Suppress("MagicNumber", "LargeClass", "TooManyFunctions")

package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
import kotlin.test.assertFailsWith
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

    @Test
    fun `transferFirmware uses high-MTU packets when the link negotiates a larger MTU`() = runTest {
        val env = createConnectedTransport(mtu = 244)
        env.responder.scheme = LegacyResponderScheme.HappyPath

        env.transport.transferInitPacket(ByteArray(14) { it.toByte() }).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(600) { it.toByte() }) {}

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")
        // Firmware streams in 244-byte packets (negotiated MTU − 3) instead of the 20-byte default — the ~12× win.
        assertEquals(244, env.packetWrites().maxOf { it.data.size })
    }

    @Test
    fun `transferFirmware floors the packet size to a 4-byte boundary`() = runTest {
        val env = createConnectedTransport(mtu = 242) // not a multiple of 4
        env.responder.scheme = LegacyResponderScheme.HappyPath

        env.transport.transferInitPacket(ByteArray(14) { it.toByte() }).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(600) { it.toByte() }) {}

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")
        // 242 → 240: the Adafruit DFU data path rejects writes whose length isn't a multiple of 4.
        assertEquals(240, env.packetWrites().maxOf { it.data.size })
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
    fun `transferFirmware maps START_DFU INVALID_STATE to StaleSessionReset for recovery restart`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.RejectStartInvalidState

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(40)) {}

        assertTrue(result.isFailure)
        assertIs<LegacyDfuException.StaleSessionReset>(result.exceptionOrNull())
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
        val firmwareSize = (PRN_INTERVAL_PACKETS + 1) * 20
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

    private class TestEnv(
        val transport: LegacyDfuTransport,
        val service: AutoRespondingLegacyService,
        val connection: FakeBleConnection,
    ) {
        val responder = service.responder

        fun controlPointWrites(): List<FakeBleWrite> =
            service.delegate.writes.filter { it.characteristic.uuid == LegacyDfuUuids.CONTROL_POINT }

        fun packetWrites(): List<FakeBleWrite> =
            service.delegate.writes.filter { it.characteristic.uuid == LEGACY_DFU_PACKET_UUID }
    }

    private suspend fun createConnectedTransport(
        mtu: Int? = null,
        profile: LegacyDfuStreamProfile = LegacyDfuStreamProfile.NORMAL,
    ): TestEnv {
        val scanner = FakeBleScanner()
        val fakeConnection = FakeBleConnection().apply { maxWriteValueLength = mtu }
        val autoService = AutoRespondingLegacyService(fakeConnection.service)
        val wrappedConnection = AutoRespondingBleConnection(fakeConnection, autoService)
        val factory =
            object : BleConnectionFactory {
                override fun create(scope: CoroutineScope, tag: String): BleConnection = wrappedConnection
            }
        val transport = LegacyDfuTransport(scanner, factory, address, Dispatchers.Unconfined, profile)

        scanner.emitDevice(FakeBleDevice(dfuAddress))
        transport.connectToDfuMode().getOrThrow()
        return TestEnv(transport, autoService, fakeConnection)
    }

    /**
     * Drives the simulated bootloader response stream. After each `write()` to control point or packet, this service
     * synthesises the appropriate notification(s) on the control-point characteristic so the transport's pending
     * `awaitResponse` / `awaitPacketReceiptDuringStream` calls unblock.
     */
    private class AutoRespondingLegacyService(val delegate: FakeBleService) : BleService {
        val responder = LegacyResponder()

        /**
         * When `true`, the next `write()` to the Legacy Control Point characteristic suspends forever (via
         * [awaitCancellation]) instead of completing. Used to prove the bounded abort returns within
         * [LegacyDfuTransport.RESET_WRITE_TIMEOUT] when the device never ACKs the RESET.
         */
        var hangOnControlPointWrites: Boolean = false

        /**
         * When `true`, the next `write()` to the Legacy Control Point characteristic throws an [AssertionError]. Used
         * to prove [LegacyDfuTransport.abort] (which catches [Exception], not [Throwable]) does not swallow [Error]
         * subtypes.
         */
        var throwErrorOnControlPointWrites: Boolean = false

        /**
         * When `true`, the next `write()` to the Legacy Control Point characteristic throws a [RuntimeException]. Used
         * to prove [LegacyDfuTransport.abort] swallows operational [Exception] subtypes (best-effort / non-fatal).
         */
        var throwExceptionOnControlPointWrites: Boolean = false

        override fun hasCharacteristic(c: BleCharacteristic) = delegate.hasCharacteristic(c)

        override fun observe(c: BleCharacteristic): Flow<ByteArray> = delegate.observe(c)

        override suspend fun read(c: BleCharacteristic): ByteArray = delegate.read(c)

        override fun preferredWriteType(c: BleCharacteristic): BleWriteType = delegate.preferredWriteType(c)

        override suspend fun write(c: BleCharacteristic, data: ByteArray, writeType: BleWriteType) {
            if (throwErrorOnControlPointWrites && c.uuid == LegacyDfuUuids.CONTROL_POINT) {
                throw AssertionError("Simulated assertion failure during control point write")
            }
            if (throwExceptionOnControlPointWrites && c.uuid == LegacyDfuUuids.CONTROL_POINT) {
                throw RuntimeException("Simulated link failure during control point write")
            }
            if (hangOnControlPointWrites && c.uuid == LegacyDfuUuids.CONTROL_POINT) {
                awaitCancellation()
            }
            delegate.write(c, data, writeType)
            val response = responder.onWrite(c.uuid, data) ?: return
            response.forEach { delegate.emitNotification(LegacyDfuUuids.CONTROL_POINT, it) }
        }
    }

    /** What the simulated bootloader is meant to do for this test case. */
    enum class LegacyResponderScheme {
        HappyPath,
        RejectStart,
        RejectStartInvalidState,
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

        /**
         * Optional suspend hook invoked after each firmware-packet write with the cumulative byte count, while
         * [AutoRespondingLegacyService.write] is still in progress. Tests use this to hold a write in flight (e.g. via
         * [awaitCancellation]) while another coroutine changes connection state to simulate a mid-stream link drop at a
         * deterministic offset.
         */
        var onFirmwarePacketWrite: (suspend (bytesReceived: Long) -> Unit)? = null

        /**
         * When `true`, packet-receipt notifications are NOT emitted by the simulated bootloader. Used to suspend the
         * transport inside the PRN wait so a test can drive a disconnect while the wait is in flight.
         */
        var suppressPrnNotifications: Boolean = false

        private var packetBytesReceived = 0L
        private var packetsSinceLastPrn = 0
        private var firmwareTransferStarted = false
        private var imageSizesWritten = false
        private var expectedFirmwareSize: Int = 0

        /**
         * Effective PRN cadence. Defaults to the NORMAL profile value; updated from the `PACKET_RECEIPT_NOTIF_REQ`
         * control write so the responder matches whatever interval the transport requested (NORMAL=10, RECOVERY=5).
         */
        private var prnInterval: Int = PRN_INTERVAL_PACKETS

        suspend fun onWrite(uuid: kotlin.uuid.Uuid, data: ByteArray): List<ByteArray>? = when (uuid) {
            LegacyDfuUuids.CONTROL_POINT -> handleControlWrite(data)
            LEGACY_DFU_PACKET_UUID -> handlePacketWrite(data)
            else -> null
        }

        @Suppress("ReturnCount")
        private fun handleControlWrite(data: ByteArray): List<ByteArray>? {
            if (data.isEmpty()) return null
            val opcode = data[0]
            return when (opcode) {
                LegacyDfuOpcode.START_DFU -> null

                // response comes after image sizes packet write
                LegacyDfuOpcode.INIT_DFU_PARAMS -> {
                    if (data.size >= 2 && data[1] == LegacyDfuOpcode.INIT_PARAMS_COMPLETE) {
                        listOf(initResponse())
                    } else {
                        null
                    }
                }

                LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ -> {
                    if (data.size >= 3) {
                        // Parse uint16-LE PRN value the transport just wrote and follow it for receipt cadence.
                        prnInterval = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                    }
                    null
                }

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

        private suspend fun handlePacketWrite(data: ByteArray): List<ByteArray>? {
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
                onFirmwarePacketWrite?.invoke(packetBytesReceived)
                val responses = mutableListOf<ByteArray>()
                val firmwareDone = packetBytesReceived >= expectedFirmwareSize
                // Use the cadence the transport actually requested via PACKET_RECEIPT_NOTIF_REQ so a RECOVERY
                // (prnInterval=5) upload receives receipts at the smaller interval.
                if (packetsSinceLastPrn >= prnInterval && !firmwareDone) {
                    packetsSinceLastPrn = 0
                    if (!suppressPrnNotifications) {
                        val reported =
                            if (scheme == LegacyResponderScheme.PrnUnderReport) {
                                packetBytesReceived - 1
                            } else {
                                packetBytesReceived
                            }
                        responses += packetReceipt(reported)
                    }
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

            LegacyResponderScheme.RejectStartInvalidState ->
                byteArrayOf(LegacyDfuOpcode.RESPONSE_CODE, LegacyDfuOpcode.START_DFU, LegacyDfuStatus.INVALID_STATE)

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

    // -----------------------------------------------------------------------
    // Stream profile selection
    // -----------------------------------------------------------------------

    @Test
    fun `NORMAL profile writes PRN request value 10`() = runTest {
        val env = createConnectedTransport(profile = LegacyDfuStreamProfile.NORMAL)
        env.responder.scheme = LegacyResponderScheme.HappyPath

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        env.transport.transferFirmware(ByteArray(40)) {}

        val prnWrite = env.controlPointWrites().single { it.data[0] == LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ }
        val requested = (prnWrite.data[1].toInt() and 0xFF) or ((prnWrite.data[2].toInt() and 0xFF) shl 8)
        assertEquals(PRN_INTERVAL_PACKETS, requested)
    }

    @Test
    fun `RECOVERY profile writes PRN request value 5`() = runTest {
        val env = createConnectedTransport(profile = LegacyDfuStreamProfile.RECOVERY)
        env.responder.scheme = LegacyResponderScheme.HappyPath

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        env.transport.transferFirmware(ByteArray(40)) {}

        val prnWrite = env.controlPointWrites().single { it.data[0] == LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ }
        val requested = (prnWrite.data[1].toInt() and 0xFF) or ((prnWrite.data[2].toInt() and 0xFF) shl 8)
        assertEquals(RECOVERY_PRN_INTERVAL_PACKETS, requested)
    }

    @Test
    fun `RECOVERY profile awaits packet receipts at the smaller interval`() = runTest {
        // RECOVERY prnInterval = 5, so a 100-byte firmware at 20-byte packets fires a PRN exactly once at byte 100
        // (5 packets × 20B) — but offset == firmware.size at that point means the `offset < firmware.size` gate
        // suppresses the wait. Use 110 bytes (would need 6 packets at NORMAL=10 to hit the first PRN) so the
        // first PRN at 100B fires under RECOVERY but never fires under NORMAL within the 110B upload.
        val env = createConnectedTransport(profile = LegacyDfuStreamProfile.RECOVERY)
        env.responder.scheme = LegacyResponderScheme.HappyPath

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val progress = mutableListOf<Float>()
        val result = env.transport.transferFirmware(ByteArray(110)) { progress += it }

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")
        // A PRN fires at offset 100 (5 packets × 20B under RECOVERY prnInterval=5); progress must record it during
        // streaming, then the final 1f at completion. This proves the PRN was consumed mid-stream, not just drained
        // by the final response wait.
        assertEquals(listOf(100f / 110f, 1f), progress)
    }

    @Test
    fun `NORMAL profile does not await a PRN for a sub-interval upload`() = runTest {
        // 110-byte firmware at 20-byte packets = 6 packets. NORMAL prnInterval = 10, so no PRN is due before the
        // upload completes — the auto-responder must NOT emit a PRN, and the transport must NOT call
        // awaitPacketReceiptDuringStream.
        val env = createConnectedTransport(profile = LegacyDfuStreamProfile.NORMAL)
        env.responder.scheme = LegacyResponderScheme.HappyPath

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()
        val result = env.transport.transferFirmware(ByteArray(110)) {}

        assertTrue(result.isSuccess, "transferFirmware failed: ${result.exceptionOrNull()}")
    }

    // -----------------------------------------------------------------------
    // Mid-stream disconnect
    // -----------------------------------------------------------------------

    @Test
    fun `mid-stream disconnect returns typed MidStreamDisconnect with correct byte counts`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.HappyPath

        // Hold the first firmware-packet write in flight so the transfer cannot run through all 200 bytes before
        // the test injects the disconnect. Production streamOffset has already published 20 before the write, so
        // the tripwire observer will read bytesSent == 20 when the link drops.
        val firstPacketWriteInFlight = CompletableDeferred<Unit>()
        env.responder.onFirmwarePacketWrite = { bytes ->
            if (bytes >= 20L) {
                firstPacketWriteInFlight.complete(Unit)
                awaitCancellation()
            }
        }

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()

        val transferDeferred = async { env.transport.transferFirmware(ByteArray(200)) {} }
        firstPacketWriteInFlight.await()
        runCurrent()

        assertTrue(
            !transferDeferred.isCompleted,
            "transfer must remain suspended inside the first firmware-packet write",
        )

        env.connection.simulateRemoteDisconnect()
        runCurrent()

        val result = transferDeferred.await()
        val error =
            assertIs<LegacyDfuException.MidStreamDisconnect>(
                result.exceptionOrNull(),
                "expected the outer stream tripwire to classify the disconnect while the first write was in flight",
            )
        // First packet write was in flight (streamOffset published as 20 before service.write), so bytesSent == 20.
        assertEquals(20, error.bytesSent)
        assertEquals(200, error.totalBytes)
        // No PRN was received before the drop (first PRN would be at byte 200), so lastConfirmedBytes is -1.
        assertEquals(-1, error.lastConfirmedBytes)
        assertTrue(error.connectionState.isNotBlank())
    }

    /**
     * A drop during a stream PRN wait must be classified by the OUTER stream-level tripwire as
     * [LegacyDfuException.MidStreamDisconnect] — NOT as a generic handshake-style [DfuException.ConnectionFailed]. The
     * stream PRN wait uses [awaitPacketReceiptDuringStream], which deliberately does NOT install its own disconnect
     * tripwire, so the outer watcher is the sole classifier.
     */
    @Test
    fun `stream-level tripwire classifies a drop during a PRN wait as MidStreamDisconnect`() = runTest {
        val env = createConnectedTransport()
        env.responder.scheme = LegacyResponderScheme.HappyPath
        // Suppress PRNs so the transport suspends inside awaitPacketReceiptDuringStream at the first PRN boundary.
        env.responder.suppressPrnNotifications = true

        env.transport.transferInitPacket(ByteArray(14)).getOrThrow()

        // First NORMAL PRN boundary = prnInterval (10) × default packet size (20B, MTU not negotiated) = byte 200.
        // The responder fires this hook synchronously after accounting the 10th packet write, so completing the
        // deferred proves the stream has written bytes 0..200 and is about to enter awaitPacketReceiptDuringStream —
        // long before virtual time can reach the 30s COMMAND_TIMEOUT that would otherwise mask the tripwire path.
        val reachedPrnBoundary = CompletableDeferred<Unit>()
        env.responder.onFirmwarePacketWrite = { bytes -> if (bytes >= 200L) reachedPrnBoundary.complete(Unit) }

        // Run the transfer asynchronously so the test body can synchronize the disconnect to the suspended PRN wait.
        val transferDeferred = async { env.transport.transferFirmware(ByteArray(220)) {} }
        // Wait for the 10th packet write to land, then flush dispatched continuations so the stream coroutine reaches
        // the suspended receive() inside awaitPacketReceiptDuringStream — without advancing virtual time.
        reachedPrnBoundary.await()
        runCurrent()

        // Simulate remote disconnect while the transport is suspended in awaitPacketReceiptDuringStream.
        env.connection.simulateRemoteDisconnect()
        runCurrent()

        val result = transferDeferred.await()
        assertTrue(result.isFailure)
        val ex = assertIs<LegacyDfuException.MidStreamDisconnect>(result.exceptionOrNull())
        // Must be the typed MidStreamDisconnect from the outer stream-level tripwire — NOT the generic
        // ConnectionFailed that an inner handshake-style tripwire would have produced, and NOT a local Timeout.
        assertEquals(200, ex.bytesSent)
        assertEquals(220, ex.totalBytes)
    }

    // -----------------------------------------------------------------------
    // Bounded RESET (abort)
    // -----------------------------------------------------------------------

    @Test
    fun `RESET is written WITH_RESPONSE`() = runTest {
        val env = createConnectedTransport()

        env.transport.abort()

        val resetWrite = env.controlPointWrites().single { it.data.isNotEmpty() && it.data[0] == LegacyDfuOpcode.RESET }
        assertEquals(BleWriteType.WITH_RESPONSE, resetWrite.writeType)
    }

    @Test
    fun `acknowledged RESET completes and logs acknowledgement`() = runTest {
        val env = createConnectedTransport()

        env.transport.abort()

        // An acknowledged RESET means the write completed within the timeout; abort must return normally.
        val resetWrite = env.controlPointWrites().single { it.data.isNotEmpty() && it.data[0] == LegacyDfuOpcode.RESET }
        assertTrue(resetWrite.data.size == 1, "RESET payload is a single opcode byte")
    }

    @Test
    fun `abort returns within RESET_WRITE_TIMEOUT when the RESET write hangs`() = runTest {
        val env = createConnectedTransport()
        env.service.hangOnControlPointWrites = true

        // Run abort on the test dispatcher so virtual time advances past RESET_WRITE_TIMEOUT. The hanging write
        // never completes; withTimeoutOrNull inside abort must return null and abort must return normally.
        val abortJob = async { env.transport.abort() }
        advanceUntilIdle()
        abortJob.await()

        // No RESET write should have been recorded on the delegate — the hang prevented the write from reaching
        // FakeBleService.write, so an "acknowledged" log would be a lie.
        val resetWrites =
            env.controlPointWrites().filter { it.data.isNotEmpty() && it.data[0] == LegacyDfuOpcode.RESET }
        assertTrue(resetWrites.isEmpty(), "RESET write should not have been recorded when the link hung")
    }

    @Test
    fun `abort propagates parent cancellation rather than reporting success`() = runTest {
        val env = createConnectedTransport()
        env.service.hangOnControlPointWrites = true

        // parentJob stands in for the caller's scope; cancelling it must propagate through withTimeoutOrNull
        // (which only swallows its own TimeoutCancellationException, not parent cancellation) and out of abort.
        val parentJob = Job()
        val abortDeferred = async(parentJob) { env.transport.abort() }

        // Let abort reach the hanging RESET write.
        runCurrent()
        parentJob.cancel()
        assertFailsWith<CancellationException> { abortDeferred.await() }
    }

    @Test
    fun `abort does not swallow Error subtypes from the RESET write`() = runTest {
        val env = createConnectedTransport()
        env.service.throwErrorOnControlPointWrites = true

        // abort catches Exception (operational link failures) but lets Error subtypes propagate.
        assertFailsWith<AssertionError> { env.transport.abort() }
    }

    @Test
    fun `abort swallows operational Exception from the RESET write`() = runTest {
        val env = createConnectedTransport()
        env.service.throwExceptionOnControlPointWrites = true

        // A RuntimeException from the control-point write is an operational Exception — abort must catch it
        // (best-effort, non-fatal) and return normally without rethrowing.
        env.transport.abort()
    }

    @Test
    fun `close completes after abort`() = runTest {
        val env = createConnectedTransport()

        env.transport.abort()
        // close() must complete normally regardless of what abort did — mirrors the handler's
        // try { abort() } finally { close() } teardown contract.
        env.transport.close()
    }
}
