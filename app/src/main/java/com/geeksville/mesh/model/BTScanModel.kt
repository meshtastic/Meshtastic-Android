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

package com.geeksville.mesh.model

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.repository.datastore.recentaddresses.RecentAddress
import com.geeksville.mesh.repository.datastore.recentaddresses.RecentAddressesRepository
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.repository.network.NetworkRepository.Companion.toAddressString
import com.geeksville.mesh.repository.radio.InterfaceId
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.util.anonymize
import com.hoho.android.usbserial.driver.UsbSerialDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A sealed class is used here to represent the different types of devices that can be displayed in the list. This is
 * more type-safe and idiomatic than using a base class with boolean flags (e.g., isBLE, isUSB). It allows for
 * exhaustive `when` expressions in the code, making it more robust and readable.
 *
 * @param name The display name of the device.
 * @param fullAddress The unique address of the device, prefixed with a type identifier.
 * @param bonded Indicates whether the device is bonded (for BLE) or has permission (for USB).
 */
sealed class DeviceListEntry(open val name: String, open val fullAddress: String, open val bonded: Boolean) {
    val address: String
        get() = fullAddress.substring(1)

    override fun toString(): String =
        "DeviceListEntry(name=${name.anonymize}, addr=${address.anonymize}, bonded=$bonded)"

    @Suppress("MissingPermission")
    data class Ble(val device: BluetoothDevice) :
        DeviceListEntry(
            name = device.name ?: "unnamed-${device.address}",
            fullAddress = "x${device.address}",
            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
        )

    data class Usb(
        private val radioInterfaceService: RadioInterfaceService,
        private val usbManager: UsbManager,
        val driver: UsbSerialDriver,
    ) : DeviceListEntry(
        name = driver.device.deviceName,
        fullAddress = radioInterfaceService.toInterfaceAddress(InterfaceId.SERIAL, driver.device.deviceName),
        bonded = usbManager.hasPermission(driver.device),
    )

    data class Tcp(override val name: String, override val fullAddress: String) :
        DeviceListEntry(name, fullAddress, true)

    data class Disconnect(override val name: String) : DeviceListEntry(name, NO_DEVICE_SELECTED, true)

