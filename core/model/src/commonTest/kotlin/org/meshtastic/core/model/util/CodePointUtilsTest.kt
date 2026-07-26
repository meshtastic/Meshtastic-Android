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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodePointUtilsTest {

    @Test
    fun `isValidCodePoint accepts scalar values`() {
        assertTrue('A'.code.isValidCodePoint())
        assertTrue(PUSHPIN_CODE_POINT.isValidCodePoint())
        assertTrue(0.isValidCodePoint())
        assertTrue(0x10FFFF.isValidCodePoint())
    }

    @Test
    fun `isValidCodePoint rejects out of range and surrogates`() {
        assertFalse((-1).isValidCodePoint())
        assertFalse(0x110000.isValidCodePoint())
        assertFalse(Int.MAX_VALUE.isValidCodePoint())
        assertFalse(Int.MIN_VALUE.isValidCodePoint())
        assertFalse(0xD800.isValidCodePoint())
        assertFalse(0xDFFF.isValidCodePoint())
    }

    @Test
    fun `toCodePointString encodes bmp and supplementary planes`() {
        assertEquals("A", 'A'.code.toCodePointString())
        assertEquals("📍", PUSHPIN_CODE_POINT.toCodePointString())
        assertEquals("􏿿", 0x10FFFF.toCodePointString())
    }

    @Test
    fun `toCodePointString falls back instead of throwing on out-of-range input`() {
        // Values that reach Waypoint.icon from the wire; each of these throws from Character.toChars.
        assertEquals("📍", (-1).toCodePointString())
        assertEquals("📍", 0x110000.toCodePointString())
        assertEquals("📍", 0xFFFFFFFF.toInt().toCodePointString())
        assertEquals("📍", 0xD800.toCodePointString())
    }

    @Test
    fun `zero renders as a control character without the waypoint helper`() {
        // The premise for waypointIconOrDefault existing: zero is a valid scalar value, so toCodePointString has no
        // reason to substitute anything. If this ever starts returning the pushpin, the helper is redundant.
        assertEquals("\u0000", 0.toCodePointString())
    }

    @Test
    fun `waypointIconOrDefault maps the unset icon to the pushpin`() {
        assertEquals(PUSHPIN_CODE_POINT, 0.waypointIconOrDefault())
        assertEquals("📍", 0.waypointIconOrDefault().toCodePointString())
    }

    @Test
    fun `waypointIconOrDefault leaves a chosen icon alone`() {
        // Includes an out-of-range value: the helper resolves "unset", it is not a validity clamp — that is
        // toCodePointString's job, and conflating the two would silently discard the distinction.
        assertEquals('A'.code, 'A'.code.waypointIconOrDefault())
        assertEquals(0x1F600, 0x1F600.waypointIconOrDefault())
        assertEquals(-1, (-1).waypointIconOrDefault())
    }

    @Test
    fun `toCodePointString honours an explicit fallback`() {
        assertEquals("?", (-1).toCodePointString(fallback = '?'.code))
        // A bogus fallback must not propagate the throw either.
        assertEquals("📍", (-1).toCodePointString(fallback = -2))
    }
}
