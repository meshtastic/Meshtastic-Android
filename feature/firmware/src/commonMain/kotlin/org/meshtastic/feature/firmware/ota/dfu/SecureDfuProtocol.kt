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
@file:Suppress("MagicNumber", "ReturnCount")

package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

// ---------------------------------------------------------------------------
// Nordic Secure DFU – service and characteristic UUIDs
// ---------------------------------------------------------------------------

internal object SecureDfuUuids {
    /** Main DFU service — present in both normal mode (buttonless) and DFU mode. */
    val SERVICE: Uuid = Uuid.parse("0000FE59-0000-1000-8000-00805F9B34FB")

    /** Control Point: write opcodes WITH_RESPONSE, receive notifications. */
    val CONTROL_POINT: Uuid = Uuid.parse("8EC90001-F315-4F60-9FB8-838830DAEA50")

    /** Packet: write firmware/init data WITHOUT_RESPONSE. */
    val PACKET: Uuid = Uuid.parse("8EC90002-F315-4F60-9FB8-838830DAEA50")

    /** Buttonless DFU – no bond required. Write 0x01 to reboot into DFU mode. */
    val BUTTONLESS_NO_BONDS: Uuid = Uuid.parse("8EC90003-F315-4F60-9FB8-838830DAEA50")

    /** Buttonless DFU – bond required variant. */
    val BUTTONLESS_WITH_BONDS: Uuid = Uuid.parse("8EC90004-F315-4F60-9FB8-838830DAEA50")
}

/**
 * Nordic Legacy DFU service UUIDs (also used by Adafruit's `BLEDfu` helper class). Meshtastic firmware exposes this
 * service when built **without** `BLE_DFU_SECURE`. The buttonless trigger is a single write of `0x01` (`START_DFU`) to
 * the Control Point characteristic; the device then disconnects and reboots into the bootloader (which itself runs
 * Secure DFU on modern Adafruit/oltaco bootloaders).
 *
 * Reference: `Adafruit_nRF52_Arduino/libraries/Bluefruit52Lib/src/services/BLEDfu.cpp`.
 */
internal object LegacyDfuUuids {
    /** Legacy DFU service — exposed by app firmware to trigger reboot into the bootloader. */
    val SERVICE: Uuid = Uuid.parse("00001530-1212-EFDE-1523-785FEABCD123")

    /**
     * Control Point: NOTIFY + WRITE. Notifications must be subscribed before writing or the device returns
     * `ATTERR_CPS_CCCD_CONFIG_ERROR`.
     */
    val CONTROL_POINT: Uuid = Uuid.parse("00001531-1212-EFDE-1523-785FEABCD123")
}

/** Secure DFU buttonless trigger: single-byte `0x01` (START_DFU) to FE59 service. */
internal const val BUTTONLESS_ENTER_BOOTLOADER: Byte = 0x01

/**
 * Legacy DFU buttonless trigger payload per Nordic's `LegacyButtonlessDfuImpl.java:53`: `[OP_CODE_START_DFU=0x01,
 * IMAGE_TYPE_APPLICATION=0x04]`. The Adafruit `BLEDfu` (and original Nordic SDK 6.x bootloader) require both bytes —
 * sending only the opcode is a spec violation that some bootloader builds silently drop.
 */
internal val LEGACY_BUTTONLESS_ENTER_BOOTLOADER: ByteArray = byteArrayOf(0x01, 0x04)

// ---------------------------------------------------------------------------
// Protocol opcodes
// ---------------------------------------------------------------------------

internal object DfuOpcode {
    const val CREATE: Byte = 0x01
    const val SET_PRN: Byte = 0x02
    const val CALCULATE_CHECKSUM: Byte = 0x03
    const val EXECUTE: Byte = 0x04
    const val SELECT: Byte = 0x06
    const val ABORT: Byte = 0x0C
    const val RESPONSE_CODE: Byte = 0x60
}

internal object DfuObjectType {
    const val COMMAND: Byte = 0x01 // init packet (.dat)
    const val DATA: Byte = 0x02 // firmware binary (.bin)
}

internal object DfuResultCode {
    const val SUCCESS: Byte = 0x01
    const val OP_CODE_NOT_SUPPORTED: Byte = 0x02
    const val INVALID_PARAMETER: Byte = 0x03
    const val INSUFFICIENT_RESOURCES: Byte = 0x04
    const val INVALID_OBJECT: Byte = 0x05
    const val UNSUPPORTED_TYPE: Byte = 0x07
    const val OPERATION_NOT_PERMITTED: Byte = 0x08
    const val OPERATION_FAILED: Byte = 0x0A
    const val EXT_ERROR: Byte = 0x0B
}

