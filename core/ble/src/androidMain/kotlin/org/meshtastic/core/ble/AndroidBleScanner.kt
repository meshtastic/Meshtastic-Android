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
import no.nordicsemi.kotlin.ble.client.distinctByPeripheral
import org.koin.core.annotation.Single
import kotlin.time.Duration

/**
 * An Android implementation of [BleScanner] using Nordic's [CentralManager].
 *
 * @param centralManager The Nordic [CentralManager] to use for scanning.
 */
@Single
class AndroidBleScanner(private val centralManager: CentralManager) : BleScanner {

    override fun scan(timeout: Duration): Flow<BleDevice> =
        centralManager.scan(timeout).distinctByPeripheral().map { AndroidBleDevice(it.peripheral) }
}
