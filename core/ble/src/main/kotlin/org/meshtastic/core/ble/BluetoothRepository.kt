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
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.android.AndroidEnvironment
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.di.ProcessLifecycle
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** Repository responsible for maintaining and updating the state of Bluetooth availability. */
@Singleton
class BluetoothRepository
@Inject
constructor(
    private val dispatchers: CoroutineDispatchers,
    @ProcessLifecycle private val processLifecycle: Lifecycle,
    private val centralManager: CentralManager,
    private val androidEnvironment: AndroidEnvironment,
    private val bleScanner: BleScanner,
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

    private val _scannedDevices = MutableStateFlow<List<Peripheral>>(emptyList())
    val scannedDevices: StateFlow<List<Peripheral>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanJob: Job? = null

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            androidEnvironment.bluetoothState.collect { updateBluetoothState() }
        }
    }

    fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
    }

    /** @return true for a valid Bluetooth address, false otherwise */
    fun isValid(bleAddress: String): Boolean = BluetoothAdapter.checkBluetoothAddress(bleAddress)

    /** Starts a BLE scan for Meshtastic devices. The results are published to the [scannedDevices] flow. */
    @SuppressLint("MissingPermission")
    fun startScan() {
        if (isScanning.value) return

        scanJob?.cancel()
        _scannedDevices.value = emptyList()

        scanJob =
            processLifecycle.coroutineScope.launch(dispatchers.default) {
                bleScanner
                    .scan(5.seconds) { ServiceUuid(SERVICE_UUID) }
                    .onStart { _isScanning.value = true }
                    .onCompletion { _isScanning.value = false }
                    .catch { ex ->
                        Logger.w(ex) { "Bluetooth scan failed" }
                        _isScanning.value = false
                    }
                    .collect { peripheral ->
                        // Add or update the peripheral in our list
                        val currentList = _scannedDevices.value
                        _scannedDevices.value =
                            (currentList.filterNot { it.address == peripheral.address } + peripheral)
                    }
            }
    }

    /** Stops the currently active BLE scan. */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    /**
     * Initiates bonding with the given peripheral. This is a suspending function that completes when the bonding
     * process is finished. After successful bonding, the repository's state is refreshed to include the new bonded
     * device.
     *
     * @param peripheral The peripheral to bond with.
     * @throws SecurityException if required Bluetooth permissions are not granted.
     * @throws Exception if the bonding process fails.
     */
    @SuppressLint("MissingPermission")
    suspend fun bond(peripheral: Peripheral) {
        peripheral.createBond()
        refreshState()
    }

    internal suspend fun updateBluetoothState() {
        val hasPerms =
            if (androidEnvironment.androidSdkVersion >= Build.VERSION_CODES.S) {
                androidEnvironment.isBluetoothScanPermissionGranted &&
                    androidEnvironment.isBluetoothConnectPermissionGranted
            } else {
                androidEnvironment.isLocationPermissionGranted
            }
        val enabled = androidEnvironment.isBluetoothEnabled
        val newState =
            BluetoothState(
                hasPermissions = hasPerms,
                enabled = enabled,
                bondedDevices = getBondedAppPeripherals(enabled, hasPerms),
            )

        _state.emit(newState)
        Logger.d { "Detected our bluetooth access=$newState" }
    }

    @SuppressLint("MissingPermission")
    private fun getBondedAppPeripherals(enabled: Boolean, hasPerms: Boolean): List<Peripheral> =
        if (enabled && hasPerms) {
            centralManager.getBondedPeripherals().filter(::isMatchingPeripheral)
        } else {
            emptyList()
        }

    /** Checks if a peripheral is one of ours, either by its advertised name or by the services it provides. */
    private fun isMatchingPeripheral(peripheral: Peripheral): Boolean {
        val nameMatches = peripheral.name?.matches(Regex(BLE_NAME_PATTERN)) ?: false
        val hasRequiredService = peripheral.services(listOf(SERVICE_UUID)).value?.isNotEmpty() ?: false

        return nameMatches || hasRequiredService
    }
}
