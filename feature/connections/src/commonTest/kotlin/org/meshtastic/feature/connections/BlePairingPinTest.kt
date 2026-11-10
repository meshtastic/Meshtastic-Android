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
package org.meshtastic.feature.connections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlePairingPinTest {

    @Test
    fun `a six digit PIN on a BLE address is kept`() {
        assertEquals("123456", blePairingPinFor("xAA:BB:CC:DD:EE:FF", "123456"))
    }

    @Test
    fun `a PIN with leading zeros is kept as typed`() {
        // fixed_pin is shown zero-padded to six digits, so the link must not lose the padding.
        assertEquals("012345", blePairingPinFor("xAA:BB:CC:DD:EE:FF", "012345"))
    }

    @Test
    fun `a missing PIN is null`() {
        assertNull(blePairingPinFor("xAA:BB:CC:DD:EE:FF", null))
    }

    @Test
    fun `a PIN that is not six digits is dropped`() {
        listOf("", "12345", "1234567", "12345a", "12 456", "١٢٣٤٥٦").forEach { pin ->
            assertNull(blePairingPinFor("xAA:BB:CC:DD:EE:FF", pin), "PIN '$pin' should be rejected")
        }
    }

    @Test
    fun `a PIN on a non-BLE address is dropped`() {
        assertNull(blePairingPinFor("t192.168.1.1:4403", "123456"))
        assertNull(blePairingPinFor(NO_DEVICE_SELECTED, "123456"))
        assertNull(blePairingPinFor(null, "123456"))
    }
}
