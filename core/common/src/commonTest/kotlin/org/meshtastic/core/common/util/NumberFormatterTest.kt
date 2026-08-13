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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NumberFormatterTest {

    @Test
    fun testFormat() {
        assertEquals("1.23", NumberFormatter.format(1.23456, 2))
        assertEquals("1.235", NumberFormatter.format(1.23456, 3))
        assertEquals("1.00", NumberFormatter.format(1.0, 2))
        assertEquals("0.00", NumberFormatter.format(0.0, 2))
        assertEquals("-1.23", NumberFormatter.format(-1.23456, 2))
    }

    @Test
    fun testFormatZeroDecimalPlaces() {
        assertEquals("1", NumberFormatter.format(1.23, 0))
        assertEquals("-1", NumberFormatter.format(-1.23, 0))
    }

    @Test
    fun testFormatNaN() {
        assertEquals("—", NumberFormatter.format(Double.NaN, 2))
        assertEquals("—", NumberFormatter.format(Float.NaN, 1))
    }

    @Test
    fun testFormatInfinity() {
        assertEquals("—", NumberFormatter.format(Double.POSITIVE_INFINITY, 2))
        assertEquals("—", NumberFormatter.format(Double.NEGATIVE_INFINITY, 2))
        assertEquals("—", NumberFormatter.format(Float.POSITIVE_INFINITY, 1))
    }

    @Test
    fun testParseDecimalDotLocale() {
        assertEquals(48.21, NumberFormatter.parseDecimalOrNull("48.21"))
        assertEquals(-150.0, NumberFormatter.parseDecimalOrNull("-150"))
        assertEquals(-1.5, NumberFormatter.parseDecimalOrNull("-1.5"))
        assertEquals(906.875, NumberFormatter.parseDecimalOrNull("906.875"))
    }

    @Test
    fun testParseDecimalCommaLocale() {
        assertEquals(48.21, NumberFormatter.parseDecimalOrNull("48,21"))
        assertEquals(-73.935, NumberFormatter.parseDecimalOrNull("-73,935"))
        assertEquals(0.5, NumberFormatter.parseDecimalOrNull("0,5"))
    }

    @Test
    fun testParseDecimalOtherSeparators() {
        assertEquals(1.5, NumberFormatter.parseDecimalOrNull("1٫5"))
        assertEquals(1.5, NumberFormatter.parseDecimalOrNull("1·5"))
        assertEquals(1.5, NumberFormatter.parseDecimalOrNull("1、5"))
    }

    @Test
    fun testParseDecimalStripsGrouping() {
        // More than one separator: the last is the decimal mark, the rest are grouping.
        assertEquals(1234.56, NumberFormatter.parseDecimalOrNull("1.234,56"))
        assertEquals(1234.56, NumberFormatter.parseDecimalOrNull("1,234.56"))
        assertEquals(1234567.8, NumberFormatter.parseDecimalOrNull("1.234.567,8"))
        assertEquals(-1234.5, NumberFormatter.parseDecimalOrNull("-1.234,5"))
    }

    @Test
    fun testParseDecimalStripsWhitespaceGrouping() {
        assertEquals(1234.5, NumberFormatter.parseDecimalOrNull("1 234,5"))
        assertEquals(48.21, NumberFormatter.parseDecimalOrNull("  48.21  "))
    }

    @Test
    fun testParseDecimalRejectsGarbage() {
        assertNull(NumberFormatter.parseDecimalOrNull(""))
        assertNull(NumberFormatter.parseDecimalOrNull("   "))
        assertNull(NumberFormatter.parseDecimalOrNull("abc"))
        assertNull(NumberFormatter.parseDecimalOrNull("."))
        assertNull(NumberFormatter.parseDecimalOrNull("1.2.3.4a"))
    }

    @Test
    fun testIsPartialDecimalAcceptsTransientInput() {
        // What a controlled field must keep on the way to "-1.5" or ".5".
        assertTrue(NumberFormatter.isPartialDecimal(""))
        assertTrue(NumberFormatter.isPartialDecimal("-"))
        assertTrue(NumberFormatter.isPartialDecimal("."))
        assertTrue(NumberFormatter.isPartialDecimal(","))
        assertTrue(NumberFormatter.isPartialDecimal("-."))
        assertTrue(NumberFormatter.isPartialDecimal("-1."))
        assertTrue(NumberFormatter.isPartialDecimal("1 "))
    }

    @Test
    fun testIsPartialDecimalRejectsNonNumeric() {
        assertFalse(NumberFormatter.isPartialDecimal("abc"))
        assertFalse(NumberFormatter.isPartialDecimal("1a"))
        assertFalse(NumberFormatter.isPartialDecimal("1-2"))
        assertFalse(NumberFormatter.isPartialDecimal("--1"))
    }

    @Test
    fun testParseDecimalRoundTripsFormatOutput() {
        // format() always emits '.', so its output must survive the parser unchanged.
        assertEquals(-42.75, NumberFormatter.parseDecimalOrNull(NumberFormatter.format(-42.75, 2)))
    }
}
