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

package com.geeksville.mesh.repository.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.geeksville.mesh.util.exceptionReporter
import javax.inject.Inject

/**
 * A helper class to call onChanged when bluetooth is enabled or disabled
 */
class BluetoothBroadcastReceiver @Inject constructor(
    private val bluetoothRepository: BluetoothRepository
) : BroadcastReceiver() {
    internal val intentFilter get() = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    }

    override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            when (intent.bluetoothAdapterState) {
                // Simulate a disconnection if the user disables bluetooth entirely
                BluetoothAdapter.STATE_OFF -> bluetoothRepository.refreshState()
                BluetoothAdapter.STATE_ON -> bluetoothRepository.refreshState()
            }
        }
        if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            bluetoothRepository.refreshState()
        }
    }

    private val Intent.bluetoothAdapterState: Int
        get() = getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
}
