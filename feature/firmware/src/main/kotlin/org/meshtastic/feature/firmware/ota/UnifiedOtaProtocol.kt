/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.firmware.ota

/** Commands supported by the ESP32 Unified OTA protocol. All commands are text-based and terminated with '\n'. */
sealed class OtaCommand {
    /** Request device version information */
    data object Version : OtaCommand() {
        override fun toString() = "VERSION\n"
    }

    /** Start OTA update with firmware size and SHA-256 hash */
    data class StartOta(val sizeBytes: Long, val sha256Hash: String) : OtaCommand() {
        override fun toString() = "OTA $sizeBytes $sha256Hash\n"
    }

    /** Request device reboot */
    data object Reboot : OtaCommand() {
        override fun toString() = "REBOOT\n"
    }
}

/** Responses from the ESP32 Unified OTA protocol. */
sealed class OtaResponse {
    /** Successful response with optional data */
    data class Ok(
        val hwVersion: String? = null,
        val fwVersion: String? = null,
        val rebootCount: Int? = null,
        val gitHash: String? = null,
    ) : OtaResponse()

    /** Device is erasing flash partition (sent before OK after OTA command) */
    data object Erasing : OtaResponse()

    /** Acknowledgment for received data chunk (BLE only) */
    data object Ack : OtaResponse()

    /** Error response with message */
    data class Error(val message: String) : OtaResponse()

    companion object {
        private const val OK_PREFIX_LENGTH = 3
        private const val ERR_PREFIX_LENGTH = 4
        private const val VERSION_PARTS_COUNT = 4

        /**
         * Parse a response string from the device. Format examples:
         * - "OK\n"
         * - "OK 1 2.3.4 45 v2.3.4-abc123\n"
         * - "ERASING\n"
         * - "ACK\n"
         * - "ERR Hash Rejected\n"
         */
        fun parse(response: String): OtaResponse {
            val trimmed = response.trim()

            return when {
                trimmed == "OK" -> Ok()
                trimmed.startsWith("OK ") -> {
                    val parts = trimmed.substring(OK_PREFIX_LENGTH).split(" ")
                    when (parts.size) {
                        VERSION_PARTS_COUNT ->
                            Ok(
                                hwVersion = parts[0],
                                fwVersion = parts[1],
                                rebootCount = parts[2].toIntOrNull(),
                                gitHash = parts[3],
                            )
                        else -> Ok()
                    }
                }
                trimmed == "ERASING" -> Erasing
                trimmed == "ACK" -> Ack
                trimmed.startsWith("ERR ") -> Error(trimmed.substring(ERR_PREFIX_LENGTH))
                trimmed == "ERR" -> Error("Unknown error")
                else -> Error("Unknown response: $trimmed")
            }
        }
    }
}

/** Interface for ESP32 Unified OTA protocol implementation. Supports both BLE and WiFi/TCP transports. */
interface UnifiedOtaProtocol {
    /**
     * Connect to the device and discover OTA service/establish connection.
     *
     * @return Success if connected and ready, error otherwise
     */
    suspend fun connect(): Result<Unit>

    /**
     * Send VERSION command to get device information.
     *
     * @return Version information from the device
     */
    suspend fun sendVersion(): Result<OtaResponse.Ok>

    /**
     * Start OTA update process.
     *
     * @param sizeBytes Total firmware size in bytes
     * @param sha256Hash SHA-256 hash of the firmware (64 hex characters)
     * @param onStatus Optional callback to report status changes (e.g., "Erasing...")
     * @return Success if device accepts and is ready, error otherwise
     */
    suspend fun startOta(sizeBytes: Long, sha256Hash: String, onStatus: suspend (String) -> Unit = {}): Result<Unit>

    /**
     * Stream firmware binary data to the device.
     *
     * @param data Complete firmware binary
     * @param chunkSize Size of each chunk to send (256-512 for BLE, up to 1024 for WiFi)
     * @param onProgress Progress callback (0.0 to 1.0)
     * @return Success if all data transferred and verified, error otherwise
     */
    suspend fun streamFirmware(data: ByteArray, chunkSize: Int, onProgress: suspend (Float) -> Unit): Result<Unit>

    /**
     * Request device reboot.
     *
     * @return Success if reboot command accepted
     */
    suspend fun reboot(): Result<Unit>

    /** Close the connection and cleanup resources. */
    suspend fun close()
}

/** Exception thrown during OTA protocol operations. */
sealed class OtaProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ConnectionFailed(message: String, cause: Throwable? = null) : OtaProtocolException(message, cause)

    class CommandFailed(val command: OtaCommand, val response: OtaResponse.Error) :
        OtaProtocolException("Command $command failed: ${response.message}")

    class HashRejected(val providedHash: String) :
        OtaProtocolException("Device rejected hash: $providedHash (NVS mismatch)")

    class TransferFailed(message: String, cause: Throwable? = null) : OtaProtocolException(message, cause)

    class VerificationFailed(message: String) : OtaProtocolException(message)

    class Timeout(message: String) : OtaProtocolException(message)
}
