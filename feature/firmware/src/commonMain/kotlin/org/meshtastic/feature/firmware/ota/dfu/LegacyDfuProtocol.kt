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

import kotlin.uuid.Uuid

// ---------------------------------------------------------------------------
// Nordic Legacy DFU – additional service characteristic UUIDs
// (Service + Control Point are declared in `LegacyDfuUuids` in
// SecureDfuProtocol.kt — they're shared with the Phase-1 buttonless trigger.)
// ---------------------------------------------------------------------------

/** Packet characteristic — accepts WRITE_NO_RESPONSE for image sizes, init data, and firmware bytes. */
internal val LEGACY_DFU_PACKET_UUID: Uuid = Uuid.parse("00001532-1212-EFDE-1523-785FEABCD123")

/**
 * DFU Version characteristic — optional; uint16 LE giving bootloader DFU version. Used to gate the extended init-packet
 * flow (≥ 5 ⇒ START/COMPLETE bracket; ≤ 4 ⇒ unsupported old SDK).
 */
internal val LEGACY_DFU_VERSION_UUID: Uuid = Uuid.parse("00001534-1212-EFDE-1523-785FEABCD123")

// ---------------------------------------------------------------------------
// Protocol opcodes (Nordic SDK 11/12 / Adafruit BLEDfu)
// ---------------------------------------------------------------------------

internal object LegacyDfuOpcode {
    const val START_DFU: Byte = 0x01
    const val INIT_DFU_PARAMS: Byte = 0x02
    const val RECEIVE_FIRMWARE_IMAGE: Byte = 0x03
    const val VALIDATE: Byte = 0x04
    const val ACTIVATE_AND_RESET: Byte = 0x05
    const val RESET: Byte = 0x06
    const val PACKET_RECEIPT_NOTIF_REQ: Byte = 0x08

    /** Prefix on every Control-Point response notification. */
    const val RESPONSE_CODE: Byte = 0x10

    /** Prefix on every Packet-Receipt notification (carries `[bytes_received_u32_le]`). */
    const val PACKET_RECEIPT: Byte = 0x11

    /** Sub-opcode of `INIT_DFU_PARAMS`: marks beginning of init-packet stream. */
    const val INIT_PARAMS_START: Byte = 0x00

    /** Sub-opcode of `INIT_DFU_PARAMS`: marks end of init-packet stream. */
    const val INIT_PARAMS_COMPLETE: Byte = 0x01
}

/**
 * `START_DFU` image-type bitmask. Meshtastic only ever ships application updates over OTA, so the transport hard-codes
 * [APPLICATION].
 */
internal object LegacyDfuImageType {
    const val SOFT_DEVICE: Byte = 0x01
    const val BOOTLOADER: Byte = 0x02
    const val APPLICATION: Byte = 0x04
}

/** Result codes returned in the third byte of a response notification. */
internal object LegacyDfuStatus {
    const val SUCCESS: Byte = 0x01
    const val INVALID_STATE: Byte = 0x02
    const val NOT_SUPPORTED: Byte = 0x03
    const val DATA_SIZE_EXCEEDS_LIMIT: Byte = 0x04
    const val CRC_ERROR: Byte = 0x05
    const val OPERATION_FAILED: Byte = 0x06

    fun describe(status: Byte): String = when (status) {
        SUCCESS -> "SUCCESS"
        INVALID_STATE -> "INVALID_STATE"
        NOT_SUPPORTED -> "NOT_SUPPORTED"
        DATA_SIZE_EXCEEDS_LIMIT -> "DATA_SIZE_EXCEEDS_LIMIT"
        CRC_ERROR -> "CRC_ERROR"
        OPERATION_FAILED -> "OPERATION_FAILED"
        else -> "UNKNOWN(0x${status.toUByte().toString(16).padStart(2, '0')})"
    }
}

// ---------------------------------------------------------------------------
// Response parsing
// ---------------------------------------------------------------------------

/** Parsed notification from the Legacy DFU Control Point characteristic. */
internal sealed class LegacyDfuResponse {

    /** `[0x10, requestOpcode, 0x01]` — request succeeded. */
    data class Success(val requestOpcode: Byte) : LegacyDfuResponse()

