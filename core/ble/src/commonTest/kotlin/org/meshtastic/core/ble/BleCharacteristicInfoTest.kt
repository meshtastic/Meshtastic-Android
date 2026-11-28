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
package org.meshtastic.core.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/**
 * Bits are the GATT Characteristic Properties of Bluetooth Core Vol 3 Part G 3.3.1.1: Broadcast 0x01, Read 0x02, Write
 * Without Response 0x04, Write 0x08, Notify 0x10, Indicate 0x20.
 */
class BleCharacteristicInfoTest {
    private val uuid = Uuid.parse("bef8d6c9-9c21-4c9e-b632-bd58c1009f9f")

    private fun flags(properties: Int) =
        bleCharacteristicInfo(uuid, properties).let { it.notifies to it.writesWithoutResponse }

    @Test
    fun `notify and write without response each map from their own bit`() {
        assertEquals(true to false, flags(0x10))
        assertEquals(false to true, flags(0x04))
        assertEquals(true to true, flags(0x02 or 0x04 or 0x10))
    }

    @Test
    fun `indicate and write with response are neither notify nor write without response`() {
        assertEquals(false to false, flags(0x01 or 0x02 or 0x08 or 0x20))
    }
}
