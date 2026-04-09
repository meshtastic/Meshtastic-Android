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
package org.meshtastic.core.ble

import com.juul.kable.Peripheral
import kotlin.concurrent.Volatile

/**
 * A simple global tracker for the currently active BLE connection. This resolves instance mismatch issues between
 * dynamically created UI devices (scanned vs bonded) and the actual connection.
 *
 * Fields are volatile to ensure visibility across AIDL binder threads and coroutine dispatchers.
 */
internal object ActiveBleConnection {
    @Volatile var activePeripheral: Peripheral? = null
    @Volatile var activeAddress: String? = null
}