    /** `[0x10, requestOpcode, status]` where `status != 0x01` — device rejected the request. */
    data class Failure(val requestOpcode: Byte, val status: Byte) : LegacyDfuResponse()

    /** `[0x11, bytes_received_u32_le]` — periodic packet-receipt notification. */
    data class PacketReceipt(val bytesReceived: Long) : LegacyDfuResponse()

    /** Unrecognised bytes — logged, surfaced as a protocol error. */
    data class Unknown(val raw: ByteArray) : LegacyDfuResponse() {
        override fun equals(other: Any?) = other is Unknown && raw.contentEquals(other.raw)

        override fun hashCode() = raw.contentHashCode()
    }

    companion object {
        @Suppress("ReturnCount")
        fun parse(data: ByteArray): LegacyDfuResponse {
            if (data.isEmpty()) return Unknown(data)
            return when (data[0]) {
                LegacyDfuOpcode.RESPONSE_CODE -> {
                    if (data.size < 3) return Unknown(data)
                    val requestOpcode = data[1]
                    val status = data[2]
                    if (status == LegacyDfuStatus.SUCCESS) {
                        Success(requestOpcode)
                    } else {
                        Failure(requestOpcode, status)
                    }
                }

                LegacyDfuOpcode.PACKET_RECEIPT -> {
                    if (data.size < 5) return Unknown(data)
                    val bytes =
                        (data[1].toLong() and 0xFF) or
                            ((data[2].toLong() and 0xFF) shl 8) or
                            ((data[3].toLong() and 0xFF) shl 16) or
                            ((data[4].toLong() and 0xFF) shl 24)
                    PacketReceipt(bytes)
                }

                else -> Unknown(data)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Payload builders
// ---------------------------------------------------------------------------

/**
 * Build the 12-byte image-sizes payload written to the Packet characteristic immediately after `START_DFU`.
 *
 * Layout: `[soft_device_size_u32_le, bootloader_size_u32_le, app_size_u32_le]`. Meshtastic only updates the
 * application, so [softDeviceSize] and [bootloaderSize] default to 0.
 */
internal fun legacyImageSizesPayload(appSize: Int, softDeviceSize: Int = 0, bootloaderSize: Int = 0): ByteArray =
    intToLeBytes(softDeviceSize) + intToLeBytes(bootloaderSize) + intToLeBytes(appSize)

/** Build the 3-byte `PACKET_RECEIPT_NOTIF_REQ` payload: opcode + uint16-LE PRN value. */
internal fun legacyPrnRequestPayload(packets: Int): ByteArray = byteArrayOf(
    LegacyDfuOpcode.PACKET_RECEIPT_NOTIF_REQ,
    (packets and 0xFF).toByte(),
    ((packets ushr 8) and 0xFF).toByte(),
)

// ---------------------------------------------------------------------------
// Stream profiles
// ---------------------------------------------------------------------------

/**
 * Default packet-receipt-notification interval (packets between flow-control ACKs). Higher values mean fewer
 * notification round-trips per byte and therefore faster throughput, at the cost of a slightly longer recovery window
 * if a packet is dropped.
 *
 * Capped at 10 to match Nordic's own Legacy DFU implementation, which force-limits legacy PRN to ≤10 with the comment:
 * "DFU bootloaders from SDK 6.0.0 or older were unable to save incoming data to flash as fast as they are being sent …
 * PRN = 10 may be the highest supported value" and treats status 6 (OPERATION_FAILED) as "data sent too fast — reduce
 * PRN to 10 or less." The stock Adafruit bootloader shares this SDK11 flash-write path, so a higher value risks
 * OPERATION_FAILED mid-stream on stock bootloaders. 10 is the safe ceiling that still batches flow-control ACKs.
 */
internal const val PRN_INTERVAL_PACKETS = 10

/**
 * Tighter PRN interval used only on a recovery upload that follows a mid-stream disconnect. Halving the interval
 * reduces the burst depth between flow-control checkpoints. Used exclusively by [LegacyDfuStreamProfile.RECOVERY] as a
 * recovery heuristic; it does not by itself prevent a bootloader-side stall.
 */
internal const val RECOVERY_PRN_INTERVAL_PACKETS = 5

/**
 * Legacy upload profile. Selects the PRN interval used in BOTH the `PACKET_RECEIPT_NOTIF_REQ` request and the
 * receipt-await threshold inside [org.meshtastic.feature.firmware.ota.dfu.LegacyDfuTransport.streamFirmware].
 * - [NORMAL]: the healthy first-attempt transfer profile — fewer PRN round-trips, higher throughput.
 * - [RECOVERY]: selected after a [LegacyDfuException.MidStreamDisconnect] so subsequent attempts on the same run use
 *   the tighter interval. A [LegacyDfuException.StaleSessionReset] alone does NOT switch the profile, since the link
 *   did not actually drop mid-upload.
 */
internal enum class LegacyDfuStreamProfile(val prnIntervalPackets: Int) {
    NORMAL(PRN_INTERVAL_PACKETS),
    RECOVERY(RECOVERY_PRN_INTERVAL_PACKETS),
}

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------

/**
 * Errors specific to the Nordic Legacy DFU protocol. These are a subtype of [DfuException] so the existing handler
 * error-path code (which catches `DfuException`) covers both protocols.
 */
sealed class LegacyDfuException(message: String, cause: Throwable? = null) : DfuException(message, cause) {
    /** Device returned a non-success status for a given opcode. */
    class ProtocolError(val requestOpcode: Byte, val status: Byte) :
        LegacyDfuException(
            "Legacy DFU protocol error: opcode=0x${requestOpcode.toUByte().toString(16).padStart(2, '0')} " +
                "status=${LegacyDfuStatus.describe(status)}",
        )

    /** Bootloader exposes DFU Version characteristic with a value below 5 (Nordic SDK ≤ 6). Unsupported. */
    class UnsupportedBootloader(version: Int) :
        LegacyDfuException(
            "Legacy DFU bootloader version $version is too old (need ≥ 5). Please update the bootloader.",
        )

    /** Init packet ([dat]) appears to be Secure-DFU shaped (signed/CBOR), not the small legacy 14-32 B form. */
    class InitPacketNotLegacy(size: Int) :
        LegacyDfuException(
            "Init packet is $size bytes — too large for Legacy DFU. " +
                "This .dat looks like a Secure DFU init packet; the bootloader will reject it.",
        )

    /** Bytes received reported by device differs from bytes sent past last PRN window. */
    class PacketReceiptMismatch(expected: Long, actual: Long) :
        LegacyDfuException("Packet receipt mismatch: expected $expected bytes received, device reports $actual")

    /**
     * START_DFU was rejected with INVALID_STATE because the bootloader still holds state from an interrupted session.
     * The current connection is closed without attempting RESET; SecureDfuHandler performs a bounded reset-prime over a
     * fresh connection before retrying.
     */
    class StaleSessionReset :
        LegacyDfuException(
            "Bootloader rejected START with INVALID_STATE (leftover from an interrupted flash); reset and retrying.",
        )

    /**
     * The BLE link dropped while firmware bytes were in flight. Carries the host in-flight offset (the end of the write
     * the host had dispatched or was in flight at the moment of the drop) so the retry coordinator can distinguish a
     * mid-stream failure (which switches subsequent Legacy uploads to [LegacyDfuStreamProfile.RECOVERY]) from a
     * handshake/connect failure (which does not).
     *
     * [bytesSent] is the end offset of the host write currently dispatched or in flight. It may include the current
     * chunk when the disconnect occurs during `service.write()`. It does NOT prove the host stack accepted the chunk,
     * and it does NOT prove the bootloader received or committed it. [lastConfirmedBytes] is the authoritative
     * PRN-confirmed checkpoint (the last byte the bootloader explicitly acknowledged); `-1` means no PRN was received
     * before the drop. Do not treat [bytesSent] as confirmed device progress.
     *
     * Typed — callers must NOT parse the message to detect a mid-stream drop.
     */
    class MidStreamDisconnect(
        val bytesSent: Int,
        val totalBytes: Int,
        val connectionState: String,
        val lastConfirmedBytes: Int,
    ) : LegacyDfuException(
        "BLE link dropped mid-upload at host in-flight offset $bytesSent/$totalBytes " +
            "(lastConfirmed=$lastConfirmedBytes, state=$connectionState)",
    )
}
