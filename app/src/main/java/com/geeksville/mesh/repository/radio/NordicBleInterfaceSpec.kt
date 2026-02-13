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
package com.geeksville.mesh.repository.radio

import co.touchlab.kermit.Logger
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.model.util.anonymize
import javax.inject.Inject

/** Bluetooth backend implementation. */
class NordicBleInterfaceSpec
@Inject
constructor(
    private val factory: NordicBleInterfaceFactory,
    private val bluetoothRepository: BluetoothRepository,
) : InterfaceSpec<NordicBleInterface> {
    override fun createInterface(rest: String): NordicBleInterface = factory.create(rest)

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    override fun addressValid(rest: String): Boolean {
        val allPaired = bluetoothRepository.state.value.bondedDevices.map { it.address }.toSet()
        return if (!allPaired.contains(rest)) {
            Logger.w { "Ignoring stale bond to ${rest.anonymize}" }
            false
        } else {
            true
        }
    }
}