/**
 * Extended error codes returned when [DfuResultCode.EXT_ERROR] (0x0B) is the result code. An additional byte follows in
 * the response payload.
 */
internal object DfuExtendedError {
    const val WRONG_COMMAND_FORMAT: Byte = 0x02
    const val UNKNOWN_COMMAND: Byte = 0x03
    const val INIT_COMMAND_INVALID: Byte = 0x04
    const val FW_VERSION_FAILURE: Byte = 0x05
    const val HW_VERSION_FAILURE: Byte = 0x06
    const val SD_VERSION_FAILURE: Byte = 0x07
    const val SIGNATURE_MISSING: Byte = 0x08
    const val WRONG_HASH_TYPE: Byte = 0x09
    const val HASH_FAILED: Byte = 0x0A
    const val WRONG_SIGNATURE_TYPE: Byte = 0x0B
    const val VERIFICATION_FAILED: Byte = 0x0C
    const val INSUFFICIENT_SPACE: Byte = 0x0D

    fun describe(code: Byte): String = when (code) {
        WRONG_COMMAND_FORMAT -> "Wrong command format"
        UNKNOWN_COMMAND -> "Unknown command"
        INIT_COMMAND_INVALID -> "Init command invalid"
        FW_VERSION_FAILURE -> "FW version failure"
        HW_VERSION_FAILURE -> "HW version failure"
        SD_VERSION_FAILURE -> "SD version failure"
        SIGNATURE_MISSING -> "Signature missing"
        WRONG_HASH_TYPE -> "Wrong hash type"
        HASH_FAILED -> "Hash failed"
        WRONG_SIGNATURE_TYPE -> "Wrong signature type"
        VERIFICATION_FAILED -> "Verification failed"
        INSUFFICIENT_SPACE -> "Insufficient space"
        else -> "Unknown extended error 0x${code.toUByte().toString(16).padStart(2, '0')}"
    }
}

// ---------------------------------------------------------------------------
// Response parsing
// ---------------------------------------------------------------------------

/** Parsed notification from the DFU Control Point characteristic. */
internal sealed class DfuResponse {

    /** Simple success (CREATE, SET_PRN, EXECUTE, ABORT). */
    data class Success(val opcode: Byte) : DfuResponse()

    /** Response to SELECT opcode — carries the current object's state. */
    data class SelectResult(val opcode: Byte, val maxSize: Int, val offset: Int, val crc32: Int) : DfuResponse()

    /** Response to CALCULATE_CHECKSUM — carries accumulated offset + CRC. */
    data class ChecksumResult(val offset: Int, val crc32: Int) : DfuResponse()

    /** The device rejected the opcode with a non-success result code. */
    data class Failure(val opcode: Byte, val resultCode: Byte, val extendedError: Byte? = null) : DfuResponse()

    /** Unrecognised bytes — logged, treated as an error. */
    data class Unknown(val raw: ByteArray) : DfuResponse() {
        override fun equals(other: Any?) = other is Unknown && raw.contentEquals(other.raw)

        override fun hashCode() = raw.contentHashCode()
    }

