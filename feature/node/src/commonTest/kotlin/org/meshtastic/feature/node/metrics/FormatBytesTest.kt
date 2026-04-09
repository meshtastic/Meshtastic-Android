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
package org.meshtastic.feature.node.metrics

import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests for [formatBytes] — the pure function that formats byte counts into human-readable strings. */
@Suppress("MagicNumber")
class FormatBytesTest {

    @Test
    fun zero_bytes() {
        assertEquals("0 B", formatBytes(0L))
    }

    @Test
    fun small_byte_values() {
        assertEquals("1 B", formatBytes(1L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1023 B", formatBytes(1023L))
    }

    @Test
    fun kilobyte_boundary() {
        assertEquals("1 KB", formatBytes(1024L))
    }

    @Test
    fun kilobyte_with_decimals() {
        // 1536 bytes = 1.5 KB
        assertEquals("1.5 KB", formatBytes(1536L))
    }

    @Test
    fun megabyte_boundary() {
        assertEquals("1 MB", formatBytes(1024L * 1024))
    }

    @Test
    fun megabyte_with_decimals() {
        // 1.5 MB = 1572864 bytes
        assertEquals("1.5 MB", formatBytes(1_572_864L))
    }

    @Test
    fun gigabyte_boundary() {
        assertEquals("1 GB", formatBytes(1024L * 1024 * 1024))
    }

    @Test
    fun gigabyte_with_decimals() {
        // 2.5 GB
        assertEquals("2.5 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun negative_bytes_returns_na() {
        assertEquals("N/A", formatBytes(-1L))
        assertEquals("N/A", formatBytes(-1024L))
    }

    @Test
    fun large_values() {
        // 100 GB
        assertEquals("100 GB", formatBytes(100L * 1024 * 1024 * 1024))
    }

    @Test
    fun custom_decimal_places_zero() {
        // 1536 bytes = 1.5 KB, with 0 decimal places → 2 KB (rounded)
        assertEquals("2 KB", formatBytes(1536L, decimalPlaces = 0))
    }

    @Test
    fun custom_decimal_places_one() {
        // 1536 bytes = 1.5 KB, with 1 decimal place → 1.5 KB
        assertEquals("1.5 KB", formatBytes(1536L, decimalPlaces = 1))
    }
}
