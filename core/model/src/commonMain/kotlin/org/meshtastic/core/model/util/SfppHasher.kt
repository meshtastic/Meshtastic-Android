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

import okio.ByteString.Companion.toByteString

/** Computes SFPP (Store-Forward-Plus-Plus) message hashes for deduplication. */
object SfppHasher {
    private const val HASH_SIZE = 16
    private const val INT_BYTES = 4
    private const val INT_COUNT = 3
    private const val SHIFT_8 = 8
    private const val SHIFT_16 = 16
    private const val SHIFT_24 = 24

    fun computeMessageHash(encryptedPayload: ByteArray, to: Int, from: Int, id: Int): ByteArray {
        val input = ByteArray(encryptedPayload.size + INT_BYTES * INT_COUNT)
        encryptedPayload.copyInto(input)
        var offset = encryptedPayload.size
        for (value in intArrayOf(to, from, id)) {
            input[offset++] = value.toByte()
            input[offset++] = (value shr SHIFT_8).toByte()
            input[offset++] = (value shr SHIFT_16).toByte()
            input[offset++] = (value shr SHIFT_24).toByte()
        }
        return input.toByteString().sha256().toByteArray().copyOf(HASH_SIZE)
    }
}
