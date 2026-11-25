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
package org.meshtastic.core.network

import java.io.File
import java.nio.file.Files
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SerialPortsTest {
    private val dev = Files.createTempDirectory("serial-ports")
    private val byId = dev.resolve("by-id").createDirectory()

    @AfterTest
    fun cleanUp() {
        dev.toFile().deleteRecursively()
    }

    private fun link(name: String, node: String) =
        byId.resolve(name).createSymbolicLinkPointingTo(dev.resolve(node)).toString()

    // udev points a by-id link at the device's current node, so the picker stores the link, not `ttyACM3`.
    @Test
    fun `a device node maps to the path of its by-id link`() {
        dev.resolve("ttyACM3").createFile()
        val path = link("usb-RAKwireless_WisCore_RAK4631_Board_7885014CB2C5B186-if00", "ttyACM3")

        assertEquals(mapOf("ttyACM3" to path), serialByIdLinks(byId.toFile()))
    }

    // A node with several links must give the same address every scan, or the selection flips between them.
    @Test
    fun `a node with two links always maps to the first by name`() {
        dev.resolve("ttyUSB0").createFile()
        val first = link("usb-Silicon_Labs_CP2102-if00-port0", "ttyUSB0")
        link("usb-Silicon_Labs_CP2102_extra-if00-port0", "ttyUSB0")

        assertEquals(mapOf("ttyUSB0" to first), serialByIdLinks(byId.toFile()))
    }

    // macOS and Windows have no /dev/serial/by-id; their ports keep the system name.
    @Test
    fun `a missing by-id directory gives no links`() {
        assertEquals(emptyMap(), serialByIdLinks(File(dev.toFile(), "absent")))
    }

    // The stored address is the link where one exists; a built-in UART with no link keeps its own name.
    @Test
    fun `a port with a link is addressed by the link and one without by its name`() {
        val link = "/dev/serial/by-id/usb-RAKwireless_WisCore_RAK4631_Board_7885014CB2C5B186-if00"

        assertEquals(
            listOf(SerialPortEntry("ttyACM3", link), SerialPortEntry("ttyS0", "ttyS0")),
            serialPortEntries(listOf("ttyACM3", "ttyS0"), mapOf("ttyACM3" to link)),
        )
    }
}
