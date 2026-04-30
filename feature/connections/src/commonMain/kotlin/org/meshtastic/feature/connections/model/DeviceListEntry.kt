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
package org.meshtastic.feature.connections.model

import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.anonymize

/** Interface for platform-specific USB data to avoid Android dependencies in common code. */
interface UsbDeviceData

/** A sealed class representing the different types of devices that can be displayed in the connections list. */
sealed class DeviceListEntry(
    open val name: String,
    open val fullAddress: String,
    open val bonded: Boolean,
    open val node: Node? = null,
) {
    val address: String
        get() = fullAddress.substring(1)

    abstract fun copy(node: Node?): DeviceListEntry

    override fun toString(): String =
        "DeviceListEntry(name=${name.anonymize}, addr=${address.anonymize}, bonded=$bonded, hasNode=${node != null})"

    data class Ble(
        val device: BleDevice,
        override val bonded: Boolean = device.isBonded,
        override val node: Node? = null,
    ) : DeviceListEntry(
        name = device.name ?: "unnamed-${device.address}",
        fullAddress = "x${device.address}",
        bonded = bonded,
        node = node,
    ) {
        override fun copy(node: Node?): Ble = copy(device = device, bonded = bonded, node = node)
    }

    data class Usb(
        val usbData: UsbDeviceData,
        override val name: String,
        override val fullAddress: String,
        override val bonded: Boolean,
        override val node: Node? = null,
    ) : DeviceListEntry(name = name, fullAddress = fullAddress, bonded = bonded, node = node) {
        override fun copy(node: Node?): Usb =
            copy(usbData = usbData, name = name, fullAddress = fullAddress, bonded = bonded, node = node)
    }

    data class Tcp(override val name: String, override val fullAddress: String, override val node: Node? = null) :
        DeviceListEntry(name, fullAddress, true, node) {
        override fun copy(node: Node?): Tcp = copy(name = name, fullAddress = fullAddress, node = node)
    }

    data class Mock(override val name: String, override val node: Node? = null) :
        DeviceListEntry(name, "m", true, node) {
        override fun copy(node: Node?): Mock = copy(name = name, node = node)
    }
}

/** Matches names like Meshtastic_1234. */
private val bleNameRegex = Regex(BLE_NAME_PATTERN)

/** Returns the short name of the device if it's a Meshtastic device, otherwise null. */
fun BleDevice.getMeshtasticShortName(): String? = name?.let { bleNameRegex.find(it)?.groupValues?.get(1) }