    data class Mock(override val name: String) : DeviceListEntry(name, "m", true)
}

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
    private val recentAddressesRepository: RecentAddressesRepository,
) : ViewModel(),
    Logging {
    private val context: Context
        get() = application.applicationContext

    val errorText = MutableLiveData<String?>(null)

    val showMockInterface: StateFlow<Boolean>
        get() = MutableStateFlow(radioInterfaceService.isMockInterface()).asStateFlow()

    private val bleDevicesFlow: StateFlow<List<DeviceListEntry.Ble>> =
        bluetoothRepository.state
            .map { ble -> ble.bondedDevices.map { DeviceListEntry.Ble(it) }.sortedBy { it.name } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Flow for discovered TCP devices, using recent addresses for potential name enrichment
    private val processedDiscoveredTcpDevicesFlow: StateFlow<List<DeviceListEntry.Tcp>> =
        combine(networkRepository.resolvedList, recentAddressesRepository.recentAddresses) { tcpServices, recentList ->
            val recentMap = recentList.associateBy({ it.address }, { it.name })
            tcpServices
                .map { service ->
                    val address = "t${service.toAddressString()}"
                    val txtRecords = service.attributes // Map<String, ByteArray?>
                    val shortNameBytes = txtRecords["shortname"]
                    val idBytes = txtRecords["id"]

                    val shortName =
                        shortNameBytes?.let { String(it, Charsets.UTF_8) } ?: context.getString(R.string.meshtastic)
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

    // Flow for recent TCP devices, filtered to exclude any currently discovered devices
    private val filteredRecentTcpDevicesFlow: StateFlow<List<DeviceListEntry.Tcp>> =
        combine(recentAddressesRepository.recentAddresses, processedDiscoveredTcpDevicesFlow) {
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

    private val usbDevicesFlow: StateFlow<List<DeviceListEntry.Usb>> =
        usbRepository.serialDevicesWithDrivers
            .map { usb -> usb.map { (_, d) -> DeviceListEntry.Usb(radioInterfaceService, usbManagerLazy.get(), d) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val disconnectDevice = DeviceListEntry.Disconnect(context.getString(R.string.none))

    val mockDevice = DeviceListEntry.Mock("Demo Mode")

    val bleDevicesForUi: StateFlow<List<DeviceListEntry>> =
        bleDevicesFlow
            .map { devices -> listOf(disconnectDevice) + devices }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
                listOf(disconnectDevice),
            )

    /** UI StateFlow for discovered TCP devices. */
    val discoveredTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        processedDiscoveredTcpDevicesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
            listOf(),
        )

    /** UI StateFlow for recently connected TCP devices that are not currently discovered. */
    val recentTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        filteredRecentTcpDevicesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
            listOf(),
        )

    val usbDevicesForUi: StateFlow<List<DeviceListEntry>> =
        combine(usbDevicesFlow, showMockInterface) { usb, showMock ->
            listOf(disconnectDevice) + usb + if (showMock) listOf(mockDevice) else emptyList()
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
                listOf(disconnectDevice),
            )

    init {
        serviceRepository.statusMessage.onEach { errorText.value = it }.launchIn(viewModelScope)
        debug("BTScanModel created")
    }

    override fun onCleared() {
        super.onCleared()
        debug("BTScanModel cleared")
    }

    fun setErrorText(text: String) {
        errorText.value = text
    }

    private var scanJob: Job? = null

    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    val selectedNotNullFlow: StateFlow<String> =
        selectedAddressFlow
            .map { it ?: NO_DEVICE_SELECTED }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
                selectedAddressFlow.value ?: NO_DEVICE_SELECTED,
            )

    val scanResult = MutableLiveData<MutableMap<String, DeviceListEntry>>(mutableMapOf())

    fun clearScanResults() {
        stopScan()
        scanResult.value = mutableMapOf()
    }

    fun stopScan() {
        if (scanJob != null) {
            debug("stopping scan")
            try {
                scanJob?.cancel()
            } catch (ex: Throwable) {
                warn("Ignoring error stopping scan, probably BT adapter was disabled suddenly: ${ex.message}")
            } finally {
                scanJob = null
            }
        }
        _spinner.value = false
    }

    fun refreshPermissions() {
        // Refresh the Bluetooth state to ensure we have the latest permissions
        bluetoothRepository.refreshState()
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        debug("starting classic scan")

        _spinner.value = true
        scanJob =
            bluetoothRepository
                .scan()
                .onEach { result ->
                    val fullAddress =
                        radioInterfaceService.toInterfaceAddress(InterfaceId.BLUETOOTH, result.device.address)
                    // prevent log spam because we'll get lots of redundant scan results
                    val oldDevs = scanResult.value!!
                    val oldEntry = oldDevs[fullAddress]
                    // Don't spam the GUI with endless updates for non changing nodes
                    if (
                        oldEntry == null || oldEntry.bonded != (result.device.bondState == BluetoothDevice.BOND_BONDED)
                    ) {
                        val entry = DeviceListEntry.Ble(result.device)
                        oldDevs[entry.fullAddress] = entry
                        scanResult.value = oldDevs
                    }
                }
                .catch { ex -> serviceRepository.setErrorMessage("Unexpected Bluetooth scan failure: ${ex.message}") }
                .launchIn(viewModelScope)
    }

    private fun changeDeviceAddress(address: String) {
        try {
            serviceRepository.meshService?.let { service -> MeshService.changeDeviceAddress(context, service, address) }
        } catch (ex: RemoteException) {
            errormsg("changeDeviceSelection failed, probably it is shutting down", ex)
            // ignore the failure and the GUI won't be updating anyways
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestBonding(it: DeviceListEntry) {
        val device = bluetoothRepository.getRemoteDevice(it.address) ?: return
        info("Starting bonding for ${device.anonymize}")

        bluetoothRepository
            .createBond(device)
            .onEach { state ->
                debug("Received bond state changed $state")
                if (state != BluetoothDevice.BOND_BONDING) {
                    debug("Bonding completed, state=$state")
                    if (state == BluetoothDevice.BOND_BONDED) {
                        setErrorText(context.getString(R.string.pairing_completed))
                        changeDeviceAddress("x${device.address}")
                    } else {
                        setErrorText(context.getString(R.string.pairing_failed_try_again))
                    }
                }
            }
            .catch { ex ->
                // We ignore missing BT adapters, because it lets us run on the emulator
                warn("Failed creating Bluetooth bond: ${ex.message}")
            }
            .launchIn(viewModelScope)
    }

    private fun requestPermission(it: DeviceListEntry.Usb) {
        usbRepository
            .requestPermission(it.driver.device)
            .onEach { granted ->
                if (granted) {
                    info("User approved USB access")
                    changeDeviceAddress(it.fullAddress)
                } else {
                    errormsg("USB permission denied for device ${it.address}")
                }
            }
            .launchIn(viewModelScope)
    }

    fun addRecentAddress(address: String, name: String) {
        if (!address.startsWith("t")) return
        viewModelScope.launch { recentAddressesRepository.add(RecentAddress(address, name)) }
    }

    fun removeRecentAddress(address: String) {
        viewModelScope.launch { recentAddressesRepository.remove(address) }
    }

    // Called by the GUI when a new device has been selected by the user
    // @returns true if we were able to change to that item
    fun onSelected(it: DeviceListEntry): Boolean {
        // Using a `when` expression on the sealed class is much cleaner and safer than if/else chains.
        // It ensures that all device types are handled, and the compiler can catch any omissions.
        return when (it) {
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

            is DeviceListEntry.Disconnect,
            is DeviceListEntry.Mock,
            -> {
                changeDeviceAddress(it.fullAddress)
                true
            }
        }
    }

    private val _spinner = MutableStateFlow(false)
    val spinner: StateFlow<Boolean>
        get() = _spinner.asStateFlow()
}

const val NO_DEVICE_SELECTED = "n"
private const val SHARING_STARTED_TIMEOUT_MS = 5000L
