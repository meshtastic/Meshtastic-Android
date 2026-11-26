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
        link("usb-Silicon_Labs_CP2102_extra-if00-port0", "ttyUSB0")
        val first = link("usb-Silicon_Labs_CP2102-if00-port0", "ttyUSB0")

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

    // Only an address that belongs to one radio may carry that radio's remembered node into the picker.
    @Test
    fun `a link carrying the port's USB serial number names one radio`() {
        val link = "/dev/serial/by-id/usb-RAKwireless_WisCore_RAK4631_Board_7885014CB2C5B186-if00"

        val entries =
            serialPortEntries(
                listOf("ttyACM3", "ttyS0"),
                mapOf("ttyACM3" to link),
                mapOf("ttyACM3" to "7885014CB2C5B186", "ttyS0" to "Unknown"),
            )

        assertEquals(listOf(true, false), entries.map { it.namesRadio })
    }

    // A CH340 reports no serial, so udev names the link by model; a CP2102's default `0001` is shared by many boards.
    @Test
    fun `a bridge link without a serial or with a factory default serial names no radio`() {
        val entries =
            serialPortEntries(
                listOf("ttyUSB0", "ttyUSB1"),
                mapOf(
                    "ttyUSB0" to "/dev/serial/by-id/usb-1a86_USB_Serial-if00-port0",
                    "ttyUSB1" to
                        "/dev/serial/by-id/usb-Silicon_Labs_CP2102_USB_to_UART_Bridge_Controller_0001-if00-port0",
                ),
                mapOf("ttyUSB0" to null, "ttyUSB1" to "0001"),
            )

        assertEquals(listOf(false, false), entries.map { it.namesRadio })
    }

    // A by-id address is a symlink: the permission hint must name the device node's group, not the link's.
    @Test
    fun `the group hint for a link names its target's group`() {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return // GNU stat only
        val target = "/dev/null"
        val targetGroup =
            ProcessBuilder("stat", "-c", "%G", target).start().inputStream.bufferedReader().readText().trim()
        val link = byId.resolve("usb-test-if00").createSymbolicLinkPointingTo(File(target).toPath()).toString()

        assertEquals(targetGroup, detectSerialGroup(link))
    }
}
