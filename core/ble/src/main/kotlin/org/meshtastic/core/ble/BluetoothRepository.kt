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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.RemoteServices
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.android.AndroidEnvironment
import org.meshtastic.core.ble.MeshtasticBleConstants.BLE_NAME_PATTERN
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.di.ProcessLifecycle
import javax.inject.Inject
import javax.inject.Singleton

/** Repository responsible for maintaining and updating the state of Bluetooth availability. */
@Singleton
class BluetoothRepository
@Inject
constructor(
    private val dispatchers: CoroutineDispatchers,
    @ProcessLifecycle private val processLifecycle: Lifecycle,
    private val centralManager: CentralManager,
    private val androidEnvironment: AndroidEnvironment,
) {
    private val _state =
        MutableStateFlow(
            BluetoothState(
                // Assume we have permission until we get our initial state update to prevent premature
                // notifications to the user.
                hasPermissions = true,
            ),
        )
    val state: StateFlow<BluetoothState>
        get() = _state.asStateFlow()

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
        updateBluetoothState()
    }

    internal suspend fun updateBluetoothState() {
        val hasPerms = hasRequiredPermissions()
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

    /** @return true if the given address is currently bonded to the system. */
    @SuppressLint("MissingPermission")
    fun isBonded(address: String): Boolean {
        val enabled = androidEnvironment.isBluetoothEnabled
        val hasPerms = hasRequiredPermissions()
        return if (enabled && hasPerms) {
            centralManager.getBondedPeripherals().any { it.address == address }
        } else {
            false
        }
    }

    private fun hasRequiredPermissions(): Boolean = if (androidEnvironment.requiresBluetoothRuntimePermissions) {
        androidEnvironment.isBluetoothScanPermissionGranted &&
            androidEnvironment.isBluetoothConnectPermissionGranted
    } else {
        androidEnvironment.isLocationPermissionGranted
    }

    /** Checks if a peripheral is one of ours, either by its advertised name or by the services it provides. */
    private fun isMatchingPeripheral(peripheral: Peripheral): Boolean {
        val nameMatches = peripheral.name?.matches(Regex(BLE_NAME_PATTERN)) ?: false
        val hasRequiredService =
            (peripheral.services(listOf(SERVICE_UUID)).value as? RemoteServices.Discovered)?.services?.isNotEmpty()
                ?: false

        return nameMatches || hasRequiredService
    }
}
