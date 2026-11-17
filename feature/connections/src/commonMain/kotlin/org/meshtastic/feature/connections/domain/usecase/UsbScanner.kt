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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.feature.connections.model.DeviceListEntry

/** Platform-specific scanner for USB/Serial devices. */
interface UsbScanner {
    fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>>

    /**
     * Whether discovering a device this scanner hasn't already found requires an explicit, gesture-driven request
     * action (e.g. Web Serial's `requestPort()` picker) rather than passive/ambient enumeration. `false` on every
     * platform except web: JVM/Android can already enumerate every attached serial device without prompting.
     */
    fun needsExplicitDeviceRequest(): Boolean = false

    /**
     * Triggers the platform's explicit device-request flow (a no-op where [needsExplicitDeviceRequest] is `false`). On
     * web this shows the browser's serial-port picker, so it must be called from a coroutine started directly inside a
     * user-gesture event handler (e.g. a button's onClick) with no earlier suspension — the same "transient activation"
     * constraint Web Bluetooth's device picker has.
     */
    suspend fun requestNewDevice() {}
}
