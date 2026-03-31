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
package org.meshtastic.feature.firmware.ota

import kotlin.test.Test
import kotlin.test.assertEquals

class BleScanSupportTest {

    @Test
    fun calculateMacPlusOneNormal() {
        val original = "12:34:56:78:9A:BC"
        // 0xBC + 1 = 0xBD
        assertEquals("12:34:56:78:9A:BD", calculateMacPlusOne(original))
    }

    @Test
    fun calculateMacPlusOneWrapAround() {
        val original = "12:34:56:78:9A:FF"
        // 0xFF + 1 = 0x100 -> truncated to modulo 0xFF is 0x00
        assertEquals("12:34:56:78:9A:00", calculateMacPlusOne(original))
    }

    @Test
    fun calculateMacPlusOneInvalidLength() {
        val original = "12:34:56:78"
        // Return original if invalid
        assertEquals(original, calculateMacPlusOne(original))
    }

    @Test
    fun calculateMacPlusOneInvalidCharacter() {
        val original = "12:34:56:78:9A:ZZ"
        // Return original if cannot parse HEX
        assertEquals(original, calculateMacPlusOne(original))
    }
}
