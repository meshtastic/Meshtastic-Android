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
package org.meshtastic.feature.connections.domain.usecase

import org.meshtastic.core.network.SerialPortEntry
import org.meshtastic.feature.connections.model.JvmUsbDeviceData
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmUsbScannerTest {
    // The picker must store the address that survives re-enumeration, not the kernel name it shows.
    @Test
    fun `an entry is stored by its port address and shown by its system name`() {
        val link = "/dev/serial/by-id/usb-RAKwireless_WisCore_RAK4631_Board_7885014CB2C5B186-if00"

        val entry = usbEntries(listOf(SerialPortEntry("ttyACM3", link))).single()

        assertEquals("s$link", entry.fullAddress)
        assertEquals("ttyACM3", entry.name)
        assertEquals(JvmUsbDeviceData("ttyACM3"), entry.usbData)
    }
}
