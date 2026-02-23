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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object SfppHasher {
    private const val HASH_SIZE = 16
    private const val INT_BYTES = 4

    fun computeMessageHash(encryptedPayload: ByteArray, to: Int, from: Int, id: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(encryptedPayload)
        digest.update(ByteBuffer.allocate(INT_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(to).array())
        digest.update(ByteBuffer.allocate(INT_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(from).array())
        digest.update(ByteBuffer.allocate(INT_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(id).array())
        return digest.digest().copyOf(HASH_SIZE)
    }
}
