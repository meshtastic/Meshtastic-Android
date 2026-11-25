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
package org.meshtastic.feature.connections.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import org.meshtastic.core.network.SerialPortEntry
import org.meshtastic.core.network.availableSerialPorts
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.JvmUsbDeviceData
import kotlin.coroutines.coroutineContext

@Single
class JvmUsbScanner : UsbScanner {
    override fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>> = flow {
        while (coroutineContext.isActive) {
            emit(usbEntries(availableSerialPorts()))
            delay(POLL_INTERVAL_MS)
        }
    }
        .distinctUntilChanged()

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
    }
}

/** Picker entries for [ports]: shown by system name, stored by the address that survives re-enumeration. */
internal fun usbEntries(ports: List<SerialPortEntry>): List<DeviceListEntry.Usb> = ports.map { port ->
    DeviceListEntry.Usb(
        usbData = JvmUsbDeviceData(port.name),
        name = port.name,
        fullAddress = "s${port.address}",
        bonded = true, // Desktop serial ports don't need Android USB permission bonding
        node = null,
    )
}