    companion object {
        fun parse(data: ByteArray): DfuResponse {
            if (data.size < 3 || data[0] != DfuOpcode.RESPONSE_CODE) return Unknown(data)
            val opcode = data[1]
            val result = data[2]
            if (result != DfuResultCode.SUCCESS) {
                // Extract the extended error byte when present (result == 0x0B and byte at index 3).
                val extError = if (result == DfuResultCode.EXT_ERROR && data.size >= 4) data[3] else null
                return Failure(opcode, result, extError)
            }

            return when (opcode) {
                DfuOpcode.SELECT -> {
                    if (data.size < 15) return Failure(opcode, DfuResultCode.INVALID_PARAMETER)
                    SelectResult(
                        opcode = opcode,
                        maxSize = data.readIntLe(3),
                        offset = data.readIntLe(7),
                        crc32 = data.readIntLe(11),
                    )
                }
                DfuOpcode.CALCULATE_CHECKSUM -> {
                    if (data.size < 11) return Failure(opcode, DfuResultCode.INVALID_PARAMETER)
                    ChecksumResult(offset = data.readIntLe(3), crc32 = data.readIntLe(7))
                }
                else -> Success(opcode)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Byte-level helpers
// ---------------------------------------------------------------------------

internal fun ByteArray.readIntLe(offset: Int): Int = (this[offset].toInt() and 0xFF) or
    ((this[offset + 1].toInt() and 0xFF) shl 8) or
    ((this[offset + 2].toInt() and 0xFF) shl 16) or
    ((this[offset + 3].toInt() and 0xFF) shl 24)

internal fun intToLeBytes(value: Int): ByteArray = byteArrayOf(
    (value and 0xFF).toByte(),
    ((value ushr 8) and 0xFF).toByte(),
    ((value ushr 16) and 0xFF).toByte(),
    ((value ushr 24) and 0xFF).toByte(),
)

// ---------------------------------------------------------------------------
// CRC-32 (IEEE 802.3 / PKZIP) — pure Kotlin, no platform dependencies
// ---------------------------------------------------------------------------

internal object DfuCrc32 {
    private val TABLE =
        IntArray(256).also { table ->
            for (n in 0..255) {
                var c = n
                repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
                table[n] = c
            }
        }

    /** Compute CRC-32 over [data], optionally seeding from a previous [seed] (pass prior result). */
    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size - offset, seed: Int = 0): Int {
        var crc = seed.inv()
        for (i in offset until offset + length) {
            crc = (crc ushr 8) xor TABLE[(crc xor data[i].toInt()) and 0xFF]
        }
        return crc.inv()
    }
}

// ---------------------------------------------------------------------------
// DFU zip package contents
// ---------------------------------------------------------------------------

/** Contents extracted from a Nordic DFU .zip package. */
data class DfuZipPackage(
    val initPacket: ByteArray, // .dat  – signed init packet
    val firmware: ByteArray, // .bin  – application binary
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DfuZipPackage) return false
        return initPacket.contentEquals(other.initPacket) && firmware.contentEquals(other.firmware)
    }

    override fun hashCode() = 31 * initPacket.contentHashCode() + firmware.contentHashCode()
}

// ---------------------------------------------------------------------------
// Manifest (kotlinx.serialization)
// ---------------------------------------------------------------------------

@Serializable internal data class DfuManifest(val manifest: DfuManifestContent)

@Serializable
internal data class DfuManifestContent(
    val application: DfuManifestEntry? = null,
    val bootloader: DfuManifestEntry? = null,
    @SerialName("softdevice_bootloader") val softdeviceBootloader: DfuManifestEntry? = null,
    val softdevice: DfuManifestEntry? = null,
) {
    /** First non-null entry in priority order. */
    val primaryEntry: DfuManifestEntry?
        get() = application ?: softdeviceBootloader ?: bootloader ?: softdevice
}

@Serializable
internal data class DfuManifestEntry(
    @SerialName("bin_file") val binFile: String,
    @SerialName("dat_file") val datFile: String,
)

// ---------------------------------------------------------------------------
// Exceptions
// ---------------------------------------------------------------------------

/** Errors specific to the Nordic Secure DFU protocol. */
sealed class DfuException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** BLE connection to the DFU target could not be established or was lost. */
    class ConnectionFailed(message: String, cause: Throwable? = null) : DfuException(message, cause)

    /** The DFU zip package is malformed or missing required entries. */
    class InvalidPackage(message: String) : DfuException(message)

    /** The device returned a DFU error response for a given opcode. */
    class ProtocolError(val opcode: Byte, val resultCode: Byte, val extendedError: Byte? = null) :
        DfuException(
            buildString {
                append("DFU protocol error: opcode=0x${opcode.toUByte().toString(16).padStart(2, '0')} ")
                append("result=0x${resultCode.toUByte().toString(16).padStart(2, '0')}")
                if (extendedError != null) {
                    append(" ext=${DfuExtendedError.describe(extendedError)}")
                }
            },
        )

    /** CRC-32 of the transferred data does not match the device's computed checksum. */
    class ChecksumMismatch(expected: Int, actual: Int) :
        DfuException(
            "CRC-32 mismatch: expected 0x${expected.toUInt().toString(16).padStart(8, '0')} " +
                "got 0x${actual.toUInt().toString(16).padStart(8, '0')}",
        )

    /** A DFU operation did not complete within the expected time window. */
    class Timeout(message: String) : DfuException(message)

    /** Data transfer to the device failed for a non-protocol reason (e.g. BLE write error). */
    class TransferFailed(message: String, cause: Throwable? = null) : DfuException(message, cause)
}
