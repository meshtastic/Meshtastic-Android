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
}
