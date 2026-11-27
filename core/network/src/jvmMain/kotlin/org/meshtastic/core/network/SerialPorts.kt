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
    val ports = SerialPort.getCommPorts()
    return serialPortEntries(
        ports.map { it.systemPortName },
        serialByIdLinks(SERIAL_BY_ID_DIR),
        ports.associate { it.systemPortName to it.serialNumber },
    )
}

/**
 * Each of [names] with its by-id link from [links] as the address, or its own name when it has none. The address names
 * one radio only when its link carries the port's USB serial number from [serials].
 */
internal fun serialPortEntries(
    names: List<String>,
    links: Map<String, String>,
    serials: Map<String, String?> = emptyMap(),
): List<SerialPortEntry> = names.map { name ->
    val link = links[name]
    SerialPortEntry(name, link ?: name, namesRadio = link != null && linkCarriesSerial(link, serials[name]))
}

/**
 * udev writes the USB serial number into a by-id name, `usb-<vendor>_<model>_<serial>-if00`, only when the device
 * reports one; a short serial is a bridge chip's factory default (a CP2102 reports `0001`) that many boards share.
 */
internal fun linkCarriesSerial(link: String, serial: String?): Boolean =
    serial != null && serial.length >= MIN_UNIQUE_SERIAL_LENGTH && File(link).name.contains("_$serial-if")

private const val MIN_UNIQUE_SERIAL_LENGTH = 8

/** Each device node name in [dir]'s link targets, mapped to the first of its links by name. */
internal fun serialByIdLinks(dir: File): Map<String, String> = dir.listFiles()
    .orEmpty()
    .mapNotNull { link -> runCatching { link.canonicalFile.name to link.path }.getOrNull() }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, links) -> links.min() }

/**
 * A serial port the desktop picker offers: [name] to show, [address] to store and reopen. [namesRadio] is true when the
 * address belongs to one physical radio rather than to a port or a board model.
 */
data class SerialPortEntry(val name: String, val address: String, val namesRadio: Boolean = false)
