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
package org.meshtastic.core.network.serial

import kotlin.test.Test
import kotlin.test.assertEquals

class SerialPortIdPrefixTest {
    @Test
    fun `usb ids print as four lowercase hex digits each`() {
        assertEquals("239a:8029", serialPortIdPrefix(0x239A, 0x8029))
        assertEquals("10c4:ea60", serialPortIdPrefix(0x10C4, 0xEA60))
    }

    @Test
    fun `a port without usb ids gets zeros`() {
        assertEquals("0000:0000", serialPortIdPrefix(0, 0))
    }
}
