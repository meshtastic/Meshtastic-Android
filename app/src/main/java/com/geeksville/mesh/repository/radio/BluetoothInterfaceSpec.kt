/*
 * Copyright (c) 2024 Meshtastic LLC
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

import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.util.anonymize
import javax.inject.Inject

/**
 * Bluetooth backend implementation.
 */
class BluetoothInterfaceSpec @Inject constructor(
    private val factory: BluetoothInterfaceFactory,
    private val bluetoothRepository: BluetoothRepository,
) : InterfaceSpec<BluetoothInterface>, Logging {
    override fun createInterface(rest: String): BluetoothInterface {
        return factory.create(rest)
    }

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    override fun addressValid(rest: String): Boolean {
        val allPaired = bluetoothRepository.state.value.bondedDevices
            .map { it.address }.toSet()
        return if (!allPaired.contains(rest)) {
            warn("Ignoring stale bond to ${rest.anonymize}")
            false
        } else {
            true
        }
    }
}
