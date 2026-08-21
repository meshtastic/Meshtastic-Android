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
package org.meshtastic.feature.firmware.ota.dfu

/**
 * Common upload-time surface implemented by both [SecureDfuTransport] (Nordic Secure DFU, service `FE59`) and
 * [LegacyDfuTransport] (Nordic Legacy DFU / Adafruit BLEDfu, service `1530`).
 *
 * The choice of which implementation to use is made by the handler after the device reboots into bootloader mode, based
 * on which DFU service is exposed.
 */
interface DfuUploadTransport {
    /**
     * True once connected if the bootloader can only transfer in small (≤20-byte) blocks — i.e. it did not negotiate a
     * larger ATT MTU. Used to surface a "slow bootloader" advisory to the user. Valid after [connectToDfuMode].
     */
    val isLowSpeedTransfer: Boolean
        get() = false

    /** Establish the GATT session with the device in DFU mode. */
    suspend fun connectToDfuMode(): Result<Unit>

    /** Upload the init packet (`.dat`) and have the device validate it. */
    suspend fun transferInitPacket(initPacket: ByteArray): Result<Unit>

    /**
     * Upload the firmware binary (`.bin`). [onProgress] is invoked with a value in `[0.0, 1.0]` after each protocol
     * checkpoint (PRN window or end-of-image).
     */
    suspend fun transferFirmware(firmware: ByteArray, onProgress: suspend (Float) -> Unit): Result<Unit>

    /**
     * As [transferFirmware], additionally reporting coarse [DfuUploadPhase] transitions so the UI can explain a wait
     * that produces no progress (Legacy DFU erases the whole application region before it accepts a single byte).
     * Transports without a meaningful prepare step keep the default, which never reports a phase.
     */
    suspend fun transferFirmware(
        firmware: ByteArray,
        onPhase: suspend (DfuUploadPhase) -> Unit,
        onProgress: suspend (Float) -> Unit,
    ): Result<Unit> = transferFirmware(firmware, onProgress)

    /**
     * Best-effort abort. Operational transport exceptions are swallowed; structured-concurrency cancellation and Error
     * subtypes propagate.
     */
    suspend fun abort()

    /** Disconnect and release resources. */
    suspend fun close()
}

/** Coarse phases inside [DfuUploadTransport.transferFirmware], for UI that must explain a silent wait. */
enum class DfuUploadPhase {
    /** Start request sent; the bootloader is erasing flash and will not accept data until it has finished. */
    PREPARING,

    /** The bootloader accepted the start; firmware bytes are streaming and progress callbacks follow. */
    STREAMING,
}
