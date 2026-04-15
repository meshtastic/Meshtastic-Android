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

class AddressUtilsTest {

    @Test
    fun nullReturnsDefault() {
        assertEquals("DEFAULT", normalizeAddress(null))
    }

    @Test
    fun blankReturnsDefault() {
        assertEquals("DEFAULT", normalizeAddress(""))
        assertEquals("DEFAULT", normalizeAddress("   "))
    }

    @Test
    fun sentinelNReturnsDefault() {
        assertEquals("DEFAULT", normalizeAddress("N"))
        assertEquals("DEFAULT", normalizeAddress("n"))
    }

    @Test
    fun sentinelNullReturnsDefault() {
        assertEquals("DEFAULT", normalizeAddress("NULL"))
        assertEquals("DEFAULT", normalizeAddress("null"))
        assertEquals("DEFAULT", normalizeAddress("Null"))
    }

    @Test
    fun stripsColons() {
        assertEquals("AABBCCDD", normalizeAddress("AA:BB:CC:DD"))
    }

    @Test
    fun uppercases() {
        assertEquals("AABBCCDD", normalizeAddress("aa:bb:cc:dd"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("AABBCC", normalizeAddress("  AA:BB:CC  "))
    }

    @Test
    fun alreadyNormalizedPassesThrough() {
        assertEquals("AABBCCDD", normalizeAddress("AABBCCDD"))
    }

    @Test
    fun mixedCaseWithColons() {
        assertEquals("AABBCC", normalizeAddress("aA:Bb:cC"))
    }
}
