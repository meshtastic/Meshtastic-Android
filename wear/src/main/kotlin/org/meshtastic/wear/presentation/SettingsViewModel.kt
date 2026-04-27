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
package org.meshtastic.wear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.BluetoothState
import org.meshtastic.core.ble.MeshtasticBleDevice
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.WatchPrefs
import kotlin.time.Duration.Companion.seconds

import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

sealed interface PhoneConnectionState {
    data object Disconnected : PhoneConnectionState

    data object Scanning : PhoneConnectionState

    data class Connecting(val device: BleDevice) : PhoneConnectionState

    data class Connected(val device: BleDevice) : PhoneConnectionState

    data object NoPermissions : PhoneConnectionState

    /** Indicates the phone is reachable via the Data Layer API. */
    data object PhoneLinked : PhoneConnectionState
}

@KoinViewModel
class SettingsViewModel(
    private val bleScanner: BleScanner,
    private val bluetoothRepository: BluetoothRepository,
    private val radioController: RadioController,
    private val radioPrefs: RadioPrefs,
    private val transportFactory: RadioTransportFactory,
    private val watchPrefs: WatchPrefs,
    application: android.app.Application,
) : ViewModel() {

    private val context = application.applicationContext
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isPhoneLinked = MutableStateFlow(false)
    val isPhoneLinked: StateFlow<Boolean> = _isPhoneLinked.asStateFlow()

    val highContrastModeEnabled = watchPrefs.highContrastModeEnabled

    fun setHighContrastModeEnabled(enabled: Boolean) {
        watchPrefs.setHighContrastModeEnabled(enabled)
    }

    val bluetoothState = bluetoothRepository.state

    init {
        checkPhoneLink()
    }

    private fun checkPhoneLink() {
        viewModelScope.launch {
            try {
                val nodes = com.google.android.gms.wearable.Wearable.getNodeClient(context).connectedNodes.await()
                _isPhoneLinked.value = nodes.isNotEmpty()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to check phone link" }
            }
        }
    }

    val connectionState: StateFlow<PhoneConnectionState> =
        combine(
            _isScanning,
            _isPhoneLinked,
            bluetoothState,
            radioController.connectionState,
            radioPrefs.devAddr,
            radioPrefs.devName,
        ) { args ->
            val scanning = args[0] as Boolean
            val linked = args[1] as Boolean
            val btState = args[2] as BluetoothState
            val radioState = args[3] as ConnectionState
            val devAddr = args[4] as String?
            val devName = args[5] as String?

            when {
                linked -> PhoneConnectionState.PhoneLinked
                !btState.hasPermissions -> PhoneConnectionState.NoPermissions
                scanning -> PhoneConnectionState.Scanning
                radioState is ConnectionState.Connected && devAddr != null -> {
                    val device =
                        MeshtasticBleDevice(
                            address = devAddr.removePrefix(InterfaceId.BLUETOOTH.id.toString()),
                            name = devName ?: "Phone",
                        )
                    PhoneConnectionState.Connected(device)
                }
                radioState is ConnectionState.Connecting && devAddr != null -> {
                    val device =
                        MeshtasticBleDevice(
                            address = devAddr.removePrefix(InterfaceId.BLUETOOTH.id.toString()),
                            name = devName ?: "Phone",
                        )
                    PhoneConnectionState.Connecting(device)
                }
                else -> PhoneConnectionState.Disconnected
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhoneConnectionState.Disconnected)

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (!bluetoothState.value.hasPermissions) {
            return
        }
        scanJob?.cancel()
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        scanJob =
            viewModelScope.launch {
                try {
                    // Scan for 30 seconds
                    bleScanner.scan(30.seconds).collect { device ->
                        _discoveredDevices.update { current ->
                            if (current.any { it.address == device.address }) {
                                current
                            } else {
                                current + device
                            }
                        }
                    }
                } finally {
                    _isScanning.value = false
                }
            }
    }

    fun stopScan() {
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun connectToDevice(device: BleDevice) {
        Logger.i { "Connecting to device: ${device.name} (${device.address})" }
        stopScan()
        val address = transportFactory.toInterfaceAddress(InterfaceId.BLUETOOTH, device.address)
        radioPrefs.setDevName(device.name)
        radioController.setDeviceAddress(address)
    }

    fun disconnect() {
        radioController.setDeviceAddress("")
        radioPrefs.setDevAddr(null)
        radioPrefs.setDevName(null)
    }

    fun requestSync() {
        viewModelScope.launch {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(context).sendMessage(node.id, "/request_sync", null).await()
                }
                Logger.d { "Sync request sent to ${nodes.size} nodes" }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to send sync request" }
            }
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
