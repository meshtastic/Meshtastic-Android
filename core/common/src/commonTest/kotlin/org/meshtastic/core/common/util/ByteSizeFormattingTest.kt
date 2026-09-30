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

import kotlin.test.Test
import kotlin.test.assertEquals

/** Thresholds and digits under the en-US locale the test JVMs are pinned to, on the fixed-symbol path. */
class ByteSizeFormattingTest {

    @Test
    fun `up to 900 bytes stays in bytes with no fraction`() {
        assertEquals("0 B", formatByteSize(0L))
        assertEquals("1 B", formatByteSize(1L))
        assertEquals("900 B", formatByteSize(900L))
    }

    @Test
    fun `past 900 the next unit takes over with two fraction digits`() {
        assertEquals("0.90 kB", formatByteSize(901L))
        assertEquals("0.90 MB", formatByteSize(900_001L))
    }

    @Test
    fun `a kilobyte is 1000 bytes and not 1024`() {
        assertEquals("1.00 kB", formatByteSize(1_000L))
        assertEquals("1.02 kB", formatByteSize(1_024L))
        assertEquals("1.50 kB", formatByteSize(1_500L))
        assertEquals("1.50 MB", formatByteSize(1_500_000L))
    }

    @Test
    fun `a 16 GiB count reads in decimal gigabytes`() {
        assertEquals("17.18 GB", formatByteSize(17_179_869_184L))
    }

    @Test
    fun `from 100 the value has no fraction`() {
        assertEquals("99.50 kB", formatByteSize(99_500L))
        assertEquals("100 kB", formatByteSize(100_000L))
        assertEquals("900 kB", formatByteSize(900_000L))
        assertEquals("250 GB", formatByteSize(250_000_000_000L))
    }

    @Test
    fun `terabytes are the largest unit`() {
        assertEquals("1.00 TB", formatByteSize(1_000_000_000_000L))
        assertEquals("2,000 TB", formatByteSize(2_000_000_000_000_000L))
    }

    @Test
    fun `a negative count keeps its sign`() {
        assertEquals("-1.50 kB", formatByteSize(-1_500L))
        assertEquals("-9,223,372 TB", formatByteSize(Long.MIN_VALUE))
    }

    @Test
    fun `megabytes stay in megabytes at any size`() {
        assertEquals("0.5 MB", formatMegabytes(0.5, 1))
        assertEquals("1.3 MB", formatMegabytes(1.25, 1))
        assertEquals("17,180 MB", formatMegabytes(17_179.869184, 0))
    }
}
