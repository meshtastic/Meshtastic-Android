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
@file:Suppress(
    "MagicNumber",
    "TooManyFunctions",
    "ThrowsCount",
    "ReturnCount",
    "SwallowedException",
    "TooGenericExceptionCaught",
)

package org.meshtastic.feature.firmware.ota.dfu

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.DEFAULT_BLE_WRITE_VALUE_LENGTH
import org.meshtastic.feature.firmware.ota.calculateMacPlusOne
import org.meshtastic.feature.firmware.ota.scanForBleDevice

/**
 * Kable-based transport for the Nordic Secure DFU (Secure DFU over BLE) protocol.
 *
 * Usage:
 * 1. [triggerButtonlessDfu] — connect to the device in normal mode and trigger reboot into DFU mode.
 * 2. [connectToDfuMode] — scan for the device in DFU mode and establish the DFU GATT session.
 * 3. [transferInitPacket] / [transferFirmware] — send .dat then .bin.
 * 4. [abort] — send ABORT to the device before closing (on cancellation or error).
 * 5. [close] — tear down the connection.
 */
class SecureDfuTransport(
    private val scanner: BleScanner,
    connectionFactory: BleConnectionFactory,
    private val address: String,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val bleConnection = connectionFactory.create(transportScope, "Secure DFU")

    /** Receives binary notifications from the Control Point characteristic. */
    private val notificationChannel = Channel<ByteArray>(Channel.UNLIMITED)

    // ---------------------------------------------------------------------------
    // Phase 1: Buttonless DFU trigger (normal-mode device)
    // ---------------------------------------------------------------------------

    /**
     * Connects to the device running normal firmware and writes to the Buttonless DFU characteristic so the bootloader
     * takes over. The device disconnects and reboots.
     *
     * The caller must have already released the mesh-service BLE connection before calling this.
     */
    suspend fun triggerButtonlessDfu(): Result<Unit> = runCatching {
        Logger.i { "DFU: Scanning for device $address to trigger buttonless DFU..." }

        val device =
            scanForDevice { d -> d.address == address }
                ?: throw DfuException.ConnectionFailed("Device $address not found for buttonless DFU trigger")

        Logger.i { "DFU: Connecting to $address to trigger buttonless DFU..." }
        bleConnection.connectAndAwait(device, CONNECT_TIMEOUT_MS)

        bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
            val buttonlessChar = service.characteristic(SecureDfuUuids.BUTTONLESS_NO_BONDS)
            Logger.i { "DFU: Writing buttonless DFU trigger..." }
            service.write(buttonlessChar, byteArrayOf(0x01), BleWriteType.WITH_RESPONSE)
        }

        // Device will disconnect and reboot — expected, not an error.
        Logger.i { "DFU: Buttonless DFU triggered, device is rebooting..." }
        bleConnection.disconnect()
    }

    // ---------------------------------------------------------------------------
    // Phase 2: Connect to device in DFU mode
    // ---------------------------------------------------------------------------

    /**
     * Scans for the device in DFU mode (address or address+1) and establishes the GATT connection, enabling
     * notifications on the Control Point.
     */
    suspend fun connectToDfuMode(): Result<Unit> = runCatching {
        val dfuAddress = calculateMacPlusOne(address)
        val targetAddresses = setOf(address, dfuAddress)
        Logger.i { "DFU: Scanning for DFU mode device at $targetAddresses..." }

        val device =
            scanForDevice { d -> d.address in targetAddresses }
                ?: throw DfuException.ConnectionFailed("DFU mode device not found. Tried: $targetAddresses")

        Logger.i { "DFU: Found DFU mode device at ${device.address}, connecting..." }

        bleConnection.connectionState.onEach { Logger.d { "DFU: Connection state → $it" } }.launchIn(transportScope)

        val connected = bleConnection.connectAndAwait(device, CONNECT_TIMEOUT_MS)
        if (connected is BleConnectionState.Disconnected) {
            throw DfuException.ConnectionFailed("Failed to connect to DFU device ${device.address}")
        }

        bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
            val controlChar = service.characteristic(SecureDfuUuids.CONTROL_POINT)

            // Subscribe to Control Point notifications before issuing any commands.
            // launchIn(this) uses connectionScope so the subscription persists beyond this block.
            val subscribed = CompletableDeferred<Unit>()
            service
                .observe(controlChar)
                .onEach { bytes ->
                    if (!subscribed.isCompleted) {
                        Logger.d { "DFU: Control Point subscribed" }
                        subscribed.complete(Unit)
                    }
                    notificationChannel.trySend(bytes)
                }
                .catch { e ->
                    if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    Logger.e(e) { "DFU: Control Point notification error" }
                }
                .launchIn(this)

            delay(SUBSCRIPTION_SETTLE_MS)
            if (!subscribed.isCompleted) subscribed.complete(Unit)
            subscribed.await()

            Logger.i { "DFU: Connected and ready (${device.address})" }
        }
    }

    // ---------------------------------------------------------------------------
    // Phase 3: Init packet transfer (.dat)
    // ---------------------------------------------------------------------------

    /**
     * Sends the DFU init packet (`.dat` file). The device verifies this against the bootloader's security requirements
     * before accepting firmware.
     */
    suspend fun transferInitPacket(initPacket: ByteArray): Result<Unit> = runCatching {
        Logger.i { "DFU: Transferring init packet (${initPacket.size} bytes)..." }
        setPrn(PRN_INTERVAL)
        transferObjectWithRetry(DfuObjectType.COMMAND, initPacket, onProgress = null)
        Logger.i { "DFU: Init packet transferred and executed." }
    }

    // ---------------------------------------------------------------------------
    // Phase 4: Firmware transfer (.bin)
    // ---------------------------------------------------------------------------

    /**
     * Sends the firmware binary (`.bin` file) using the DFU object-transfer protocol.
     *
     * The binary is split into objects sized by the device's reported maximum object size. After each object the device
     * confirms the running CRC-32. On success, the bootloader validates the full image and reboots into the new
     * firmware.
     *
     * @param firmware Raw bytes of the `.bin` file.
     * @param onProgress Callback receiving progress in [0.0, 1.0].
     */
    suspend fun transferFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit): Result<Unit> = runCatching {
        Logger.i { "DFU: Transferring firmware (${firmware.size} bytes)..." }
        setPrn(PRN_INTERVAL)
        transferObjectWithRetry(DfuObjectType.DATA, firmware, onProgress)
        Logger.i { "DFU: Firmware transferred and executed." }
    }

    // ---------------------------------------------------------------------------
    // Abort & teardown
    // ---------------------------------------------------------------------------

    /**
     * Sends the ABORT opcode to the device, instructing it to discard any in-progress transfer and return to an idle
     * state. Best-effort — never throws.
     *
     * Call this before [close] when cancelling or recovering from an error so the device doesn't need a power cycle to
     * accept a fresh DFU session.
     */
    suspend fun abort() {
        runCatching {
            bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
                val controlChar = service.characteristic(SecureDfuUuids.CONTROL_POINT)
                service.write(controlChar, byteArrayOf(DfuOpcode.ABORT), BleWriteType.WITH_RESPONSE)
            }
            Logger.i { "DFU: Abort sent to device." }
        }
            .onFailure { Logger.w(it) { "DFU: Failed to send abort (device may have disconnected)" } }
    }

    suspend fun close() {
        runCatching { bleConnection.disconnect() }.onFailure { Logger.w(it) { "DFU: Error during disconnect" } }
        transportScope.cancel()
    }

    // ---------------------------------------------------------------------------
    // Object-transfer protocol (shared by init packet and firmware)
    // ---------------------------------------------------------------------------

    /**
     * Wraps [transferObject] with per-object retry logic. On retry, [transferObject] will re-SELECT the object type and
     * resume from the device's reported offset if the CRC matches.
     */
    private suspend fun transferObjectWithRetry(
        objectType: Byte,
        data: ByteArray,
        onProgress: (suspend (Float) -> Unit)?,
    ) {
        var lastError: Throwable? = null
        repeat(OBJECT_RETRY_COUNT) { attempt ->
            try {
                transferObject(objectType, data, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                Logger.w(e) { "DFU: Object transfer failed (attempt ${attempt + 1}/$OBJECT_RETRY_COUNT): ${e.message}" }
                if (attempt < OBJECT_RETRY_COUNT - 1) delay(RETRY_DELAY_MS)
            }
        }
        throw lastError ?: DfuException.TransferFailed("Object transfer failed after $OBJECT_RETRY_COUNT attempts")
    }

    private suspend fun transferObject(objectType: Byte, data: ByteArray, onProgress: (suspend (Float) -> Unit)?) {
        val selectResult = sendSelect(objectType)
        val maxObjectSize = selectResult.maxSize.takeIf { it > 0 } ?: DEFAULT_MAX_OBJECT_SIZE
        val totalBytes = data.size
        var offset = 0

        // If the device already has a partial object with a matching CRC, resume.
        if (selectResult.offset in 1..totalBytes) {
            val expectedCrc = DfuCrc32.calculate(data, length = selectResult.offset)
            if (expectedCrc == selectResult.crc32) {
                Logger.i { "DFU: Resuming at offset ${selectResult.offset} (CRC match)" }
                offset = selectResult.offset
            } else {
                Logger.w { "DFU: Offset ${selectResult.offset} CRC mismatch — restarting from 0" }
            }
        }

        while (offset < totalBytes) {
            val objectSize = minOf(maxObjectSize, totalBytes - offset)
            sendCreate(objectType, objectSize)

            val objectEnd = offset + objectSize
            writePackets(data, offset, objectEnd)

            val checksumResult = sendCalculateChecksum()
            val expectedCrc = DfuCrc32.calculate(data, length = objectEnd)

            if (checksumResult.offset != objectEnd) {
                throw DfuException.TransferFailed(
                    "Offset mismatch after object: expected $objectEnd, got ${checksumResult.offset}",
                )
            }
            if (checksumResult.crc32 != expectedCrc) {
                throw DfuException.ChecksumMismatch(expected = expectedCrc, actual = checksumResult.crc32)
            }

            sendExecute()
            offset = objectEnd
            onProgress?.invoke(offset.toFloat() / totalBytes)
            Logger.d { "DFU: Object complete. Progress: $offset/$totalBytes" }
        }
    }

    // ---------------------------------------------------------------------------
    // Low-level GATT helpers
    // ---------------------------------------------------------------------------

    /**
     * Writes [data] from [from] to [until] as MTU-sized packets WITHOUT_RESPONSE.
     *
     * PRN flow control: every [PRN_INTERVAL] packets we await a ChecksumResult notification from the device and
     * validate the running CRC-32. This prevents the device's receive buffer from overflowing and detects corruption
     * early.
     */
    private suspend fun writePackets(data: ByteArray, from: Int, until: Int) {
        val mtu = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE) ?: DEFAULT_BLE_WRITE_VALUE_LENGTH
        var packetsSincePrn = 0

        bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
            val packetChar = service.characteristic(SecureDfuUuids.PACKET)
            var pos = from

            while (pos < until) {
                val chunkEnd = minOf(pos + mtu, until)
                service.write(packetChar, data.copyOfRange(pos, chunkEnd), BleWriteType.WITHOUT_RESPONSE)
                pos = chunkEnd
                packetsSincePrn++

                // Wait for the device's PRN receipt notification, then validate CRC.
                // Skip the wait on the last packet — the final CALCULATE_CHECKSUM covers it.
                if (PRN_INTERVAL > 0 && packetsSincePrn >= PRN_INTERVAL && pos < until) {
                    val response = awaitNotification(COMMAND_TIMEOUT_MS)
                    if (response is DfuResponse.ChecksumResult) {
                        val expectedCrc = DfuCrc32.calculate(data, length = pos)
                        if (response.offset != pos || response.crc32 != expectedCrc) {
                            throw DfuException.ChecksumMismatch(expected = expectedCrc, actual = response.crc32)
                        }
                        Logger.d { "DFU: PRN checksum OK at offset $pos" }
                    }
                    packetsSincePrn = 0
                }
            }
        }
    }

    private suspend fun sendCommand(payload: ByteArray): DfuResponse {
        bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
            val controlChar = service.characteristic(SecureDfuUuids.CONTROL_POINT)
            service.write(controlChar, payload, BleWriteType.WITH_RESPONSE)
        }
        return awaitNotification(COMMAND_TIMEOUT_MS)
    }

    private suspend fun setPrn(value: Int) {
        val payload = byteArrayOf(DfuOpcode.SET_PRN) + intToLeBytes(value).copyOfRange(0, 2)
        val response = sendCommand(payload)
        response.requireSuccess(DfuOpcode.SET_PRN)
        Logger.d { "DFU: PRN set to $value" }
    }

    private suspend fun sendSelect(objectType: Byte): DfuResponse.SelectResult {
        val response = sendCommand(byteArrayOf(DfuOpcode.SELECT, objectType))
        return when (response) {
            is DfuResponse.SelectResult -> response
            is DfuResponse.Failure -> throw DfuException.ProtocolError(DfuOpcode.SELECT, response.resultCode)
            else -> throw DfuException.TransferFailed("Unexpected response to SELECT: $response")
        }
    }

    private suspend fun sendCreate(objectType: Byte, size: Int) {
        val payload = byteArrayOf(DfuOpcode.CREATE, objectType) + intToLeBytes(size)
        val response = sendCommand(payload)
        response.requireSuccess(DfuOpcode.CREATE)
        Logger.d { "DFU: Created object type=0x${objectType.toUByte().toString(16)} size=$size" }
    }

    private suspend fun sendCalculateChecksum(): DfuResponse.ChecksumResult {
        val response = sendCommand(byteArrayOf(DfuOpcode.CALCULATE_CHECKSUM))
        return when (response) {
            is DfuResponse.ChecksumResult -> response
            is DfuResponse.Failure ->
                throw DfuException.ProtocolError(DfuOpcode.CALCULATE_CHECKSUM, response.resultCode)
            else -> throw DfuException.TransferFailed("Unexpected response to CALCULATE_CHECKSUM: $response")
        }
    }

    private suspend fun sendExecute() {
        val response = sendCommand(byteArrayOf(DfuOpcode.EXECUTE))
        response.requireSuccess(DfuOpcode.EXECUTE)
        Logger.d { "DFU: Object executed." }
    }

    private suspend fun awaitNotification(timeoutMs: Long): DfuResponse = try {
        withTimeout(timeoutMs) {
            val bytes = notificationChannel.receive()
            DfuResponse.parse(bytes).also { Logger.d { "DFU: Notification → $it" } }
        }
    } catch (_: TimeoutCancellationException) {
        throw DfuException.Timeout("No response from Control Point after ${timeoutMs}ms")
    }

    private fun DfuResponse.requireSuccess(expectedOpcode: Byte) {
        when (this) {
            is DfuResponse.Success ->
                if (opcode != expectedOpcode) {
                    throw DfuException.TransferFailed(
                        "Response opcode mismatch: expected 0x${expectedOpcode.toUByte().toString(16)}, " +
                            "got 0x${opcode.toUByte().toString(16)}",
                    )
                }
            is DfuResponse.Failure -> throw DfuException.ProtocolError(opcode, resultCode)
            else ->
                throw DfuException.TransferFailed(
                    "Unexpected response for opcode 0x${expectedOpcode.toUByte().toString(16)}: $this",
                )
        }
    }

    // ---------------------------------------------------------------------------
    // Scanning helpers
    // ---------------------------------------------------------------------------

    private suspend fun scanForDevice(predicate: (BleDevice) -> Boolean): BleDevice? = scanForBleDevice(
        scanner = scanner,
        tag = "DFU",
        serviceUuid = SecureDfuUuids.SERVICE,
        retryCount = SCAN_RETRY_COUNT,
        retryDelayMs = SCAN_RETRY_DELAY_MS,
        predicate = predicate,
    )

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val COMMAND_TIMEOUT_MS = 30_000L
        private const val SUBSCRIPTION_SETTLE_MS = 500L
        private const val SCAN_RETRY_COUNT = 3
        private const val SCAN_RETRY_DELAY_MS = 2_000L
        private const val RETRY_DELAY_MS = 2_000L

        /**
         * PRN interval: device sends a ChecksumResult notification every N packets. Provides flow control and early CRC
         * validation. 0 = disabled.
         */
        private const val PRN_INTERVAL = 10

        /** Number of times to retry a failed object transfer before giving up. */
        private const val OBJECT_RETRY_COUNT = 3

        private const val DEFAULT_MAX_OBJECT_SIZE = 4096
    }
}
