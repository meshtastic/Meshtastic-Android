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
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals

class Crc32Test {

    @Test
    fun matchesStandardCheckValue() {
        assertEquals(0xCBF43926u, "123456789".encodeUtf8().crc32())
    }

    @Test
    fun matchesZlibForAsciiText() {
        assertEquals(0x414FA339u, "The quick brown fox jumps over the lazy dog".encodeUtf8().crc32())
    }

    @Test
    fun matchesZlibForKeySizedBuffer() {
        val key = ByteArray(32) { it.toByte() }.toByteString()
        assertEquals(0x91267E8Au, key.crc32())
    }

    @Test
    fun emptyInputYieldsZero() {
        assertEquals(0u, ByteString.EMPTY.crc32())
    }
}
