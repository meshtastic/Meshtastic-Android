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

import kotlin.test.Test
import kotlin.test.assertEquals

class CommonUtilsTest {

    @Test
    fun testByteArrayOfInts() {
        val bytes = byteArrayOfInts(0x01, 0xFF, 0x80)
        assertEquals(3, bytes.size)
        assertEquals(1, bytes[0])
        assertEquals(-1, bytes[1]) // 0xFF as signed byte
        assertEquals(-128, bytes[2].toInt()) // 0x80 as signed byte
    }

    @Test
    fun testXorHash() {
        val data = byteArrayOfInts(0x01, 0x02, 0x03)
        assertEquals(0 xor 1 xor 2 xor 3, xorHash(data))

        val data2 = byteArrayOfInts(0xFF, 0xFF)
        assertEquals(0xFF xor 0xFF, xorHash(data2))
        assertEquals(0, xorHash(data2))
    }
}
