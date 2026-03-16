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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers

/** Android implementation of [BluetoothRepository]. */
@Single
class AndroidBluetoothRepository(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
) : BluetoothRepository {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow(BluetoothState(hasPermissions = true))
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private val deviceCache = mutableMapOf<String, DirectBleDevice>()

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    override fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    override fun isValid(bleAddress: String): Boolean = BluetoothAdapter.checkBluetoothAddress(bleAddress)

    @Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught", "SwallowedException")
    @SuppressLint("MissingPermission")
    override suspend fun bond(device: BleDevice) {
        val macAddress = device.address
        val remoteDevice =
            bluetoothAdapter?.getRemoteDevice(macAddress) ?: throw Exception("Bluetooth adapter unavailable")

        if (remoteDevice.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
            updateBluetoothState()
            return
        }

        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
            val receiver =
                object : android.content.BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    override fun onReceive(c: Context, intent: android.content.Intent) {
                        if (intent.action == android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                            val d =
                                intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(
                                    android.bluetooth.BluetoothDevice.EXTRA_DEVICE,
                                )
                            if (d?.address?.equals(macAddress, ignoreCase = true) == true) {
                                val state =
                                    intent.getIntExtra(
                                        android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE,
                                        android.bluetooth.BluetoothDevice.ERROR,
                                    )
                                val prevState =
                                    intent.getIntExtra(
                                        android.bluetooth.BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                        android.bluetooth.BluetoothDevice.ERROR,
                                    )

                                if (state == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                                    try {
                                        context.unregisterReceiver(this)
                                    } catch (ignored: Exception) {}
                                    if (cont.isActive) cont.resume(Unit) {}
                                } else if (
                                    state == android.bluetooth.BluetoothDevice.BOND_NONE &&
                                    prevState == android.bluetooth.BluetoothDevice.BOND_BONDING
                                ) {
                                    try {
                                        context.unregisterReceiver(this)
                                    } catch (ignored: Exception) {}
                                    if (cont.isActive) {
                                        cont.resumeWith(Result.failure(Exception("Bonding failed or rejected")))
                                    }
                                }
                            }
                        }
                    }
                }

            val filter = android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(receiver, filter)

            cont.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (ignored: Exception) {}
            }

            if (!remoteDevice.createBond()) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (ignored: Exception) {}
                if (cont.isActive) cont.resumeWith(Result.failure(Exception("Failed to initiate bonding")))
            }
        }
        updateBluetoothState()
    }

    internal suspend fun updateBluetoothState() {
        val enabled = bluetoothAdapter?.isEnabled == true
        val newState =
            BluetoothState(
                hasPermissions = true, // Simplified for now, should check actual manifest perms
                enabled = enabled,
                bondedDevices = getBondedAppPeripherals(),
            )

        _state.emit(newState)
        Logger.d { "Detected our bluetooth access=$newState" }
    }

    @SuppressLint("MissingPermission")
    private fun getBondedAppPeripherals(): List<BleDevice> = bluetoothAdapter?.bondedDevices?.map { device ->
        deviceCache.getOrPut(device.address) { DirectBleDevice(device.address, device.name) }
    } ?: emptyList()

    @SuppressLint("MissingPermission")
    override fun isBonded(address: String): Boolean = try {
        bluetoothAdapter?.bondedDevices?.any { it.address.equals(address, ignoreCase = true) } ?: false
    } catch (e: SecurityException) {
        Logger.w(e) { "SecurityException checking bonded devices. Missing BLUETOOTH_CONNECT?" }
        false
    }
}
