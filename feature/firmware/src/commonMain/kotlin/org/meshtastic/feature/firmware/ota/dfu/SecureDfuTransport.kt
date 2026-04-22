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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.DEFAULT_BLE_WRITE_VALUE_LENGTH
import org.meshtastic.core.ble.MeshtasticBleDevice
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.firmware.ota.calculateMacPlusOne
import org.meshtastic.feature.firmware.ota.scanForBleDevice
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

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
    dispatcher: CoroutineDispatcher,
) : DfuUploadTransport {
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
     * Per the Nordic Secure DFU spec, indications **must** be enabled on the Buttonless DFU characteristic before
     * writing the Enter DFU command. The device validates the CCCD and rejects the write with
     * `ATTERR_CPS_CCCD_CONFIG_ERROR` if indications are not enabled.
     *
     * After writing the trigger, the device may disconnect before the indication response arrives — this race condition
     * is expected and handled gracefully.
     *
     * The caller must have already released the mesh-service BLE connection before calling this.
     */
    suspend fun triggerButtonlessDfu(): Result<Unit> = safeCatching {
        // No scan needed: the address is already bonded with the OS, so we can connect directly the same way the
        // Nordic Android DFU library does (BluetoothAdapter.getRemoteDevice(address).connectGatt). Scanning here is
        // unreliable because the device may not have resumed advertising in the brief window after we released the
        // mesh-service GATT.
        Logger.i { "DFU: Connecting to $address to trigger buttonless DFU..." }
        bleConnection.connectAndAwait(MeshtasticBleDevice(address), CONNECT_TIMEOUT)

        // Try the Nordic Secure DFU service (FE59) first — used when the firmware is built with BLE_DFU_SECURE.
        // If it isn't exposed, fall back to the Legacy DFU service (1530) used by Meshtastic builds that rely on
        // Adafruit's stock BLEDfu helper. Both ultimately reboot the device into the same bootloader.
        try {
            triggerSecureButtonless()
        } catch (e: NoSuchElementException) {
            Logger.i(e) { "DFU: Secure DFU service (FE59) not present — falling back to legacy DFU service (1530)" }
            triggerLegacyButtonless()
        }

        // Device will disconnect and reboot — expected, not an error.
        Logger.i { "DFU: Buttonless DFU triggered, device is rebooting..." }
        bleConnection.disconnect()
    }

    /** Nordic Secure DFU buttonless trigger — INDICATE + write 0x01, then wait for `0x20-01-STATUS` response. */
    private suspend fun triggerSecureButtonless() {
        triggerButtonless(
            serviceUuid = SecureDfuUuids.SERVICE,
            characteristicUuid = SecureDfuUuids.BUTTONLESS_NO_BONDS,
            payload = byteArrayOf(BUTTONLESS_ENTER_BOOTLOADER),
            profileTimeout = TRIGGER_TIMEOUT,
            logLabel = "secure",
            awaitResponse = { channel ->
                try {
                    withTimeout(BUTTONLESS_RESPONSE_TIMEOUT) {
                        val response = channel.receive()
                        if (
                            response.size >= 3 &&
                            response[0] == BUTTONLESS_RESPONSE_CODE &&
                            response[2] != 0x01.toByte()
                        ) {
                            Logger.w { "DFU: Buttonless DFU response indicates error: ${response.toHexString()}" }
                        } else {
                            Logger.i { "DFU: Buttonless DFU indication received successfully" }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Logger.d { "DFU: No buttonless indication received (device may have already disconnected)" }
                } catch (_: Exception) {
                    Logger.d { "DFU: Buttonless indication wait interrupted (device disconnecting)" }
                }
            },
        )
    }

    /**
     * Nordic Legacy DFU buttonless trigger (Adafruit `BLEDfu`). Subscribe to NOTIFICATIONS on the control point
     * (required to satisfy the device's CCCD check), then write `[0x01, 0x04]` (START_DFU + IMAGE_TYPE_APPLICATION, per
     * Nordic's `LegacyButtonlessDfuImpl.java:53`). The device reboots into the bootloader immediately — no notification
     * response is expected before disconnect.
     */
    private suspend fun triggerLegacyButtonless() {
        triggerButtonless(
            serviceUuid = LegacyDfuUuids.SERVICE,
            characteristicUuid = LegacyDfuUuids.CONTROL_POINT,
            payload = LEGACY_BUTTONLESS_ENTER_BOOTLOADER,
            profileTimeout = TRIGGER_TIMEOUT,
            logLabel = "legacy",
            awaitResponse = null,
        )
    }

    /**
     * Shared scaffold for both Secure and Legacy buttonless triggers. The Adafruit/Nordic protocol is identical in
     * shape: enable CCCD on a control characteristic, write a small opcode, optionally await an indication/notification
     * response. The whole trigger is wrapped in [profileTimeout] and treats timeout as success — by the time we'd time
     * out, the device has either rebooted (verified by [connectToDfuMode]) or never received the byte (which surfaces
     * as a useful scan failure later). This escapes the well-known race where Kable's `WITH_RESPONSE` write blocks on
     * an ATT ACK that never arrives because the device rebooted before sending it.
     */
    private suspend fun triggerButtonless(
        serviceUuid: Uuid,
        characteristicUuid: Uuid,
        payload: ByteArray,
        profileTimeout: Duration,
        logLabel: String,
        awaitResponse: (suspend CoroutineScope.(Channel<ByteArray>) -> Unit)?,
    ) {
        try {
            withTimeout(profileTimeout) {
                bleConnection.profile(serviceUuid, timeout = profileTimeout) { service ->
                    val char = service.characteristic(characteristicUuid)

                    val channel = Channel<ByteArray>(Channel.UNLIMITED)
                    val observeJob =
                        service
                            .observe(char)
                            .onEach { channel.trySend(it) }
                            .catch { e ->
                                Logger.d(e) { "DFU: $logLabel notification stream ended (expected on disconnect)" }
                            }
                            .launchIn(this)

                    delay(SUBSCRIPTION_SETTLE)

                    Logger.i { "DFU: Writing $logLabel buttonless DFU trigger..." }
                    service.write(char, payload, BleWriteType.WITH_RESPONSE)

                    awaitResponse?.invoke(this, channel)

                    observeJob.cancel()
                }
            }
        } catch (_: TimeoutCancellationException) {
            Logger.w {
                "DFU: $logLabel buttonless trigger timed out — likely cause: stale BLE bond (Meshtastic " +
                    "BLEDfu requires SECMODE_ENC_WITH_MITM). User must Forget+Re-pair the device in Android " +
                    "Bluetooth settings if the next DFU-mode scan also fails."
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Phase 2: Connect to device in DFU mode
    // ---------------------------------------------------------------------------

    /**
     * Scans for the device in DFU mode (address or address+1) and establishes the GATT connection, enabling
     * notifications on the Control Point.
     */
    override suspend fun connectToDfuMode(): Result<Unit> = safeCatching {
        val dfuAddress = calculateMacPlusOne(address)
        val targetAddresses = setOf(address, dfuAddress)
        Logger.i { "DFU: Scanning for DFU mode device at $targetAddresses..." }

        val device =
            scanForDevice { d -> d.address in targetAddresses }
                ?: throw DfuException.ConnectionFailed("DFU mode device not found. Tried: $targetAddresses")

        Logger.i { "DFU: Found DFU mode device at ${device.address}, connecting..." }

        bleConnection.connectionState.onEach { Logger.d { "DFU: Connection state → $it" } }.launchIn(transportScope)

        val connected = bleConnection.connectAndAwait(device, CONNECT_TIMEOUT)
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

            delay(SUBSCRIPTION_SETTLE)
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
     *
     * PRN is explicitly disabled (set to 0) for the init packet per the Nordic DFU library convention — the init packet
     * is small (<512 bytes, fits in a single object) and does not benefit from flow control.
     */
    override suspend fun transferInitPacket(initPacket: ByteArray): Result<Unit> = safeCatching {
        Logger.i { "DFU: Transferring init packet (${initPacket.size} bytes)..." }
        setPrn(0)
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
    override suspend fun transferFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit): Result<Unit> =
        safeCatching {
            Logger.i { "DFU: Transferring firmware (${firmware.size} bytes)..." }
            // Bump BLE link to high-throughput mode (~7.5 ms interval) before streaming.
            // Default Android intervals (~30-50 ms) starve the link during sustained DFU and trigger LSTO.
            val highPriorityRequested = bleConnection.requestHighConnectionPriority()
            Logger.i { "Secure DFU: requestHighConnectionPriority -> $highPriorityRequested" }
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
    override suspend fun abort() {
        safeCatching {
            bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
                val controlChar = service.characteristic(SecureDfuUuids.CONTROL_POINT)
                service.write(controlChar, byteArrayOf(DfuOpcode.ABORT), BleWriteType.WITH_RESPONSE)
            }
            Logger.i { "DFU: Abort sent to device." }
        }
            .onFailure { Logger.w(it) { "DFU: Failed to send abort (device may have disconnected)" } }
    }

    /** Disconnect from the DFU target and cancel the transport coroutine scope. */
    override suspend fun close() {
        safeCatching { bleConnection.disconnect() }.onFailure { Logger.w(it) { "DFU: Error during disconnect" } }
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
                if (attempt < OBJECT_RETRY_COUNT - 1) delay(RETRY_DELAY)
            }
        }
        throw lastError ?: DfuException.TransferFailed("Object transfer failed after $OBJECT_RETRY_COUNT attempts")
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth")
    private suspend fun transferObject(objectType: Byte, data: ByteArray, onProgress: (suspend (Float) -> Unit)?) {
        val selectResult = sendSelect(objectType)
        val maxObjectSize = selectResult.maxSize.takeIf { it > 0 } ?: DEFAULT_MAX_OBJECT_SIZE
        val totalBytes = data.size
        var offset = 0
        var isFirstChunk = true
        var currentPrnInterval = if (objectType == DfuObjectType.COMMAND) 0 else PRN_INTERVAL

        // Resume logic — per Nordic DFU spec, distinguish between executed objects and partial current object.
        if (selectResult.offset in 1..totalBytes) {
            val expectedCrc = DfuCrc32.calculate(data, length = selectResult.offset)
            if (expectedCrc == selectResult.crc32) {
                val executedBytes = maxObjectSize * (selectResult.offset / maxObjectSize)
                val pendingBytes = selectResult.offset - executedBytes

                if (selectResult.offset == totalBytes) {
                    // Device already has the complete data. Just execute.
                    Logger.i { "DFU: Device already has all $totalBytes bytes (CRC match), executing..." }
                    sendExecute()
                    onProgress?.invoke(1f)
                    return
                } else if (pendingBytes == 0 && executedBytes > 0) {
                    // Offset is at an object boundary — last complete object may not be executed yet.
                    Logger.i { "DFU: Resuming at object boundary $executedBytes, executing last object..." }
                    try {
                        sendExecute()
                    } catch (e: DfuException.ProtocolError) {
                        if (e.resultCode != DfuResultCode.OPERATION_NOT_PERMITTED) throw e
                        Logger.d { "DFU: Execute returned OPERATION_NOT_PERMITTED (already executed), continuing..." }
                    }
                    offset = executedBytes
                    isFirstChunk = false
                } else if (pendingBytes > 0) {
                    // Partial object in progress — skip to the start of the current object and resume from there.
                    // We resume from the executed boundary because the partial object needs to be re-sent if we can't
                    // verify the partial state cleanly. The Nordic library does the same thing.
                    Logger.i {
                        "DFU: Resuming at offset $executedBytes (executed=$executedBytes, pending=$pendingBytes)"
                    }
                    offset = executedBytes
                    isFirstChunk = false
                }
            } else {
                Logger.w { "DFU: Offset ${selectResult.offset} CRC mismatch — restarting from 0" }
            }
        }

        while (offset < totalBytes) {
            val objectSize = minOf(maxObjectSize, totalBytes - offset)
            sendCreate(objectType, objectSize)

            // First-chunk delay: some older bootloaders need time to prepare flash after Create.
            // The Nordic DFU library uses 400ms for the first chunk.
            if (isFirstChunk) {
                delay(FIRST_CHUNK_DELAY)
                isFirstChunk = false
            }

            val objectEnd = offset + objectSize
            writePackets(data, offset, objectEnd, currentPrnInterval)

            val checksumResult = sendCalculateChecksum()
            val expectedCrc = DfuCrc32.calculate(data, length = objectEnd)

            // Bytes-lost detection: if the device reports fewer bytes than we sent, some packets were lost in
            // the BLE stack. Rather than throwing immediately, tighten PRN to 1 and retry the remaining bytes.
            if (checksumResult.offset < objectEnd) {
                val bytesLost = objectEnd - checksumResult.offset
                Logger.w {
                    "DFU: $bytesLost bytes lost in BLE stack (sent to $objectEnd, device at ${checksumResult.offset})"
                }
                // Verify CRC up to the device's offset is valid
                val partialCrc = DfuCrc32.calculate(data, length = checksumResult.offset)
                if (checksumResult.crc32 != partialCrc) {
                    throw DfuException.ChecksumMismatch(expected = partialCrc, actual = checksumResult.crc32)
                }
                // Tighten PRN to maximum flow control and resend the lost portion
                currentPrnInterval = 1
                Logger.i { "DFU: Forcing PRN=1 and resending from offset ${checksumResult.offset}" }
                writePackets(data, checksumResult.offset, objectEnd, currentPrnInterval)

                val recheckResult = sendCalculateChecksum()
                if (recheckResult.offset != objectEnd || recheckResult.crc32 != expectedCrc) {
                    val expectedHex = expectedCrc.toUInt().toString(16)
                    val actualHex = recheckResult.crc32.toUInt().toString(16)
                    throw DfuException.TransferFailed(
                        "Recovery failed after bytes-lost: " +
                            "expected offset=$objectEnd crc=0x$expectedHex, " +
                            "got offset=${recheckResult.offset} crc=0x$actualHex",
                    )
                }
                Logger.i { "DFU: Recovery successful, continuing with PRN=1" }
            } else if (checksumResult.offset != objectEnd) {
                throw DfuException.TransferFailed(
                    "Offset mismatch after object: expected $objectEnd, got ${checksumResult.offset}",
                )
            } else if (checksumResult.crc32 != expectedCrc) {
                throw DfuException.ChecksumMismatch(expected = expectedCrc, actual = checksumResult.crc32)
            }

            // Execute with retry for INVALID_OBJECT — the SoftDevice may still be erasing flash.
            try {
                sendExecute()
            } catch (e: DfuException.ProtocolError) {
                if (e.resultCode == DfuResultCode.INVALID_OBJECT && offset + objectSize >= totalBytes) {
                    Logger.w { "DFU: Execute returned INVALID_OBJECT on final object, retrying once..." }
                    delay(RETRY_DELAY)
                    sendExecute()
                } else {
                    throw e
                }
            }

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
     * PRN flow control: every [prnInterval] packets we await a ChecksumResult notification from the device and validate
     * the running CRC-32. This prevents the device's receive buffer from overflowing and detects corruption early. Pass
     * 0 to disable PRN (used for init packets).
     */
    private suspend fun writePackets(data: ByteArray, from: Int, until: Int, prnInterval: Int) {
        val mtu = bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE) ?: DEFAULT_BLE_WRITE_VALUE_LENGTH
        var pos = from

        coroutineScope {
            // Trip-wire: cancels the streaming coroutine the moment Kable observes a disconnect.
            val watcher = launch {
                val state = bleConnection.connectionState.first { it is BleConnectionState.Disconnected }
                Logger.w { "Secure DFU: Link dropped mid-stream at offset $pos/$until (state=$state)" }
                throw DfuException.ConnectionFailed("BLE link dropped mid-upload at byte $pos/$until")
            }

            try {
                var packetsSincePrn = 0
                bleConnection.profile(SecureDfuUuids.SERVICE, timeout = STREAM_TIMEOUT) { service ->
                    val packetChar = service.characteristic(SecureDfuUuids.PACKET)
                    while (pos < until) {
                        val chunkEnd = minOf(pos + mtu, until)
                        service.write(packetChar, data.copyOfRange(pos, chunkEnd), BleWriteType.WITHOUT_RESPONSE)
                        pos = chunkEnd
                        packetsSincePrn++

                        if (prnInterval > 0 && packetsSincePrn >= prnInterval && pos < until) {
                            val response = awaitNotification(COMMAND_TIMEOUT)
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
            } finally {
                watcher.cancel()
            }
        }
    }

    private suspend fun sendCommand(payload: ByteArray): DfuResponse {
        bleConnection.profile(SecureDfuUuids.SERVICE) { service ->
            val controlChar = service.characteristic(SecureDfuUuids.CONTROL_POINT)
            service.write(controlChar, payload, BleWriteType.WITH_RESPONSE)
        }
        return awaitNotification(COMMAND_TIMEOUT)
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
            is DfuResponse.Failure ->
                throw DfuException.ProtocolError(DfuOpcode.SELECT, response.resultCode, response.extendedError)
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
                throw DfuException.ProtocolError(
                    DfuOpcode.CALCULATE_CHECKSUM,
                    response.resultCode,
                    response.extendedError,
                )
            else -> throw DfuException.TransferFailed("Unexpected response to CALCULATE_CHECKSUM: $response")
        }
    }

    private suspend fun sendExecute() {
        val response = sendCommand(byteArrayOf(DfuOpcode.EXECUTE))
        response.requireSuccess(DfuOpcode.EXECUTE)
        Logger.d { "DFU: Object executed." }
    }

    private suspend fun awaitNotification(timeout: Duration): DfuResponse = try {
        withTimeout(timeout) {
            val bytes = notificationChannel.receive()
            DfuResponse.parse(bytes).also { Logger.d { "DFU: Notification → $it" } }
        }
    } catch (_: TimeoutCancellationException) {
        throw DfuException.Timeout("No response from Control Point after $timeout")
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
            is DfuResponse.Failure -> throw DfuException.ProtocolError(opcode, resultCode, extendedError)
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
        retryDelay = SCAN_RETRY_DELAY,
        predicate = predicate,
    )

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    companion object {
        private val CONNECT_TIMEOUT = 15.seconds
        private val COMMAND_TIMEOUT = 30.seconds
        private val SUBSCRIPTION_SETTLE = 500.milliseconds
        private val BUTTONLESS_RESPONSE_TIMEOUT = 3.seconds

        /**
         * Tight wall-clock cap on the buttonless trigger phase (Secure or Legacy). After we send the opcode, the device
         * reboots into the bootloader and disconnects — but Kable's `WITH_RESPONSE` write blocks on an ATT ACK that
         * never arrives in this race. If we don't see the disconnect propagate within this window, the device almost
         * certainly didn't receive the byte, so failing fast lets the caller surface a useful error (or retry) instead
         * of waiting on the default 30s `profile()` timeout. Comfortably exceeds the secure-path indication wait
         * ([BUTTONLESS_RESPONSE_TIMEOUT]) plus settle/write overhead.
         */
        private val TRIGGER_TIMEOUT = 5.seconds
        private const val SCAN_RETRY_COUNT = 3
        private val SCAN_RETRY_DELAY = 2.seconds
        private val RETRY_DELAY = 2.seconds
        private val FIRST_CHUNK_DELAY = 400.milliseconds

        /**
         * Wall-clock budget for a single firmware-object streaming session. Must comfortably exceed the upload duration
         * for the largest expected image at the slowest realistic Secure DFU throughput. Per-PRN/per-write watchdogs
         * inside the loop detect real stalls; this is just a safety net so a hung profile block can't sit forever.
         */
        private val STREAM_TIMEOUT = 15.minutes

        /** Response code prefix for Buttonless DFU indications (0x20 = response). */
        private const val BUTTONLESS_RESPONSE_CODE: Byte = 0x20

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
