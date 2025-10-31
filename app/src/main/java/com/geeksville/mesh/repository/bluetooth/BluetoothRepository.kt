/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.repository.radio.BluetoothInterface
import com.geeksville.mesh.util.registerReceiverCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.common.hasBluetoothPermission
import org.meshtastic.core.di.CoroutineDispatchers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Repository responsible for maintaining and updating the state of Bluetooth availability. */
@Singleton
class BluetoothRepository
@Inject
constructor(
    private val application: Application,
    private val bluetoothAdapterLazy: dagger.Lazy<BluetoothAdapter?>,
    private val bluetoothBroadcastReceiverLazy: dagger.Lazy<BluetoothBroadcastReceiver>,
    private val dispatchers: CoroutineDispatchers,
    private val processLifecycle: Lifecycle,
) {
    private val _state =
        MutableStateFlow(
            BluetoothState(
                // Assume we have permission until we get our initial state update to prevent premature
                // notifications to the user.
                hasPermissions = true,
            ),
        )
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            updateBluetoothState()
            bluetoothBroadcastReceiverLazy.get().let { receiver ->
                application.registerReceiverCompat(receiver, receiver.intentFilter)
            }
        }
    }

    fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    /** @return true for a valid Bluetooth address, false otherwise */
    fun isValid(bleAddress: String): Boolean = BluetoothAdapter.checkBluetoothAddress(bleAddress)

    fun getRemoteDevice(address: String): BluetoothDevice? = bluetoothAdapterLazy
        .get()
        ?.takeIf { application.hasBluetoothPermission() && isValid(address) }
        ?.getRemoteDevice(address)

    private fun getBluetoothLeScanner(): BluetoothLeScanner? =
        bluetoothAdapterLazy.get()?.takeIf { application.hasBluetoothPermission() }?.bluetoothLeScanner

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scan(): Flow<ScanResult> {
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BluetoothInterface.BTM_SERVICE_UUID)).build()

        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        return getBluetoothLeScanner()?.scan(listOf(filter), settings) ?: emptyFlow()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun createBond(device: BluetoothDevice): Flow<Int> = device.createBond(application)

    internal suspend fun updateBluetoothState() {
        val hasPerms = application.hasBluetoothPermission()
        val newState: BluetoothState =
            bluetoothAdapterLazy.get()?.let { adapter ->
                val enabled = adapter.isEnabled
                val bondedDevices = adapter.takeIf { hasPerms }?.bondedDevices ?: emptySet()

                BluetoothState(
                    hasPermissions = hasPerms,
                    enabled = enabled,
                    bondedDevices =
                    if (!enabled) {
                        emptyList()
                    } else {
                        bondedDevices.toList()
                    },
                )
            } ?: BluetoothState()

        _state.emit(newState)
        Timber.d("Detected our bluetooth access=$newState")
    }
}
