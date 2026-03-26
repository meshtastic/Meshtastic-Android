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
}
