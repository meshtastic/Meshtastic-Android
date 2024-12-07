/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.repository.radio

import android.hardware.usb.UsbManager
import com.geeksville.mesh.repository.usb.UsbRepository
import com.hoho.android.usbserial.driver.UsbSerialDriver
import javax.inject.Inject

/**
 * Serial/USB interface backend implementation.
 */
class SerialInterfaceSpec @Inject constructor(
    private val factory: SerialInterfaceFactory,
    private val usbManager: dagger.Lazy<UsbManager>,
    private val usbRepository: UsbRepository,
) : InterfaceSpec<SerialInterface> {
    override fun createInterface(rest: String): SerialInterface {
        return factory.create(rest)
    }

    override fun addressValid(
        rest: String
    ): Boolean {
        usbRepository.serialDevicesWithDrivers.value.filterValues {
            usbManager.get().hasPermission(it.device)
        }
        findSerial(rest)?.let { d ->
            return usbManager.get().hasPermission(d.device)
        }
        return false
    }

    internal fun findSerial(rest: String): UsbSerialDriver? {
        val deviceMap = usbRepository.serialDevicesWithDrivers.value
        return if (deviceMap.containsKey(rest)) {
            deviceMap[rest]!!
        } else {
            deviceMap.map { (_, driver) -> driver }.firstOrNull()
        }
    }
}
