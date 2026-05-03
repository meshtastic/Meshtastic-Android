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
 * along with this program, if not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.ble

import com.juul.kable.Peripheral

/**
 * Build a Kable [Peripheral] for a previously saved BLE MAC address.
 *
 * Uses `autoConnect = true` (bonded-device path) since there is no live advertisement.
 * Platform-specific MTU negotiation and threading strategy are applied via [platformConfig].
 *
 * Intended for use in [RadioClientProvider][org.meshtastic.app.radio.RadioClientProvider]
 * when reconstructing a [BleTransport] from a persisted radio address.
 *
 * SDK gap F: [org.meshtastic.sdk.transport.ble.BleTransport] currently requires a caller-supplied
 * [Peripheral] — it has no factory that accepts a MAC address string directly. This function
 * bridges that gap on the Android side until the SDK exposes a convenience constructor.
 */
public fun buildPeripheralForSavedAddress(address: String): Peripheral {
    val device = MeshtasticBleDevice(address)
    return createPeripheral(address) {
        platformConfig(device, autoConnect = { true })
    }
}
