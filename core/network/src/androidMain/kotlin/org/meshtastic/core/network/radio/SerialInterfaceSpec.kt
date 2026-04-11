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
package org.meshtastic.core.network.radio

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import org.koin.core.annotation.Single
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.repository.RadioInterfaceService

/** Serial/USB interface backend implementation. */
@Single
class SerialInterfaceSpec(
    private val factory: SerialInterfaceFactory,
    private val usbManager: UsbManager,
    private val usbRepository: UsbRepository,
) : InterfaceSpec<SerialInterface> {
    override fun createInterface(rest: String, service: RadioInterfaceService): SerialInterface =
        factory.create(rest, service)

    override fun addressValid(rest: String): Boolean {
        val driver = findSerial(rest) ?: return false
        return usbManager.hasPermission(driver.device)
    }

    internal fun findSerial(rest: String): UsbSerialDriver? {
        val deviceMap = usbRepository.serialDevices.value
        return deviceMap[rest] ?: deviceMap.values.firstOrNull()
    }
}
