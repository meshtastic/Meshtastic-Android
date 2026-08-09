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
package org.meshtastic.core.common.util

import okio.ByteString

/**
 * CRC-32 (IEEE 802.3 / zlib): reflected polynomial 0xEDB88320, initial value and final XOR 0xFFFFFFFF.
 *
 * Matches the firmware's `crc32Buffer` (ErriezCRC32), which firmware 2.8+ uses to derive the node number from the
 * device public key: `my_node_num = crc32(config.security.public_key)` (NodeDB::createNewIdentity). Lets the app
 * recognize a pubkey-derived ("canonical") node number when a node reappears under a new num after a firmware upgrade.
 */
object Crc32 {
    private const val POLYNOMIAL: UInt = 0xEDB88320u
    private const val INITIAL: UInt = 0xFFFFFFFFu
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK: UInt = 0xFFu
    private const val TABLE_SIZE = 256

    private val table =
        UIntArray(TABLE_SIZE) { index ->
            var crc = index.toUInt()
            repeat(BITS_PER_BYTE) { crc = if (crc and 1u != 0u) (crc shr 1) xor POLYNOMIAL else crc shr 1 }
            crc
        }

    fun compute(bytes: ByteString): UInt {
        var crc = INITIAL
        for (i in 0 until bytes.size) {
            val byte = bytes[i].toUInt() and BYTE_MASK
            crc = (crc shr BITS_PER_BYTE) xor table[((crc xor byte) and BYTE_MASK).toInt()]
        }
        return crc.inv()
    }
}

fun ByteString.crc32(): UInt = Crc32.compute(this)
