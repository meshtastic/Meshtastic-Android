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
package org.meshtastic.feature.settings.radio.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FixedPinTest {

    @Test
    fun `the range boundaries are valid`() {
        assertTrue(isValidFixedPin("100000"))
        assertTrue(isValidFixedPin("999999"))
    }

    @Test
    fun `a leading zero or wrong length or non ASCII digit is invalid`() {
        listOf("", "0", "012345", "000000", "099999", "12345", "1234567", "12345a", "٣٤٥٦٧٨").forEach { pin ->
            assertFalse(isValidFixedPin(pin), "PIN '$pin' should be invalid")
        }
    }

    @Test
    fun `a stored PIN is shown padded to six digits`() {
        assertEquals("123456", fixedPinText(123456))
        assertEquals("000000", fixedPinText(0))
        assertEquals("012345", fixedPinText(12345))
    }

    @Test
    fun `an edited invalid PIN blocks Save in every mode`() {
        listOf("012345", "000000", "12345").forEach { pin ->
            assertTrue(isFixedPinBlocked(pin, "123456", fixedPinMode = true), pin)
            assertTrue(isFixedPinBlocked(pin, "123456", fixedPinMode = false), pin)
        }
    }

    @Test
    fun `a valid PIN never blocks Save`() {
        listOf("100000", "999999").forEach { pin ->
            assertFalse(isFixedPinBlocked(pin, "123456", fixedPinMode = true), pin)
            assertFalse(isFixedPinBlocked(pin, "123456", fixedPinMode = false), pin)
        }
    }

    @Test
    fun `an unedited invalid stored PIN blocks only in fixed PIN mode`() {
        assertTrue(isFixedPinBlocked("000000", "000000", fixedPinMode = true))
        assertFalse(isFixedPinBlocked("000000", "000000", fixedPinMode = false))
    }
}
