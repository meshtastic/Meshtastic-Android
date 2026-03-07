/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.model

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.RadioInterfaceService

/**
 * A sealed class is used here to represent the different types of devices that can be displayed in the list. This is
 * more type-safe and idiomatic than using a base class with boolean flags (e.g., isBLE, isUSB). It allows for
 * exhaustive `when` expressions in the code, making it more robust and readable.
 *
 * @param name The display name of the device.
 * @param fullAddress The unique address of the device, prefixed with a type identifier.
 * @param bonded Indicates whether the device is bonded (for BLE) or has permission (for USB).
 * @param node The [Node] associated with this device, if found in the database.
 */
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

    data class Ble(val device: BleDevice, override val node: Node? = null) :
        DeviceListEntry(
            name = device.name ?: "unnamed-${device.address}",
            fullAddress = "x${device.address}",
            bonded = device.isBonded,
            node = node,
        ) {
        override fun copy(node: Node?): Ble = copy(device = device, node = node)
    }

    data class Usb(
        private val radioInterfaceService: RadioInterfaceService,
        private val usbManager: UsbManager,
        val driver: UsbSerialDriver,
        override val node: Node? = null,
    ) : DeviceListEntry(
        name = driver.device.deviceName,
        fullAddress = radioInterfaceService.toInterfaceAddress(InterfaceId.SERIAL, driver.device.deviceName),
        bonded = usbManager.hasPermission(driver.device),
        node = node,
    ) {
        override fun copy(node: Node?): Usb =
            copy(radioInterfaceService = radioInterfaceService, usbManager = usbManager, driver = driver, node = node)
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

/**
 * Returns the short name of the device if it's a Meshtastic device, otherwise null.
 *
 * @return The short name (e.g., 1234) or null.
 */
fun BleDevice.getMeshtasticShortName(): String? = name?.let { bleNameRegex.find(it)?.groupValues?.get(1) }
