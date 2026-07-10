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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.feature.firmware.ota.calculateMacPlusOne
import org.meshtastic.feature.firmware.ota.scanForBleDevice
import org.meshtastic.feature.firmware.ota.withDisconnectTripwire
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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
class LegacyDfuTransport
internal constructor(
    private val scanner: BleScanner,
    connectionFactory: BleConnectionFactory,
    private val address: String,
    dispatcher: CoroutineDispatcher,
    private val streamProfile: LegacyDfuStreamProfile = LegacyDfuStreamProfile.NORMAL,
) : DfuUploadTransport {
    private val transportScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val bleConnection = connectionFactory.create(transportScope, "Legacy DFU")

    /**
     * Stream progress captured by the disconnect-tripwire callback. The disconnect watcher runs in a child coroutine on
     * the caller's dispatcher, which is IO in production but may differ in tests. These fields provide visible
     * diagnostic snapshots between the streaming and watcher coroutines. A fresh transport is created per upload
     * attempt, so these never leak across sessions.
     */
    @Volatile private var streamOffset: Int = 0

    @Volatile private var streamLastPrnOffset: Int = -1

    @Volatile private var streamLastPrnLatencyMs: Long = -1

    /** Receives parsed responses from the Control Point characteristic. */
    private val notificationChannel = Channel<LegacyDfuResponse>(Channel.UNLIMITED)

    /** Name advertised by the device in DFU mode (e.g. `4631_DFU`). Captured in [connectToDfuMode]. */
    private var dfuAdvertisedName: String? = null

    /**
     * DFU Version reported by the optional DFU Version characteristic during [connectToDfuMode]. `-1` when the
     * characteristic is absent or unreadable. Captured here so the stream-start log can include it without re-reading.
     */
    private var dfuVersion: Int = -1

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
                .observe(controlChar) {
                    Logger.d { "Legacy DFU: Control Point subscribed" }
                    subscribed.complete(Unit)
                }
                .onEach { bytes ->
                    val parsed = LegacyDfuResponse.parse(bytes)
                    Logger.d { "Legacy DFU: Notification → $parsed" }
                    notificationChannel.trySend(parsed)
                }
                .catch { e ->
                    if (!subscribed.isCompleted) subscribed.completeExceptionally(e)
                    Logger.e(e) { "Legacy DFU: Control Point notification error" }
                }
                .launchIn(this)

            subscribed.await()
            // Conservative settle after CCCD confirmation before issuing commands.
            delay(SUBSCRIPTION_SETTLE)

            // Best-effort DFU Version read — gate out unsupported old bootloaders (SDK ≤ 6).
            val versionChar = service.characteristic(LEGACY_DFU_VERSION_UUID)
            val version =
                safeCatching { service.read(versionChar) }
                    .map { bytes ->
                        if (bytes.size >= 2) (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8) else -1
                    }
                    .getOrElse { -1 }
            dfuVersion = version
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
     * 10. `ACTIVATE_AND_RESET` — an ordinary disconnect/write Exception may occur because the device resets before the
     *     acknowledgement; treat that operational Exception as expected success (structured cancellation and Error
     *     subtypes still propagate).
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
            handleStartResponse(awaitResponse(START_RESPONSE_TIMEOUT))

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
            writeControlPoint(legacyPrnRequestPayload(streamProfile.prnIntervalPackets))

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
            // The device may reset before the GATT write ACK lands; an ordinary disconnect/write Exception is expected
            // because of that reset — safeCatching treats it as success. Structured cancellation and Error subtypes
            // still propagate.
            Logger.i { "Legacy DFU: Sending ACTIVATE_AND_RESET (disconnect during write is expected)" }
            safeCatching { writeControlPoint(byteArrayOf(LegacyDfuOpcode.ACTIVATE_AND_RESET)) }
                .onFailure { Logger.i(it) { "Legacy DFU: ACTIVATE write reported failure (expected on reset)" } }

            onProgress(1f)
            Logger.i { "Legacy DFU: Upload complete, device rebooting into new firmware." }
        }

    /**
     * Low-speed when the bootloader did not negotiate a larger MTU, leaving us on the 20-byte packet floor. Valid once
     * [connectToDfuMode] has established the connection (the MTU is known by then).
     */
    override val isLowSpeedTransfer: Boolean
        get() = computeStreamPacketSize() <= LEGACY_DFU_PACKET_SIZE

    /**
     * Determine the per-packet size for firmware streaming.
     *
     * Uses the negotiated ATT MTU − 3 (the largest `WITHOUT_RESPONSE` write the link allows), capped at
     * [MAX_HIGH_MTU_PACKET_SIZE] and floored to a 4-byte boundary. This is **self-gating**: a bootloader that cannot
     * accept large DFU writes never negotiates a large MTU, so we fall back to the 20-byte [LEGACY_DFU_PACKET_SIZE]
     * floor. The advertised name (`AdaDFU` vs OTAFIX `_DFU`) is NOT used — the negotiated MTU is the direct capability
     * signal. The Adafruit nRF52 bootloader's DFU data characteristic accepts up to MTU−3 = 244 bytes (it copies the
     * actual write length into a 600-byte pool buffer), but **rejects any write whose length is not a multiple of 4**
     * (`BLE_DFU_RESP_VAL_NOT_SUPPORTED`) — hence the word-alignment floor.
     */
    private fun computeStreamPacketSize(): Int {
        val negotiated =
            bleConnection.maximumWriteValueLength(BleWriteType.WITHOUT_RESPONSE) ?: return LEGACY_DFU_PACKET_SIZE
        val sized = negotiated.coerceIn(LEGACY_DFU_PACKET_SIZE, MAX_HIGH_MTU_PACKET_SIZE)
        return sized - (sized % DFU_PACKET_WORD_ALIGNMENT)
    }

    /**
     * Stream [firmware] to the Packet characteristic under a single outer disconnect tripwire. The outer watcher is the
     * sole streaming disconnect classifier — PRN waits inside the loop intentionally do NOT install another tripwire,
     * so a link drop during a PRN wait surfaces as a typed [LegacyDfuException.MidStreamDisconnect] carrying host-side
     * and confirmed-progress diagnostics, not a generic handshake-style [DfuException.ConnectionFailed].
     *
     * Every [streamProfile.prnIntervalPackets] packets the loop awaits a [LegacyDfuResponse.PacketReceipt] and verifies
     * the bootloader's bytes-received count. The [streamOffset], [streamLastPrnOffset], and [streamLastPrnLatencyMs]
     * snapshots give the watcher's onDrop callback visible diagnostic values.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
    private suspend fun streamFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit) {
        // Packet size = negotiated ATT MTU − 3, word-aligned and capped at 244 (see computeStreamPacketSize). Falls
        // back to 20 bytes when the bootloader did not negotiate a larger MTU, which is the self-gating safety against
        // bootloaders that can't accept large DFU writes — the 20-byte path is the slow but universally-safe default.
        val mtu = computeStreamPacketSize()
        val prnInterval = streamProfile.prnIntervalPackets
        // Reset stream progress for this attempt. These are @Volatile instance properties so the disconnect-tripwire
        // callback (running in a child coroutine on the caller's dispatcher) has a visible snapshot.
        streamOffset = 0
        streamLastPrnOffset = -1
        streamLastPrnLatencyMs = -1
        Logger.i {
            "Legacy DFU: Streaming ${firmware.size} bytes with packet size $mtu " +
                "(advertised='${dfuAdvertisedName ?: "?"}', dfuVersion=$dfuVersion, " +
                "profile=$streamProfile, prnInterval=$prnInterval)"
        }

        bleConnection.withDisconnectTripwire(
            onDrop = { state ->
                Logger.w {
                    "Legacy DFU: Link dropped mid-stream at host in-flight offset $streamOffset/${firmware.size} " +
                        "(state=$state, lastConfirmedPrn=$streamLastPrnOffset, lastPrnLatencyMs=$streamLastPrnLatencyMs)"
                }
                LegacyDfuException.MidStreamDisconnect(
                    bytesSent = streamOffset,
                    totalBytes = firmware.size,
                    connectionState = state.toString(),
                    lastConfirmedBytes = streamLastPrnOffset,
                )
            },
        ) {
            var packetsSincePrn = 0
            var bytesAtLastPrn = 0L
            bleConnection.profile(LegacyDfuUuids.SERVICE, timeout = STREAM_TIMEOUT) { service ->
                val packetChar = service.characteristic(LEGACY_DFU_PACKET_UUID)
                while (streamOffset < firmware.size) {
                    val end = minOf(streamOffset + mtu, firmware.size)
                    // Publish the in-flight boundary BEFORE invoking service.write() so the disconnect watcher
                    // observes the current attempted chunk when a disconnect fires synchronously inside the write
                    // (e.g. a test harness or a real bootloader that drops on receive). The write may not complete
                    // and the host stack may not accept it; this boundary is the dispatched boundary, not a
                    // confirmed one — the authoritative checkpoint is the PRN-confirmed offset (streamLastPrnOffset).
                    val chunk = firmware.copyOfRange(streamOffset, end)
                    streamOffset = end
                    try {
                        service.write(packetChar, chunk, BleWriteType.WITHOUT_RESPONSE)
                    } catch (e: CancellationException) {
                        Logger.w(e) {
                            "Legacy DFU: Write CANCELLED at offset $streamOffset/${firmware.size} cause=${e.cause}"
                        }
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                        Logger.w(e) {
                            "Legacy DFU: Write FAILED at offset $streamOffset/${firmware.size}: ${e.message}"
                        }
                        throw e
                    }
                    packetsSincePrn++

                    if (packetsSincePrn >= prnInterval && streamOffset < firmware.size) {
                        val awaitMark = TimeSource.Monotonic.markNow()
                        Logger.d { "Legacy DFU: Awaiting PRN at offset $streamOffset" }
                        val receipt =
                            try {
                                awaitPacketReceiptDuringStream()
                            } catch (e: CancellationException) {
                                Logger.w(e) {
                                    "Legacy DFU: awaitPacketReceiptDuringStream CANCELLED at offset $streamOffset " +
                                        "cause=${e.cause}"
                                }
                                throw e
                            }
                        val latencyMs = awaitMark.elapsedNow().inWholeMilliseconds
                        if (latencyMs >= PRN_LATENCY_WARN_THRESHOLD_MS) {
                            Logger.w {
                                "Legacy DFU: PRN receipt latency ${latencyMs}ms at offset $streamOffset " +
                                    "(>= ${PRN_LATENCY_WARN_THRESHOLD_MS}ms — possible bootloader backpressure or BLE scheduling delay)"
                            }
                        } else {
                            Logger.d { "Legacy DFU: PRN receipt at offset $streamOffset latency=${latencyMs}ms" }
                        }
                        val expected = streamOffset.toLong()
                        if (receipt.bytesReceived != expected) {
                            throw LegacyDfuException.PacketReceiptMismatch(expected, receipt.bytesReceived)
                        }
                        // Record the checkpoint only after the receipt validates — a mismatched PRN must
                        // not be reported as the last successful checkpoint in the link-drop log.
                        streamLastPrnOffset = streamOffset
                        streamLastPrnLatencyMs = latencyMs
                        bytesAtLastPrn = receipt.bytesReceived
                        packetsSincePrn = 0
                        onProgress(streamOffset.toFloat() / firmware.size)
                    }
                }
            }
            Logger.d { "Legacy DFU: Streamed $streamOffset/${firmware.size} bytes (lastPRN=$bytesAtLastPrn)" }
        }
    }

    // ---------------------------------------------------------------------------
    // Abort & teardown
    // ---------------------------------------------------------------------------

    /**
     * Send `RESET` to the device, instructing it to discard any in-progress transfer and reboot. Best-effort — the
     * device may disconnect before the write ACK lands; that's expected.
     *
     * The RESET op remains a `WITH_RESPONSE` write (the bootloader is contractually allowed to act on it before the
     * GATT ACK lands, but we still request one so the link-layer queues it reliably), but we bound the wait with a
     * short timeout. A missing acknowledgement is ambiguous: the device may have accepted RESET and rebooted, become
     * unresponsive, disconnected, or simply failed to receive or complete the write. We report the outcome
     * (acknowledged, unacknowledged, or operational failure) without claiming the RESET landed. Operational Exceptions
     * are best-effort and non-fatal; structured-concurrency cancellation and Error subtypes propagate. The caller
     * (`SecureDfuHandler`) tears the connection down afterwards regardless.
     *
     * Parent cancellation is preserved: a [CancellationException] that escapes `withTimeoutOrNull` is propagated.
     */
    override suspend fun abort() {
        val write =
            try {
                withTimeoutOrNull(RESET_WRITE_TIMEOUT) { writeControlPoint(byteArrayOf(LegacyDfuOpcode.RESET)) }
            } catch (e: CancellationException) {
                Logger.w(e) { "Legacy DFU: RESET write cancelled; disconnecting" }
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.w(e) { "Legacy DFU: RESET write failed; disconnecting" }
                return
            }
        if (write != null) {
            Logger.i { "Legacy DFU: RESET write acknowledged; disconnecting" }
        } else {
            Logger.w { "Legacy DFU: RESET write unacknowledged within the timeout; disconnecting" }
        }
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
            // Fail fast + accurately if the bootloader drops the link mid-handshake instead of answering.
            // Some Legacy bootloader variants disconnect on the first Control Point command
            // (e.g. stale-bond encryption mismatch); without this the receive() below just blocks until
            // `timeout`, so the user waited for the full command timeout and saw a misleading
            // "No response from Control Point" for what was really an immediate disconnect.
            bleConnection.withDisconnectTripwire(onDrop = ::handshakeDropError) {
                // Drain any stray PRNs that arrive before the response we want.
                while (true) {
                    val r = notificationChannel.receive()
                    if (r !is LegacyDfuResponse.PacketReceipt) return@withDisconnectTripwire r
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
        }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw DfuException.Timeout("No response from Legacy DFU Control Point after $timeout")
    }

    /**
     * PRN wait used inside [streamFirmware]. Deliberately does NOT install its own [withDisconnectTripwire]:
     * [streamFirmware] owns the sole disconnect watcher during PRN waits, and a nested tripwire here would race it and
     * could surface a generic [DfuException.ConnectionFailed] from [handshakeDropError] instead of the typed
     * [LegacyDfuException.MidStreamDisconnect] that drives the recovery profile. The handshake-level disconnect watcher
     * lives in [awaitResponse] (its [withDisconnectTripwire] classifies link drops during Control Point responses).
     *
     * A [TimeoutCancellationException] may be raised by an outer [withTimeout] (structured cancellation) rather than
     * the local 30 s missing-PRN timeout. We re-check [ensureActive] first so parent/outer cancellation is rethrown,
     * and only then surface the local [DfuException.Timeout].
     */
    private suspend fun awaitPacketReceiptDuringStream(): LegacyDfuResponse.PacketReceipt = try {
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
        currentCoroutineContext().ensureActive()
        throw DfuException.Timeout("No packet receipt notification after $COMMAND_TIMEOUT")
    }

    /** Error for a link drop while awaiting a Control Point response — distinguishes a disconnect from a true stall. */
    private fun handshakeDropError(state: BleConnectionState): Throwable = DfuException.ConnectionFailed(
        "BLE link dropped during DFU handshake (state=$state). The device disconnected before answering a Legacy DFU " +
            "command; some bootloader variants reboot to the app or drop the link in this state.",
    )

    /**
     * Validate the `START_DFU` response, with special handling for `INVALID_STATE`.
     *
     * A device whose previous DFU session was interrupted (link drop, app closed mid-stream) keeps its half-finished
     * transfer state and rejects a fresh `START_DFU` with `INVALID_STATE` until it is reset. This is the common case
     * when *recovering* a device stranded in the bootloader.
     *
     * We do NOT try to RESET on this connection: some Legacy bootloader variants become unresponsive after returning
     * INVALID_STATE. Skip a potentially blocking same-connection RESET and use the bounded fresh-connection reset-prime
     * path instead. We fast-fail with [LegacyDfuException.StaleSessionReset]; [SecureDfuHandler] then resets the
     * bootloader over a *fresh* connection (which is responsive up until START) before retrying. Mirrors the intent of
     * Nordic `LegacyDfuImpl.resetAndRestart()`.
     */
    private fun handleStartResponse(response: LegacyDfuResponse) {
        if (response is LegacyDfuResponse.Failure && response.status == LegacyDfuStatus.INVALID_STATE) {
            Logger.w { "Legacy DFU: START rejected with INVALID_STATE (stale session from an interrupted flash)" }
            throw LegacyDfuException.StaleSessionReset()
        }
        requireSuccess(LegacyDfuOpcode.START_DFU, response)
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
        predicate = predicate,
    )

    // ---------------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------------

    companion object {
        private val CONNECT_TIMEOUT = 15.seconds
        private val COMMAND_TIMEOUT = 30.seconds

        /**
         * Best-effort timeout around the Legacy `RESET` (`0x06`) Control-Point write. Legacy bootloaders may disconnect
         * before the RESET write acknowledgement returns. A missing acknowledgement is ambiguous: the device may have
         * accepted RESET and rebooted, become unresponsive, disconnected, or failed to receive/complete the write.
         * Bound the acknowledgement wait to one second, report it as unacknowledged, then rely on connection teardown
         * and the subsequent retry/re-advertisement flow.
         */
        internal val RESET_WRITE_TIMEOUT = 1.seconds

        /**
         * Time to wait for the START_DFU response notification.
         *
         * The stock Adafruit nRF52 bootloader is single-bank: on START it erases the **entire** application bank (~800
         * KB ≈ 200 flash pages) before firing the START-procedure response, and because the BLE link is live the
         * SoftDevice time-slices each page erase against radio events, stretching the erase to ~30-50 s. The old 30 s
         * cap aborted mid-erase (killing an otherwise-healthy session); Nordic's own DFU library imposes no such short
         * cap here. 90 s covers the worst-case erase with margin. The disconnect tripwire still fast-fails on a genuine
         * link drop, so this only extends the *silent-but-connected* wait.
         */
        private val START_RESPONSE_TIMEOUT = 90.seconds
        private val VALIDATE_TIMEOUT = 60.seconds
        private val SUBSCRIPTION_SETTLE = 500.milliseconds

        /**
         * Wall-clock budget for a full firmware streaming session. Must comfortably exceed the upload duration for the
         * largest expected image at the slowest realistic Legacy DFU throughput (~1-3 KB/s with 20-byte packets). The
         * per-receipt and per-write watchdogs inside the loop catch real stalls; this cap is just a safety net so a
         * hung profile block can't sit forever.
         */
        private val STREAM_TIMEOUT = 15.minutes

        /**
         * Per-receipt latency threshold above which a PRN wait is logged at WARN. The Legacy bootloader normally ACKs
         * each PRN window inside a few tens of milliseconds. A latency at or above this threshold is diagnostic only —
         * it may indicate bootloader backpressure, flash activity, BLE scheduling delay, or degraded link conditions.
         * It is not by itself a definite precursor to a supervision-timeout drop. Below this threshold receipts log at
         * DEBUG.
         */
        internal const val PRN_LATENCY_WARN_THRESHOLD_MS = 1_000L

        /**
         * Universally-safe Legacy DFU packet size (20 bytes — the original ATT_MTU minus the 3-byte ATT header). Used
         * as the floor when the link did not negotiate a larger MTU; [computeStreamPacketSize] raises the per-packet
         * size to the negotiated MTU (capped by [MAX_HIGH_MTU_PACKET_SIZE]) for a ~12× throughput win when available.
         */
        internal const val LEGACY_DFU_PACKET_SIZE = 20

        /**
         * Cap on the high-MTU DFU packet size. The largest ATT MTU the BLE 5.0 LE Data Length extension gives us is 247
         * bytes (244 of payload); the Adafruit bootloader's DFU pool buffer (600 B) comfortably holds it, so 244 keeps
         * each write to one ATT PDU. Stays a multiple of 4 — the bootloader rejects non-word-aligned writes.
         */
        internal const val MAX_HIGH_MTU_PACKET_SIZE = 244

        /** DFU data writes must be a whole number of 32-bit words; [computeStreamPacketSize] floors to this. */
        private const val DFU_PACKET_WORD_ALIGNMENT = 4

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
