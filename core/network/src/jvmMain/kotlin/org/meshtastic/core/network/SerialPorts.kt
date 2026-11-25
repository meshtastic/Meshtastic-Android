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

import com.fazecast.jSerialComm.SerialPort
import java.io.File

private val SERIAL_BY_ID_DIR = File("/dev/serial/by-id")

/**
 * The serial ports present now. On Linux a USB port's [SerialPortEntry.address] is its `/dev/serial/by-id` link: the
 * kernel gives a re-enumerated device the next free `ttyACM` number while the old one is still open, and udev points
 * the link at the new one. Other ports, and other hosts, whose names already follow the device, use the system name.
 */
fun availableSerialPorts(): List<SerialPortEntry> {
    val links = serialByIdLinks(SERIAL_BY_ID_DIR)
    return SerialPort.getCommPorts().map {
        SerialPortEntry(it.systemPortName, links[it.systemPortName] ?: it.systemPortName)
    }
}

/** Each device node name in [dir]'s link targets, mapped to the first of its links by name. */
internal fun serialByIdLinks(dir: File): Map<String, String> = dir.listFiles()
    .orEmpty()
    .mapNotNull { link -> runCatching { link.canonicalFile.name to link.path }.getOrNull() }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, links) -> links.min() }

/** A serial port the desktop picker offers: [name] to show, [address] to store and reopen. */
data class SerialPortEntry(val name: String, val address: String)
