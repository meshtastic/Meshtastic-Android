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

class FormatStringTest {

    @Test
    fun positionalStringSubstitution() {
        assertEquals("Hello World", formatString("%1\$s %2\$s", "Hello", "World"))
    }

    @Test
    fun positionalIntSubstitution() {
        assertEquals("Count: 42", formatString("Count: %1\$d", 42))
    }

    @Test
    fun positionalFloatSubstitution() {
        assertEquals("Value: 3.1", formatString("Value: %1\$.1f", 3.14159))
    }

    @Test
    fun positionalFloatTwoDecimals() {
        assertEquals("12.35%", formatString("%1\$.2f%%", 12.345))
    }

    @Test
    fun literalPercentEscape() {
        assertEquals("100%", formatString("100%%"))
    }

    @Test
    fun mixedPositionalArgs() {
        assertEquals("Battery: 85, Voltage: 3.7 V", formatString("Battery: %1\$d, Voltage: %2\$.1f V", 85, 3.7))
    }

    @Test
    fun deviceMetricsPercentTemplate() {
        assertEquals("ChUtil: 18.5%", formatString("%1\$s: %2\$.1f%%", "ChUtil", 18.456))
    }

    @Test
    fun deviceMetricsVoltageTemplate() {
        assertEquals("Voltage: 3.7 V", formatString("%1\$s: %2\$.1f V", "Voltage", 3.725))
    }

    @Test
    fun deviceMetricsNumericTemplate() {
        assertEquals("42.3", formatString("%1\$.1f", 42.345))
    }

    @Test
    fun localStatsUtilizationTemplate() {
        assertEquals(
            "ChUtil: 12.35% | AirTX: 5.68%",
            formatString("ChUtil: %1\$.2f%% | AirTX: %2\$.2f%%", 12.345, 5.678),
        )
    }

    @Test
    fun noArgsPlainString() {
        assertEquals("Hello", formatString("Hello"))
    }

    @Test
    fun sequentialStringSubstitution() {
        assertEquals("a b", formatString("%s %s", "a", "b"))
    }

    @Test
    fun sequentialIntSubstitution() {
        assertEquals("1 2", formatString("%d %d", 1, 2))
    }

    @Test
    fun sequentialFloatSubstitution() {
        assertEquals("1.2 3.5", formatString("%.1f %.1f", 1.23, 3.45))
    }

    // Hex format tests

    @Test
    fun lowercaseHex() {
        assertEquals("ff", formatString("%x", 255))
    }

    @Test
    fun uppercaseHex() {
        assertEquals("FF", formatString("%X", 255))
    }

    @Test
    fun zeroPaddedHex() {
        assertEquals("000000ff", formatString("%08x", 255))
    }

    @Test
    fun zeroPaddedHexNodeId() {
        assertEquals("!deadbeef", formatString("!%08x", 0xDEADBEEF.toInt()))
    }

    @Test
    fun hexZeroValue() {
        assertEquals("00000000", formatString("%08x", 0))
    }

    @Test
    fun positionalHex() {
        assertEquals("Node ff id 42", formatString("Node %1\$x id %2\$d", 255, 42))
    }

    // Edge case tests

    @Test
    fun trailingPercent() {
        assertEquals("hello", formatString("hello%"))
    }

    @Test
    fun outOfBoundsArgIndex() {
        assertEquals("null", formatString("%3\$s", "only_one"))
    }
}
