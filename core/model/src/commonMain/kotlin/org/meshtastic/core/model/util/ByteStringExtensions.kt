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
package org.meshtastic.core.model.util

import okio.ByteString
import okio.ByteString.Companion.decodeBase64

fun ByteString.encodeToString(): String = base64()

/**
 * Decodes a Base64 string into a [ByteString].
 *
 * @throws IllegalArgumentException if the string is not valid Base64.
 */
fun String.base64ToByteString(): ByteString =
    decodeBase64() ?: throw IllegalArgumentException("Invalid Base64 string: $this")
