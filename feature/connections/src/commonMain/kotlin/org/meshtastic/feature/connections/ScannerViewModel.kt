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
package org.meshtastic.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase

@KoinViewModel
@Suppress("LongParameterList", "TooManyFunctions")
open class ScannerViewModel(
    protected val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    private val radioInterfaceService: RadioInterfaceService,
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
) : ViewModel() {
    val showMockInterface: StateFlow<Boolean> = MutableStateFlow(radioInterfaceService.isMockInterface()).asStateFlow()

    private val _errorText = MutableStateFlow<String?>(null)
    val errorText: StateFlow<String?> = _errorText.asStateFlow()

    private val discoveredDevicesFlow =
        showMockInterface
            .flatMapLatest { showMock -> getDiscoveredDevicesUseCase.invoke(showMock) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** A combined list of bonded BLE devices for the UI. */
    val bleDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow
            .map { it?.bleDevices ?: emptyList() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** UI StateFlow for USB devices. */
    val usbDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it?.usbDevices ?: emptyList() }.stateInWhileSubscribed(initialValue = emptyList())

    /** UI StateFlow for discovered TCP devices. */
    val discoveredTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow
            .map { it?.discoveredTcpDevices ?: emptyList() }
            .stateInWhileSubscribed(initialValue = emptyList())

    /** UI StateFlow for recently connected TCP devices that are not currently discovered. */
    val recentTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow
            .map { it?.recentTcpDevices ?: emptyList() }
            .stateInWhileSubscribed(initialValue = emptyList())

    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    val selectedNotNullFlow: StateFlow<String> =
        selectedAddressFlow
            .map { it ?: NO_DEVICE_SELECTED }
            .stateInWhileSubscribed(initialValue = selectedAddressFlow.value ?: NO_DEVICE_SELECTED)

    val supportedDeviceTypes: List<org.meshtastic.core.model.DeviceType> = radioInterfaceService.supportedDeviceTypes

    init {
        serviceRepository.connectionProgress.onEach { _errorText.value = it }.launchIn(viewModelScope)
        Logger.d { "ScannerViewModel created" }
    }

    override fun onCleared() {
        super.onCleared()
        Logger.d { "ScannerViewModel cleared" }
    }

    fun setErrorText(text: String) {
        _errorText.value = text
    }

    fun changeDeviceAddress(address: String) {
        Logger.i { "Attempting to change device address to ${address.anonymize()}" }
        radioController.setDeviceAddress(address)
    }

    fun addRecentAddress(address: String, name: String) {
        if (!address.startsWith("t")) return
        viewModelScope.launch { recentAddressesDataSource.add(RecentAddress(address, name)) }
    }

    fun removeRecentAddress(address: String) {
        viewModelScope.launch { recentAddressesDataSource.remove(address) }
    }

    /**
     * Called by the GUI when a new device has been selected by the user.
     *
     * @return true if the connection was initiated immediately.
     */
    fun onSelected(it: DeviceListEntry): Boolean = when (it) {
        is DeviceListEntry.Ble -> {
            if (it.bonded) {
                changeDeviceAddress(it.fullAddress)
                true
            } else {
                requestBonding(it)
                false
            }
        }
        is DeviceListEntry.Usb -> {
            if (it.bonded) {
                changeDeviceAddress(it.fullAddress)
                true
            } else {
                requestPermission(it)
                false
            }
        }
        is DeviceListEntry.Tcp -> {
            viewModelScope.launch {
                addRecentAddress(it.fullAddress, it.name)
                changeDeviceAddress(it.fullAddress)
            }
            true
        }
        is DeviceListEntry.Mock -> {
            changeDeviceAddress(it.fullAddress)
            true
        }
    }

    /** Initiates the bonding process and connects to the device upon success. */
    protected open fun requestBonding(entry: DeviceListEntry.Ble) {}

    protected open fun requestPermission(entry: DeviceListEntry.Usb) {}

    fun disconnect() {
        changeDeviceAddress(NO_DEVICE_SELECTED)
    }
}

const val NO_DEVICE_SELECTED = "n"
