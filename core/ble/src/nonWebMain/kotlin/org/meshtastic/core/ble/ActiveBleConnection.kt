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

/** Snapshot of the currently active BLE peripheral and its address, updated atomically. */
internal data class ActiveConnection(val peripheral: Peripheral, val address: String)

/**
 * A simple global tracker for the currently active BLE connection. This resolves instance mismatch issues between
 * dynamically created UI devices (scanned vs bonded) and the actual connection.
 *
 * [active] is a single volatile reference so readers always see a consistent peripheral/address pair — the previous
 * two-field design (`activePeripheral` + `activeAddress`) was susceptible to TOCTOU races when fields were updated
 * non-atomically.
 */
internal object ActiveBleConnection {
    @Volatile var active: ActiveConnection? = null
}
