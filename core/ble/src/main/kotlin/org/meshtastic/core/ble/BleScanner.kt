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
package org.meshtastic.core.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConjunctionFilterScope
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.distinctByPeripheral
import javax.inject.Inject
import kotlin.time.Duration

/**
 * A wrapper around [CentralManager]'s scanning capabilities to provide a consistent and easy-to-use API for BLE
 * scanning across the application.
 *
 * @param centralManager The Nordic [CentralManager] to use for scanning.
 */
class BleScanner @Inject constructor(private val centralManager: CentralManager) {

    /**
     * Scans for BLE devices.
     *
     * @param timeout The duration of the scan.
     * @param filterBlock Optional filter configuration block.
     * @return A [Flow] of discovered [Peripheral]s.
     */
    fun scan(timeout: Duration, filterBlock: (ConjunctionFilterScope.() -> Unit)? = null): Flow<Peripheral> =
        if (filterBlock != null) {
            centralManager.scan(timeout, filterBlock)
        } else {
            centralManager.scan(timeout)
        }
            .distinctByPeripheral()
            .map { it.peripheral }
}
