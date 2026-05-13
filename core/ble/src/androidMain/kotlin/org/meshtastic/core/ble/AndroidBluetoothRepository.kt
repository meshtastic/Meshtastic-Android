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

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.coroutines.resume

/** Android implementation of [BluetoothRepository]. */
@Single
class AndroidBluetoothRepository(
    private val context: Context,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
) : BluetoothRepository {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow(BluetoothState(hasPermissions = hasBluetoothPermissions()))
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private val deviceCache = mutableMapOf<String, MeshtasticBleDevice>()

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    private fun hasBluetoothPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasConnect =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        val hasScan =
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        hasConnect && hasScan
    } else {
        // Pre-Android 12: classic Bluetooth permissions are install-time.
        true
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

        suspendCancellableCoroutine<Unit> { cont ->
            val receiver =
                object : android.content.BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    override fun onReceive(c: Context, intent: android.content.Intent) {
                        if (intent.action == android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                            val d =
                                androidx.core.content.IntentCompat.getParcelableExtra(
                                    intent,
                                    android.bluetooth.BluetoothDevice.EXTRA_DEVICE,
                                    android.bluetooth.BluetoothDevice::class.java,
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
                                    if (cont.isActive) cont.resume(Unit)
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
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

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
        var hasPermissions = hasBluetoothPermissions()
        val bondedDevices =
            if (hasPermissions) {
                try {
                    getBondedAppPeripherals()
                } catch (e: SecurityException) {
                    Logger.w(e) { "SecurityException accessing bonded devices. Missing BLUETOOTH_CONNECT?" }
                    hasPermissions = false
                    emptyList()
                }
            } else {
                emptyList()
            }

        val newState = BluetoothState(hasPermissions = hasPermissions, enabled = enabled, bondedDevices = bondedDevices)

        _state.emit(newState)
        Logger.d { "Detected our bluetooth access=$newState" }
    }

    @SuppressLint("MissingPermission")
    private fun getBondedAppPeripherals(): List<BleDevice> {
        val bonded = bluetoothAdapter?.bondedDevices ?: return emptyList()
        val bondedAddresses = bonded.mapTo(mutableSetOf()) { it.address }
        // Evict entries for devices that are no longer bonded and update names in case the
        // user renamed the device in firmware since the cache was populated.
        deviceCache.keys.retainAll(bondedAddresses)
        return bonded.map { device ->
            val cached = deviceCache.getOrPut(device.address) { MeshtasticBleDevice(device.address, device.name) }
            // If the name changed (firmware rename, etc.), replace the cached entry and return the new one.
            if (cached.name != device.name) {
                val updated = MeshtasticBleDevice(device.address, device.name)
                deviceCache[device.address] = updated
                updated
            } else {
                cached
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun isBonded(address: String): Boolean = try {
        bluetoothAdapter?.bondedDevices?.any { it.address.equals(address, ignoreCase = true) } ?: false
    } catch (e: SecurityException) {
        Logger.w(e) { "SecurityException checking bonded devices. Missing BLUETOOTH_CONNECT?" }
        false
    }
}
