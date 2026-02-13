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
package com.geeksville.mesh.model

import android.hardware.usb.UsbManager
import com.geeksville.mesh.repository.radio.InterfaceId
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.hoho.android.usbserial.driver.UsbSerialDriver
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.BondState
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.model.util.anonymize

/**
 * A sealed class is used here to represent the different types of devices that can be displayed in the list. This is
 * more type-safe and idiomatic than using a base class with boolean flags (e.g., isBLE, isUSB). It allows for
 * exhaustive `when` expressions in the code, making it more robust and readable.
 *
 * @param name The display name of the device.
 * @param fullAddress The unique address of the device, prefixed with a type identifier.
 * @param bonded Indicates whether the device is bonded (for BLE) or has permission (for USB).
 */
sealed class DeviceListEntry(open val name: String, open val fullAddress: String, open val bonded: Boolean) {
    val address: String
        get() = fullAddress.substring(1)

    override fun toString(): String =
        "DeviceListEntry(name=${name.anonymize}, addr=${address.anonymize}, bonded=$bonded)"

    @Suppress("MissingPermission")
    data class Ble(val peripheral: Peripheral) :
        DeviceListEntry(
            name = peripheral.name ?: "unnamed-${peripheral.address}",
            fullAddress = "x${peripheral.address}",
            bonded = peripheral.bondState.value == BondState.BONDED,
        )

    data class Usb(
        private val radioInterfaceService: RadioInterfaceService,
        private val usbManager: UsbManager,
        val driver: UsbSerialDriver,
    ) : DeviceListEntry(
        name = driver.device.deviceName,
        fullAddress = radioInterfaceService.toInterfaceAddress(InterfaceId.SERIAL, driver.device.deviceName),
        bonded = usbManager.hasPermission(driver.device),
    )

    data class Tcp(override val name: String, override val fullAddress: String) :
        DeviceListEntry(name, fullAddress, true)

    data class Mock(override val name: String) : DeviceListEntry(name, "m", true)
}

/** Matches names like Meshtastic_1234. */
private val bleNameRegex = Regex(BLE_NAME_PATTERN)

/**
 * Returns the short name of the device if it's a Meshtastic device, otherwise null.
 *
 * @return The short name (e.g., 1234) or null.
 */
fun Peripheral.getMeshtasticShortName(): String? = name?.let { bleNameRegex.find(it)?.groupValues?.get(1) }
