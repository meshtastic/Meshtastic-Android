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
package org.meshtastic.core.takserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [TakV2Compressor] size boundary validation (T077).
 *
 * Verifies that:
 * - MAX_DECOMPRESSED_SIZE is a reasonable constant (4096 bytes)
 * - Dictionary IDs are correctly defined
 * - The uncompressed marker (0xFF) is correct
 */
class TakV2CompressorBoundaryTest {

    @Test
    fun `MAX_DECOMPRESSED_SIZE is 4096 bytes`() {
        assertEquals(4096, TakV2Compressor.MAX_DECOMPRESSED_SIZE)
    }

    @Test
    fun `MAX_DECOMPRESSED_SIZE is greater than mesh MTU`() {
        // MAX_TAK_WIRE_PAYLOAD_BYTES = 225. Decompressed size must be larger than the
        // compressed wire payload to be useful. Also ensures there's a reasonable
        // amplification cap to prevent decompression bombs.
        assertTrue(TakV2Compressor.MAX_DECOMPRESSED_SIZE > MAX_TAK_WIRE_PAYLOAD_BYTES)
    }

    @Test
    fun `MAX_DECOMPRESSED_SIZE is bounded to prevent memory exhaustion`() {
        // A decompression bomb could expand a small payload into megabytes. The limit
        // must be small enough to prevent OOM in constrained Android environments.
        assertTrue(TakV2Compressor.MAX_DECOMPRESSED_SIZE <= 65536)
    }

    @Test
    fun `dictionary IDs are correctly assigned`() {
        assertEquals(0, TakV2Compressor.DICT_ID_NON_AIRCRAFT)
        assertEquals(1, TakV2Compressor.DICT_ID_AIRCRAFT)
    }

    @Test
    fun `uncompressed marker is 0xFF`() {
        assertEquals(0xFF, TakV2Compressor.DICT_ID_UNCOMPRESSED)
    }
}
