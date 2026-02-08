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
package org.meshtastic.core.model.util

import co.touchlab.kermit.Logger
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import okio.ByteString
import okio.ByteString.Companion.toByteString

@Suppress("unused") // These are extension functions meant to be imported elsewhere
fun <T : Message<T, *>> ProtoAdapter<T>.decodeOrNull(bytes: ByteString?, logger: Logger? = null): T? {
    if (bytes == null) return null
    return runCatching { decode(bytes) }
        .onFailure { exception -> logger?.e(exception) { "Failed to decode proto message" } }
        .getOrNull()
}

/**
 * Safely decode a proto message from [ByteArray], returning null on error.
 *
 * Convenience overload for ByteArray inputs, automatically converting to ByteString.
 *
 * @param bytes The ByteArray to decode, or null
 * @param logger Optional logger for error reporting
 * @return The decoded message, or null if bytes is null or decoding fails
 */
fun <T : Message<T, *>> ProtoAdapter<T>.decodeOrNull(bytes: ByteArray?, logger: Logger? = null): T? {
    if (bytes == null) return null
    return decodeOrNull(bytes.toByteString(), logger)
}

/**
 * Check if an encoded message would fit within a size limit.
 *
 * More accurate than checking ByteArray.size() as it uses Wire's actual encoding size calculation, which accounts for
 * variable-length encoding.
 *
 * Useful for:
 * - Validating packet sizes before transmission
 * - Enforcing payload limits
 * - Better error messages with actual vs expected sizes
 *
 * Example:
 * ```
 * val data = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = bytes)
 * if (!Data.ADAPTER.isWithinSizeLimit(data, MAX_PAYLOAD)) {
 *     throw RemoteException("Payload too large")
 * }
 * ```
 *
 * @param message The message to check
 * @param maxBytes Maximum allowed bytes
 * @return true if encodedSize(message) <= maxBytes
 */
fun <T : Message<T, *>> ProtoAdapter<T>.isWithinSizeLimit(message: T, maxBytes: Int): Boolean =
    encodedSize(message) <= maxBytes

/**
 * Get the estimated encoded size of a message in bytes.
 *
 * This accounts for variable-length encoding and is more accurate than just using ByteArray.size(). Useful for size
 * validation and logging.
 *
 * @param message The message to measure
 * @return Size in bytes when encoded
 */
fun <T : Message<T, *>> ProtoAdapter<T>.sizeInBytes(message: T): Int = encodedSize(message)

/**
 * Convert a proto message to a pretty-printed string representation.
 *
 * This uses Wire's built-in toString() which provides a human-readable format with field names and values. Useful for
 * debugging and logging.
 *
 * Example output:
 * ```
 * Position{latitude_i=371234567, longitude_i=-1220987654, altitude=15}
 * ```
 *
 * @param message The message to format
 * @return String representation of the message
 */
fun <T : Message<T, *>> ProtoAdapter<T>.toReadableString(message: T): String = message.toString()

/**
 * Log a proto message with readable formatting.
 *
 * Useful for debugging packet contents during development.
 *
 * Example:
 * ```
 * Position.ADAPTER.logMessage(position, Logger, "Received position update")
 * ```
 *
 * @param message The message to log
 * @param logger The logger instance
 * @param prefix Optional prefix message
 */
fun <T : Message<T, *>> ProtoAdapter<T>.logMessage(message: T, logger: Logger, prefix: String = "") {
    val prefixStr = if (prefix.isNotEmpty()) "$prefix: " else ""
    logger.d { "$prefixStr${toReadableString(message)}" }
}

/**
 * Get a compact single-line string representation for JSON/API serialization.
 *
 * Converts the proto message to a single-line format by replacing newlines. Useful for compact logging and API
 * payloads.
 *
 * @param message The message to format
 * @return Single-line string representation
 */
fun <T : Message<T, *>> ProtoAdapter<T>.toOneLiner(message: T): String = message.toString().replace('\n', ' ')
