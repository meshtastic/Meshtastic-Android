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
package org.meshtastic.feature.wifiprovision.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.bleAddressesMatch
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.DISCOVERY_WINDOW
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_SERVICE_UUID
import org.meshtastic.feature.wifiprovision.model.DiscoveredDevice

/**
 * Finds provisioning devices for the user to choose from. It keeps the [BleDevice] behind every listed row, so the
 * device the user taps is the one connected rather than whichever advertises first.
 */
internal class DeviceDiscovery(private val scanner: BleScanner) {
    private val found = mutableMapOf<String, BleDevice>()

    /** The device behind the listed row with [address], or null when the current scan has not seen it. */
    fun deviceFor(address: String): BleDevice? = found[address]

    fun clear() {
        found.clear()
    }

    /** Scans for [DISCOVERY_WINDOW], reporting the whole list, strongest first, each time a device is seen. */
    suspend fun scan(onChange: (List<DiscoveredDevice>) -> Unit) {
        var listed = emptyList<DiscoveredDevice>()
        withTimeoutOrNull(DISCOVERY_WINDOW) {
            scanner.scan(DISCOVERY_WINDOW, WIRELESS_SERVICE_UUID).collect { device ->
                found[device.address] = device
                listed = upsertByStrength(listed, device)
                onChange(listed)
            }
        }
    }

    /** The name the device with [address] advertises, or null when it is not heard within [DISCOVERY_WINDOW]. */
    suspend fun advertisedName(address: String): String? = withTimeoutOrNull(DISCOVERY_WINDOW) {
        scanner.scan(DISCOVERY_WINDOW, WIRELESS_SERVICE_UUID, address).first {
            bleAddressesMatch(it.address, address)
        }
    }
        ?.name

    companion object {
        /**
         * Add or refresh [device] in [devices]. A new device goes in ahead of the first weaker one, so the list reads
         * strongest-first as it fills; a device already listed keeps its row, so rows do not move under a tap as
         * advertisements repeat. A missing RSSI ranks below every reading, never as 0 dBm.
         */
        fun upsertByStrength(devices: List<DiscoveredDevice>, device: BleDevice): List<DiscoveredDevice> {
            val index = devices.indexOfFirst { it.address == device.address }
            if (index >= 0) {
                val current = devices[index]
                val refreshed =
                    DiscoveredDevice(
                        name = device.name ?: current.name,
                        address = device.address,
                        rssi = device.rssi ?: current.rssi,
                    )
                return devices.toMutableList().also { it[index] = refreshed }
            }
            val entry = DiscoveredDevice(name = device.name, address = device.address, rssi = device.rssi)
            val position = devices.indexOfFirst { isStronger(entry.rssi, it.rssi) }
            return devices.toMutableList().also { it.add(if (position < 0) it.size else position, entry) }
        }

        private fun isStronger(candidate: Int?, other: Int?): Boolean = when {
            candidate == null -> false
            other == null -> true
            else -> candidate > other
        }
    }
}
