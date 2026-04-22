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
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.firmware.ota.calculateMacPlusOne
import org.meshtastic.feature.firmware.ota.scanForBleDevice
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Kable-based transport for the Nordic **Legacy DFU** protocol (Nordic SDK 11/12 / Adafruit `BLEDfu`).
 *
 * Most nRF52 boards in the field — including the RAK4631 with the recommended Adafruit/oltaco "OTAFIX" bootloader —
 * speak Legacy DFU rather than Secure DFU. The two protocols share nothing at the upload layer:
 * - Different service & characteristic UUIDs (`1530`/`1531`/`1532` vs `FE59`/`8EC9…`).
 * - Different opcodes; init packet is sent on the Packet char between two control-point writes (vs Secure's
 *   CREATE/PACKET/EXECUTE object flow).
 * - PRN payload is bytes-received uint32 (vs Secure's offset+CRC32).
 * - No CRC32 in the protocol — image integrity relies on the device's CRC16 in the init packet.
 *
 * Phase-1 buttonless trigger is shared with [SecureDfuTransport] (see `triggerButtonlessDfu` there).
 */
class LegacyDfuTransport(
    private val scanner: BleScanner,
    connectionFactory: BleConnectionFactory,
    private val address: String,
    dispatcher: CoroutineDispatcher,
) : DfuUploadTransport {
    private val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val bleConnection = connectionFactory.create(transportScope, "Legacy DFU")

    /** Receives parsed responses from the Control Point characteristic. */
    private val notificationChannel = Channel<LegacyDfuResponse>(Channel.UNLIMITED)

    /** Name advertised by the device in DFU mode (e.g. `4631_DFU`). Captured in [connectToDfuMode]. */
    private var dfuAdvertisedName: String? = null

    // ---------------------------------------------------------------------------
    // Phase 2: Connect to device in DFU mode
    // ---------------------------------------------------------------------------

    /**
     * Scans for the device in DFU mode (address or address+1) and establishes the GATT connection, enabling
     * notifications on the Control Point.
     *
     * Best-effort reads the optional DFU Version characteristic to gate against unsupported old (SDK ≤ 6) bootloaders.
     */
    override suspend fun connectToDfuMode(): Result<Unit> = safeCatching {
        val dfuAddress = calculateMacPlusOne(address)
        val targetAddresses = setOf(address, dfuAddress)
        Logger.i { "Legacy DFU: Scanning for DFU mode device at $targetAddresses..." }

        val device =
            scanForDevice { d -> d.address in targetAddresses }
                ?: throw DfuException.ConnectionFailed("DFU mode device not found. Tried: $targetAddresses")

        Logger.i { "Legacy DFU: Found DFU mode device at ${device.address} (name=${device.name}), connecting..." }
        dfuAdvertisedName = device.name

        bleConnection.connectionState
            .onEach { Logger.d { "Legacy DFU: Connection state → $it" } }
            .launchIn(transportScope)

        val connected = bleConnection.connectAndAwait(device, CONNECT_TIMEOUT)
        if (connected is BleConnectionState.Disconnected) {
            throw DfuException.ConnectionFailed("Failed to connect to DFU device ${device.address}")
        }

        bleConnection.profile(LegacyDfuUuids.SERVICE) { service ->
            val controlChar = service.characteristic(LegacyDfuUuids.CONTROL_POINT)

            val subscribed = CompletableDeferred<Unit>()
            service
                .observe(controlChar)
                .onEach { bytes ->
                    if (!subscribed.isCompleted) {
                        Logger.d { "Legacy DFU: Control Point subscribed" }
                        subscribed.complete(Unit)
                    }
                    val parsed = LegacyDfuResponse.parse(bytes)
                    Logger.d { "Legacy DFU: Notification → $parsed" }
                    notificationChannel.trySend(parsed)
                }
                .catch { e ->
                    if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    Logger.e(e) { "Legacy DFU: Control Point notification error" }
                }
                .launchIn(this)

            delay(SUBSCRIPTION_SETTLE)
            if (!subscribed.isCompleted) subscribed.complete(Unit)
            subscribed.await()

            // Best-effort DFU Version read — gate out unsupported old bootloaders (SDK ≤ 6).
            val versionChar = service.characteristic(LEGACY_DFU_VERSION_UUID)
            val version =
                runCatching { service.read(versionChar) }
                    .map { bytes ->
                        if (bytes.size >= 2) (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8) else -1
                    }
                    .getOrElse { -1 }
            Logger.i { "Legacy DFU: DFU Version characteristic = $version (-1 ⇒ absent / unreadable)" }
            if (version in 1..MIN_SUPPORTED_DFU_VERSION - 1) {
                throw LegacyDfuException.UnsupportedBootloader(version)
            }

            Logger.i { "Legacy DFU: Connected and ready (${device.address})" }
        }
    }

    // ---------------------------------------------------------------------------
    // Phase 3: Init packet transfer (.dat)
    // ---------------------------------------------------------------------------

    /**
     * Sends the legacy DFU init packet using the SDK 7+ extended-init flow:
     * 1. `START_DFU [APP]` → device prepares.
     * 2. Image sizes `[0u32, 0u32, appSize_u32]` on the Packet characteristic.
     * 3. `INIT_PARAMS_START` → init bytes on Packet → `INIT_PARAMS_COMPLETE`.
     *
     * The legacy init packet for an APP image is typically 14 bytes (SDK 7) or 32 bytes (SDK 11 with signature). Any
     * `.dat` significantly larger than that almost certainly belongs to a Secure DFU build that has been mis-packaged
     * for a legacy bootloader — we surface that with a helpful error rather than letting the device reject it.
     *
     * The init packet is bracketed by START/COMPLETE, but the upload itself is intermixed with image sizes. To match
     * Nordic's library, the [initPacket] argument here is the legacy init bytes; the firmware [transferFirmware] method
     * needs to be called next to provide the actual image (and the size we include here must match its length).
     *
     * Because `transferInitPacket` is called before [transferFirmware], we don't yet know the firmware size when
     * sending image sizes. To keep the [DfuUploadTransport] contract clean we instead send image sizes lazily inside
     * [transferFirmware]'s START phase. **This method only writes START_DFU + brackets the init packet.** Any
     * outstanding image-size write happens at the start of [transferFirmware].
     */
    override suspend fun transferInitPacket(initPacket: ByteArray): Result<Unit> = safeCatching {
        if (initPacket.size > MAX_REASONABLE_LEGACY_INIT_SIZE) {
            throw LegacyDfuException.InitPacketNotLegacy(initPacket.size)
        }
        Logger.i { "Legacy DFU: Stashing init packet (${initPacket.size} bytes) for transfer in Phase 4." }
        pendingInitPacket = initPacket
    }

    /** Init packet stashed by [transferInitPacket]; flushed at the start of [transferFirmware]. */
    private var pendingInitPacket: ByteArray? = null

    // ---------------------------------------------------------------------------
    // Phase 4: Firmware transfer (.bin)
    // ---------------------------------------------------------------------------

    /**
     * Drives the full upload sequence (START, init-packet brackets, PRN setup, firmware stream, validate, activate).
     *
     * Sequence details:
     * 1. `START_DFU [0x04]` (APP image only).
     * 2. Image sizes payload on Packet char: `[0u32, 0u32, firmware.size_u32]`.
     * 3. Await START response.
     * 4. `INIT_PARAMS_START`, init bytes on Packet, `INIT_PARAMS_COMPLETE`. Await init response.
     * 5. `PRN_REQ [PRN_LE16]`. (No response.)
     * 6. `RECEIVE_FIRMWARE_IMAGE`. (No response.)
     * 7. Stream firmware in MTU-sized chunks. Every PRN packets, await `PacketReceipt(bytesReceived)` and verify count.
     * 8. After last byte, await final response for `RECEIVE_FIRMWARE_IMAGE`.
     * 9. `VALIDATE`, await response.
     * 10. `ACTIVATE_AND_RESET` — device reboots; write may fail with disconnect, treat as success.
     */
    @Suppress("LongMethod")
    override suspend fun transferFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit): Result<Unit> =
        safeCatching {
            val initPacket =
                pendingInitPacket
                    ?: throw DfuException.TransferFailed("transferInitPacket must be called before transferFirmware")
            Logger.i { "Legacy DFU: Starting upload (init=${initPacket.size}B, firmware=${firmware.size}B)..." }

            // ── 1. START_DFU + image sizes on Packet, then response ─────────────
            writeControlPoint(byteArrayOf(LegacyDfuOpcode.START_DFU, LegacyDfuImageType.APPLICATION))
            writePacket(legacyImageSizesPayload(appSize = firmware.size))
            requireSuccess(LegacyDfuOpcode.START_DFU, awaitResponse(START_RESPONSE_TIMEOUT))

            // ── 2. INIT_PARAMS_START → init bytes on Packet → INIT_PARAMS_COMPLETE → response ──
            writeControlPoint(byteArrayOf(LegacyDfuOpcode.INIT_DFU_PARAMS, LegacyDfuOpcode.INIT_PARAMS_START))
            writePacketChunked(initPacket)
            writeControlPoint(byteArrayOf(LegacyDfuOpcode.INIT_DFU_PARAMS, LegacyDfuOpcode.INIT_PARAMS_COMPLETE))
            requireSuccess(LegacyDfuOpcode.INIT_DFU_PARAMS, awaitResponse(COMMAND_TIMEOUT))

            // Bump the BLE link to high-throughput mode (~7.5 ms interval) before streaming.
            // Default Android intervals (~30-50 ms) starve the link during sustained DFU and trigger LSTO. Mirrors
            // Nordic LegacyDfuImpl.java requestConnectionPriority(CONNECTION_PRIORITY_HIGH).
            val highPriorityRequested = bleConnection.requestHighConnectionPriority()
            Logger.i { "Legacy DFU: requestHighConnectionPriority -> $highPriorityRequested" }

            // ── 3. PRN setup ────────────────────────────────────────────────────
            writeControlPoint(legacyPrnRequestPayload(PRN_INTERVAL_PACKETS))

            // ── 4. RECEIVE_FIRMWARE_IMAGE ──────────────────────────────────────
            writeControlPoint(byteArrayOf(LegacyDfuOpcode.RECEIVE_FIRMWARE_IMAGE))

            // ── 5. Stream firmware ─────────────────────────────────────────────
            streamFirmware(firmware, onProgress)

            // ── 6. Final RECEIVE_FIRMWARE_IMAGE response ────────────────────────
            requireSuccess(LegacyDfuOpcode.RECEIVE_FIRMWARE_IMAGE, awaitResponse(VALIDATE_TIMEOUT))

            // ── 7. VALIDATE ────────────────────────────────────────────────────
            writeControlPoint(byteArrayOf(LegacyDfuOpcode.VALIDATE))
            requireSuccess(LegacyDfuOpcode.VALIDATE, awaitResponse(VALIDATE_TIMEOUT))

            // ── 8. ACTIVATE_AND_RESET ──────────────────────────────────────────
            // Device may reset before the GATT write ACK lands — treat any write failure / disconnect as success.
            Logger.i { "Legacy DFU: Sending ACTIVATE_AND_RESET (disconnect during write is expected)" }
            runCatching { writeControlPoint(byteArrayOf(LegacyDfuOpcode.ACTIVATE_AND_RESET)) }
                .onFailure { Logger.i(it) { "Legacy DFU: ACTIVATE write reported failure (expected on reset)" } }

            onProgress(1f)
            Logger.i { "Legacy DFU: Upload complete, device rebooting into new firmware." }
        }

    /**
     * Stream [firmware] to the Packet characteristic, awaiting a [LegacyDfuResponse.PacketReceipt] every
     * [PRN_INTERVAL_PACKETS] packets and verifying the bytes-received count.
     *
     * Watches the connection state in parallel with the write loop; if the link drops mid-stream we cancel the write
     * coroutine and surface a [DfuException.ConnectionFailed] immediately rather than waiting indefinitely for a write
     * that will never complete.
     */

    /**
     * Determine the per-packet size for firmware streaming.
     *
     * Returns [LEGACY_DFU_PACKET_SIZE] (20) by default for safety against stock Nordic / pre-2.1 OTAFIX bootloaders
     * that overrun their flash buffer on larger writes. When the device advertises an OTAFIX-2.1+ name (`<board>_DFU`
     * suffix per https://github.com/oltaco/Adafruit_nRF52_Bootloader_OTAFIX#changes-in-otafix-21) and the connection
     * has negotiated a larger ATT MTU, returns that MTU − 3 (ATT header overhead) capped at 244 bytes.
     */
    private fun computeStreamPacketSize(): Int {
        val name = dfuAdvertisedName.orEmpty()
        val isOtafix21 = name.endsWith(OTAFIX_NAME_SUFFIX, ignoreCase = true)
        if (!isOtafix21) return LEGACY_DFU_PACKET_SIZE
        val negotiated =
            bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE) ?: return LEGACY_DFU_PACKET_SIZE
        return negotiated.coerceIn(LEGACY_DFU_PACKET_SIZE, MAX_HIGH_MTU_PACKET_SIZE)
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
    private suspend fun streamFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit) {
        // Default: 20-byte ATT packets (the only size accepted by stock Nordic / Adafruit pre-OTAFIX-2.1
        // bootloaders). Sending larger packets to those bootloaders overruns the flash-write buffer and
        // silently bricks the device. See .agent_refs/Android-DFU-Library/.../BaseCustomDfuImpl.java:417.
        //
        // OTAFIX 2.1+ explicitly supports high-MTU packets ("Enables larger DFU packets for improved
        // throughput when supported by the client" per the OTAFIX README). It re-uses the standard
        // `<board>_DFU` advertising-name suffix as a marker for the 2.1 feature set, so we opportunistically
        // bump the packet size to the negotiated ATT MTU (minus 3 for the ATT header) when we see that name.
        val mtu = computeStreamPacketSize()
        var offset = 0
        Logger.i {
            "Legacy DFU: Streaming ${firmware.size} bytes with packet size $mtu " +
                "(advertised='${dfuAdvertisedName ?: "?"}')"
        }

        coroutineScope {
            // Trip-wire: cancels the streaming coroutine the moment Kable observes a disconnect.
            val watcher = launch {
                val state = bleConnection.connectionState.first { it is BleConnectionState.Disconnected }
                Logger.w { "Legacy DFU: Link dropped mid-stream at offset $offset/${firmware.size} (state=$state)" }
                throw DfuException.ConnectionFailed("BLE link dropped mid-upload at byte $offset/${firmware.size}")
            }

            try {
                var packetsSincePrn = 0
                var bytesAtLastPrn = 0L
                bleConnection.profile(LegacyDfuUuids.SERVICE, timeout = STREAM_TIMEOUT) { service ->
                    val packetChar = service.characteristic(LEGACY_DFU_PACKET_UUID)
                    while (offset < firmware.size) {
                        val end = minOf(offset + mtu, firmware.size)
                        try {
                            service.write(packetChar, firmware.copyOfRange(offset, end), BleWriteType.WITHOUT_RESPONSE)
                        } catch (e: CancellationException) {
                            Logger.w(e) {
                                "Legacy DFU: Write CANCELLED at offset $offset/${firmware.size} cause=${e.cause}"
                            }
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                            Logger.w(e) { "Legacy DFU: Write FAILED at offset $offset/${firmware.size}: ${e.message}" }
                            throw e
                        }
                        offset = end
                        packetsSincePrn++

                        if (packetsSincePrn >= PRN_INTERVAL_PACKETS && offset < firmware.size) {
                            Logger.d { "Legacy DFU: Awaiting PRN at offset $offset" }
                            val receipt =
                                try {
                                    awaitPacketReceipt()
                                } catch (e: CancellationException) {
                                    Logger.w(e) {
                                        "Legacy DFU: awaitPacketReceipt CANCELLED at offset $offset cause=${e.cause}"
                                    }
                                    throw e
                                }
                            val expected = offset.toLong()
                            if (receipt.bytesReceived != expected) {
                                throw LegacyDfuException.PacketReceiptMismatch(expected, receipt.bytesReceived)
                            }
                            bytesAtLastPrn = receipt.bytesReceived
                            packetsSincePrn = 0
                            onProgress(offset.toFloat() / firmware.size)
                        }
                    }
                }
                Logger.d { "Legacy DFU: Streamed $offset/${firmware.size} bytes (lastPRN=$bytesAtLastPrn)" }
            } finally {
                watcher.cancel()
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Abort & teardown
    // ---------------------------------------------------------------------------

    /**
     * Send `RESET` to the device, instructing it to discard any in-progress transfer and reboot. Best-effort — the
     * device may disconnect before the write ACK lands; that's expected.
     */
    override suspend fun abort() {
        safeCatching {
            writeControlPoint(byteArrayOf(LegacyDfuOpcode.RESET))
            Logger.i { "Legacy DFU: RESET sent." }
        }
            .onFailure { Logger.w(it) { "Legacy DFU: Failed to send RESET (device may already be disconnected)" } }
    }

    override suspend fun close() {
        safeCatching { bleConnection.disconnect() }.onFailure { Logger.w(it) { "Legacy DFU: Error during disconnect" } }
        transportScope.cancel()
    }

    // ---------------------------------------------------------------------------
    // Low-level GATT helpers
    // ---------------------------------------------------------------------------

    private suspend fun writeControlPoint(payload: ByteArray) {
        bleConnection.profile(LegacyDfuUuids.SERVICE) { service ->
            val controlChar = service.characteristic(LegacyDfuUuids.CONTROL_POINT)
            service.write(controlChar, payload, BleWriteType.WITH_RESPONSE)
        }
    }

    private suspend fun writePacket(payload: ByteArray) {
        bleConnection.profile(LegacyDfuUuids.SERVICE) { service ->
            val packetChar = service.characteristic(LEGACY_DFU_PACKET_UUID)
            service.write(packetChar, payload, BleWriteType.WITHOUT_RESPONSE)
        }
    }

    /** Write [data] to the Packet char in 20-byte chunks. Legacy DFU bootloaders cap packet size at 20 bytes. */
    private suspend fun writePacketChunked(data: ByteArray) {
        bleConnection.profile(LegacyDfuUuids.SERVICE) { service ->
            val packetChar = service.characteristic(LEGACY_DFU_PACKET_UUID)
            var pos = 0
            while (pos < data.size) {
                val end = minOf(pos + LEGACY_DFU_PACKET_SIZE, data.size)
                service.write(packetChar, data.copyOfRange(pos, end), BleWriteType.WITHOUT_RESPONSE)
                pos = end
            }
        }
    }

    private suspend fun awaitResponse(timeout: Duration): LegacyDfuResponse = try {
        withTimeout(timeout) {
            // Drain any stray PRNs that arrive before the response we want.
            while (true) {
                val r = notificationChannel.receive()
                if (r !is LegacyDfuResponse.PacketReceipt) return@withTimeout r
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    } catch (_: TimeoutCancellationException) {
        throw DfuException.Timeout("No response from Legacy DFU Control Point after $timeout")
    }

    private suspend fun awaitPacketReceipt(): LegacyDfuResponse.PacketReceipt = try {
        withTimeout(COMMAND_TIMEOUT) {
            while (true) {
                val r = notificationChannel.receive()
                if (r is LegacyDfuResponse.PacketReceipt) return@withTimeout r
                if (r is LegacyDfuResponse.Failure) {
                    throw LegacyDfuException.ProtocolError(r.requestOpcode, r.status)
                }
                // Stray Success or Unknown → ignore.
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    } catch (_: TimeoutCancellationException) {
        throw DfuException.Timeout("No packet receipt notification after $COMMAND_TIMEOUT")
    }

    private fun requireSuccess(expectedOpcode: Byte, response: LegacyDfuResponse) {
        when (response) {
            is LegacyDfuResponse.Success ->
                if (response.requestOpcode != expectedOpcode) {
                    throw DfuException.TransferFailed(
                        "Legacy DFU response opcode mismatch: expected " +
                            "0x${expectedOpcode.toUByte().toString(16).padStart(2, '0')}, " +
                            "got 0x${response.requestOpcode.toUByte().toString(16).padStart(2, '0')}",
                    )
                }
            is LegacyDfuResponse.Failure ->
                throw LegacyDfuException.ProtocolError(response.requestOpcode, response.status)
            else ->
                throw DfuException.TransferFailed(
                    "Unexpected Legacy DFU response for opcode 0x${expectedOpcode.toUByte().toString(16)}: $response",
                )
        }
    }

    // ---------------------------------------------------------------------------
    // Scanning
    // ---------------------------------------------------------------------------

    private suspend fun scanForDevice(predicate: (BleDevice) -> Boolean): BleDevice? = scanForBleDevice(
        scanner = scanner,
        tag = "Legacy DFU",
        serviceUuid = LegacyDfuUuids.SERVICE,
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
        private val START_RESPONSE_TIMEOUT = 30.seconds
        private val VALIDATE_TIMEOUT = 60.seconds
        private val SUBSCRIPTION_SETTLE = 500.milliseconds
        private const val SCAN_RETRY_COUNT = 3
        private val SCAN_RETRY_DELAY = 2.seconds

        /**
         * Wall-clock budget for a full firmware streaming session. Must comfortably exceed the upload duration for the
         * largest expected image at the slowest realistic Legacy DFU throughput (~1-3 KB/s with 20-byte packets). The
         * per-receipt and per-write watchdogs inside the loop catch real stalls; this cap is just a safety net so a
         * hung profile block can't sit forever.
         */
        private val STREAM_TIMEOUT = 15.minutes

        /**
         * Packet-receipt-notification interval (packets between flow-control ACKs). Higher values mean fewer
         * notification round-trips per byte and therefore faster throughput, at the cost of a slightly longer recovery
         * window if a packet is dropped (we have to wait until the next PRN boundary to detect the gap).
         *
         * Set to 30 per the explicit recommendation from the Adafruit OTAFIX bootloader maintainer
         * (https://github.com/oltaco/Adafruit_nRF52_Bootloader_OTAFIX#recommended-ota-dfu-settings — "Number of
         * packets: 30"), which is the bootloader Meshtastic nRF52 devices ship with. Nordic's reference library
         * defaults to 12; values above ~60 are not recommended for any host. Empirically 30 yields ~3x the throughput
         * of PRN=10 on RAK4631 / OTAFIX without provoking OPERATION_FAILED on the bootloader's flash-write path.
         */
        internal const val PRN_INTERVAL_PACKETS = 30

        /**
         * Default Legacy DFU packet size (20 bytes — the original ATT_MTU minus the 3-byte ATT header).
         *
         * Stock Nordic / pre-OTAFIX-2.1 bootloaders only support this size; sending larger packets to those bootloaders
         * overruns the flash-write buffer and silently bricks the device. For OTAFIX 2.1+ bootloaders (detected via
         * `OTAFIX_NAME_SUFFIX`), [computeStreamPacketSize] bumps the per-packet size up to the negotiated MTU (capped
         * by [MAX_HIGH_MTU_PACKET_SIZE]) for a ~12× throughput win.
         */
        internal const val LEGACY_DFU_PACKET_SIZE = 20

        /**
         * Cap on the high-MTU packet size used when an OTAFIX-2.1+ bootloader is detected. The largest ATT MTU the BLE
         * 5.0 LE Data Length extension can give us is 247 bytes (244 of payload), and Adafruit's own flash accumulation
         * buffer is 240 bytes, so capping at 244 keeps each write to one ATT PDU and one flash-write boundary.
         */
        internal const val MAX_HIGH_MTU_PACKET_SIZE = 244

        /**
         * Suffix used by the OTAFIX 2.1+ bootloader on every supported board's DFU-mode advertising name (e.g.
         * `4631_DFU`, `T114_DFU`, `XIAO_DFU`). The 2.0 release advertised generic `AdaDFU`/board names without this
         * suffix, so its presence is a reliable in-band signal that high-MTU is supported.
         */
        internal const val OTAFIX_NAME_SUFFIX = "_DFU"

        /** Minimum DFU Version we support; older bootloaders use the SDK ≤ 6 single-shot init flow. */
        private const val MIN_SUPPORTED_DFU_VERSION = 5

        /**
         * Init packets larger than this are almost certainly Secure-DFU shaped (signed CBOR ≈ 100-300 bytes) rather
         * than legacy (14 B SDK 7 / 32 B SDK 11). 256 leaves comfortable headroom while still catching the obvious
         * misuse case where a Secure `.dat` is fed into the Legacy path.
         */
        internal const val MAX_REASONABLE_LEGACY_INIT_SIZE = 256
    }
}
