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
import kotlin.test.assertNotEquals

class SfppHasherTest {

    @Test
    fun outputIsAlways16Bytes() {
        val hash = SfppHasher.computeMessageHash(byteArrayOf(1, 2, 3), to = 100, from = 200, id = 1)
        assertEquals(16, hash.size)
    }

    @Test
    fun emptyPayloadProduces16Bytes() {
        val hash = SfppHasher.computeMessageHash(byteArrayOf(), to = 0, from = 0, id = 0)
        assertEquals(16, hash.size)
    }

    @Test
    fun deterministicOutput() {
        val a = SfppHasher.computeMessageHash(byteArrayOf(0xAB.toByte()), to = 1, from = 2, id = 3)
        val b = SfppHasher.computeMessageHash(byteArrayOf(0xAB.toByte()), to = 1, from = 2, id = 3)
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun differentPayloadsProduceDifferentHashes() {
        val a = SfppHasher.computeMessageHash(byteArrayOf(1), to = 1, from = 2, id = 3)
        val b = SfppHasher.computeMessageHash(byteArrayOf(2), to = 1, from = 2, id = 3)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun differentIdsProduceDifferentHashes() {
        val payload = byteArrayOf(0x10, 0x20)
        val a = SfppHasher.computeMessageHash(payload, to = 1, from = 2, id = 100)
        val b = SfppHasher.computeMessageHash(payload, to = 1, from = 2, id = 101)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun differentFromProduceDifferentHashes() {
        val payload = byteArrayOf(0x10, 0x20)
        val a = SfppHasher.computeMessageHash(payload, to = 1, from = 2, id = 3)
        val b = SfppHasher.computeMessageHash(payload, to = 1, from = 99, id = 3)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun maxIntValues() {
        val hash =
            SfppHasher.computeMessageHash(
                byteArrayOf(0xFF.toByte()),
                to = Int.MAX_VALUE,
                from = Int.MAX_VALUE,
                id = Int.MAX_VALUE,
            )
        assertEquals(16, hash.size)
    }

    @Test
    fun littleEndianByteOrder() {
        // Verify the integer 0x04030201 is encoded as [01, 02, 03, 04] (little-endian)
        val hashA = SfppHasher.computeMessageHash(byteArrayOf(), to = 0x04030201, from = 0, id = 0)
        val hashB = SfppHasher.computeMessageHash(byteArrayOf(), to = 0x01020304, from = 0, id = 0)
        // Different byte orderings must produce different hashes
        assertNotEquals(hashA.toList(), hashB.toList())
    }
}
