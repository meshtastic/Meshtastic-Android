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
package org.meshtastic.core.network.repository

import java.nio.file.Files
import kotlin.io.path.createFile
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteExisting
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmSerialDevicePresenceTest {
    private val dev = Files.createTempDirectory("serial-presence")

    @AfterTest
    fun cleanUp() {
        dev.toFile().deleteRecursively()
    }

    // The reconnect waits on portExists: a by-id address must count as present while udev points it at a device,
    // and absent while the device is gone, or the session waits forever or opens a missing port.
    @Test
    fun `a by-id address exists exactly while its link resolves`() {
        val node = dev.resolve("ttyACM3").createFile()
        val link =
            dev.resolve("usb-RAKwireless_WisCore_RAK4631_Board_7885014CB2C5B186-if00")
                .createSymbolicLinkPointingTo(node)
        val presence = JvmSerialDevicePresence()

        assertTrue(presence.portExists(link.toString()))
        node.deleteExisting()
        assertFalse(presence.portExists(link.toString()))
    }

    // macOS makes /dev/cu.X and /dev/tty.X for one USB device and the SDK listing drops tty names; a saved tty
    // address must still count as present while the node exists, or the serial link waits forever.
    @Test
    fun `a saved macOS tty port exists while its node does though the listing omits it`() {
        val cu = dev.resolve("cu.usbmodem1101").createFile()
        val tty = dev.resolve("tty.usbmodem1101").createFile()
        val listed = listOf("cu.usbmodem1101")

        assertTrue(serialPortExists("tty.usbmodem1101", isWindows = false, devDir = dev.toFile()) { listed })
        cu.deleteExisting()
        tty.deleteExisting()
        assertFalse(serialPortExists("tty.usbmodem1101", isWindows = false, devDir = dev.toFile()) { emptyList() })
    }

    // A Windows COM port is not a file, so its presence comes from the port listing.
    @Test
    fun `a Windows COM port exists while the listing has it`() {
        val listed = listOf("COM3")

        assertTrue(serialPortExists("COM3", isWindows = true, devDir = dev.toFile()) { listed })
        assertFalse(serialPortExists("COM4", isWindows = true, devDir = dev.toFile()) { listed })
    }
}
