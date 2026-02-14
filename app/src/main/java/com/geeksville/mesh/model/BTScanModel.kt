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
package com.geeksville.mesh.model

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.repository.network.NetworkRepository.Companion.toAddressString
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.service.MeshService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.meshtastic
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class BTScanModel
@Inject
constructor(
    private val application: Application,
    private val serviceRepository: ServiceRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val usbRepository: UsbRepository,
    private val usbManagerLazy: dagger.Lazy<UsbManager>,
    private val networkRepository: NetworkRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val recentAddressesDataSource: RecentAddressesDataSource,
) : ViewModel() {
    private val context: Context
        get() = application.applicationContext

    val showMockInterface: StateFlow<Boolean>
        get() = MutableStateFlow(radioInterfaceService.isMockInterface()).asStateFlow()

    val errorText = MutableLiveData<String?>(null)
    private val bondedBleDevicesFlow: StateFlow<List<DeviceListEntry.Ble>> =
        bluetoothRepository.state
            .map { ble -> ble.bondedDevices.map { DeviceListEntry.Ble(it) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val scannedBleDevicesFlow: StateFlow<List<DeviceListEntry.Ble>> =
        bluetoothRepository.scannedDevices
            .map { peripherals -> peripherals.map { DeviceListEntry.Ble(it) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Flow for discovered TCP devices, using recent addresses for potential name enrichment
    private val processedDiscoveredTcpDevicesFlow: StateFlow<List<DeviceListEntry.Tcp>> =
        combine(networkRepository.resolvedList, recentAddressesDataSource.recentAddresses) { tcpServices, recentList ->
            val recentMap = recentList.associateBy({ it.address }, { it.name })
            tcpServices
                .map { service ->
                    val address = "t${service.toAddressString()}"
                    val txtRecords = service.attributes // Map<String, ByteArray?>
                    val shortNameBytes = txtRecords["shortname"]
                    val idBytes = txtRecords["id"]

                    val shortName =
                        shortNameBytes?.let { String(it, Charsets.UTF_8) } ?: getString(Res.string.meshtastic)
                    val deviceId = idBytes?.let { String(it, Charsets.UTF_8) }?.replace("!", "")
                    var displayName = recentMap[address] ?: shortName
                    if (deviceId != null && !displayName.split("_").none { it == deviceId }) {
                        displayName += "_$deviceId"
                    }
                    DeviceListEntry.Tcp(displayName, address)
                }
                .sortedBy { it.name }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** A combined list of bonded and scanned BLE devices for the UI. */
    val bleDevicesForUi: StateFlow<List<DeviceListEntry>> =
        combine(bondedBleDevicesFlow, scannedBleDevicesFlow) { bonded, scanned ->
            val bondedAddresses = bonded.map { it.fullAddress }.toSet()
            val uniqueScanned = scanned.filterNot { it.fullAddress in bondedAddresses }
            (bonded + uniqueScanned).sortedBy { it.name }
        }
            .stateInWhileSubscribed(initialValue = emptyList())

    private val usbDevicesFlow: StateFlow<List<DeviceListEntry.Usb>> =
        usbRepository.serialDevicesWithDrivers
            .map { usb -> usb.map { (_, d) -> DeviceListEntry.Usb(radioInterfaceService, usbManagerLazy.get(), d) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mockDevice = DeviceListEntry.Mock("Demo Mode")

    // Flow for recent TCP devices, filtered to exclude any currently discovered devices
    val usbDevicesForUi: StateFlow<List<DeviceListEntry>> =
        combine(usbDevicesFlow, showMockInterface) { usb, showMock ->
            usb + if (showMock) listOf(mockDevice) else emptyList()
        }
            .stateInWhileSubscribed(initialValue = if (showMockInterface.value) listOf(mockDevice) else emptyList())

    private val filteredRecentTcpDevicesFlow: StateFlow<List<DeviceListEntry.Tcp>> =
        combine(recentAddressesDataSource.recentAddresses, processedDiscoveredTcpDevicesFlow) {
                recentList,
                discoveredDevices,
            ->
            val discoveredDeviceAddresses = discoveredDevices.map { it.fullAddress }.toSet()
            recentList
                .filterNot { recentAddress -> discoveredDeviceAddresses.contains(recentAddress.address) }
                .map { recentAddress -> DeviceListEntry.Tcp(recentAddress.name, recentAddress.address) }
                .sortedBy { it.name }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** UI StateFlow for discovered TCP devices. */
    val discoveredTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        processedDiscoveredTcpDevicesFlow.stateInWhileSubscribed(initialValue = listOf())

    /** UI StateFlow for recently connected TCP devices that are not currently discovered. */
    val recentTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        filteredRecentTcpDevicesFlow.stateInWhileSubscribed(initialValue = listOf())

    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    val selectedNotNullFlow: StateFlow<String> =
        selectedAddressFlow
            .map { it ?: NO_DEVICE_SELECTED }
            .stateInWhileSubscribed(initialValue = selectedAddressFlow.value ?: NO_DEVICE_SELECTED)

    val spinner: StateFlow<Boolean> = bluetoothRepository.isScanning

    init {
        serviceRepository.connectionProgress.onEach { errorText.value = it }.launchIn(viewModelScope)
        Logger.d { "BTScanModel created" }
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothRepository.stopScan()
        Logger.d { "BTScanModel cleared" }
    }

    fun setErrorText(text: String) {
        errorText.value = text
    }

    fun stopScan() {
        Logger.d { "stopping scan" }
        bluetoothRepository.stopScan()
    }

    fun refreshPermissions() {
        bluetoothRepository.refreshState()
    }

    fun startScan() {
        Logger.d { "starting ble scan" }
        bluetoothRepository.startScan()
    }

    private fun changeDeviceAddress(address: String) {
        try {
            serviceRepository.meshService?.let { service -> MeshService.changeDeviceAddress(context, service, address) }
        } catch (ex: RemoteException) {
            Logger.e(ex) { "changeDeviceSelection failed, probably it is shutting down" }
        }
    }

    /** Initiates the bonding process and connects to the device upon success. */
    private fun requestBonding(entry: DeviceListEntry.Ble) {
        Logger.i { "Starting bonding for ${entry.peripheral.address.anonymize}" }
        viewModelScope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                bluetoothRepository.bond(entry.peripheral)
                Logger.i { "Bonding complete for ${entry.peripheral.address.anonymize}, selecting device..." }
                changeDeviceAddress(entry.fullAddress)
            } catch (ex: SecurityException) {
                Logger.w(ex) { "Bonding failed for ${entry.peripheral.address.anonymize} Permissions not granted" }
                serviceRepository.setErrorMessage("Bonding failed: ${ex.message} Permissions not granted")
            } catch (ex: Exception) {
                // Bonding is often flaky and can fail for many reasons (timeout, user cancel, etc)
                Logger.w(ex) { "Bonding failed for ${entry.peripheral.address.anonymize}" }
                serviceRepository.setErrorMessage("Bonding failed: ${ex.message}")
            }
        }
    }

    private fun requestPermission(it: DeviceListEntry.Usb) {
        usbRepository
            .requestPermission(it.driver.device)
            .onEach { granted ->
                if (granted) {
                    Logger.i { "User approved USB access" }
                    changeDeviceAddress(it.fullAddress)
                } else {
                    Logger.e { "USB permission denied for device ${it.address}" }
                }
            }
            .launchIn(viewModelScope)
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

    fun disconnect() {
        changeDeviceAddress(NO_DEVICE_SELECTED)
    }
}

const val NO_DEVICE_SELECTED = "n"
