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
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.os.RemoteException
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
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
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class BTScanModel @Inject constructor(
    private val application: Application,
    private val serviceRepository: ServiceRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val usbRepository: UsbRepository,
    private val usbManagerLazy: dagger.Lazy<UsbManager>,
    private val networkRepository: NetworkRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val preferences: SharedPreferences,
) : ViewModel(), Logging {

    private val context: Context get() = application.applicationContext
    val devices = MutableLiveData<MutableMap<String, DeviceListEntry>>(mutableMapOf())
    val errorText = MutableLiveData<String?>(null)

    private val recentIpAddresses = MutableStateFlow(getRecentAddresses())

    private val showMockInterface: StateFlow<Boolean>
        get() =
            MutableStateFlow(radioInterfaceService.isMockInterface()).asStateFlow()

    init {
        combine(
            bluetoothRepository.state,
            networkRepository.resolvedList,
            recentIpAddresses.asStateFlow(),
            usbRepository.serialDevicesWithDrivers,
            showMockInterface,
        ) { ble, tcp, recent, usb, showMockInterface ->
            devices.value = mutableMapOf<String, DeviceListEntry>().apply {
                fun addDevice(entry: DeviceListEntry) {
                    this[entry.fullAddress] = entry
                }

                // Include a placeholder for "None"
                addDevice(
                    DeviceListEntry(
                        context.getString(R.string.none),
                        NO_DEVICE_SELECTED,
                        true
                    )
                )

                if (showMockInterface) {
                    addDevice(DeviceListEntry("Demo Mode", "m", true))
                }

                // Include paired Bluetooth devices
                ble.bondedDevices.map(::BLEDeviceListEntry).sortedBy { it.name }
                    .forEach(::addDevice)

                // Include Network Service Discovery
                tcp.forEach { service ->
                    val address = service.toAddressString()
                    val txtRecords = service.attributes // Map<String, ByteArray?>
                    val shortNameBytes = txtRecords["shortname"]
                    val idBytes = txtRecords["id"]

                    val shortName = shortNameBytes?.let { String(it, Charsets.UTF_8) }
                        ?: context.getString(R.string.meshtastic)
                    val deviceId =
                        idBytes?.let { String(it, Charsets.UTF_8) }?.replace("!", "")
                    var displayName = shortName
                    if (deviceId != null) {
                        displayName += "_$deviceId"
                    }
                    addDevice(DeviceListEntry(displayName, "t$address", true))
                }

                // Include saved IP connections
                recent.forEach { (address, name) ->
                    addDevice(DeviceListEntry(name, address, true))
                }

                usb.forEach { (_, d) ->
                    addDevice(USBDeviceListEntry(radioInterfaceService, usbManagerLazy.get(), d))
                }
            }
        }.launchIn(viewModelScope)

        serviceRepository.statusMessage
            .onEach { errorText.value = it }
            .launchIn(viewModelScope)

        debug("BTScanModel created")
    }

    /**
     * @param fullAddress Interface [prefix] + [address] (example: "x7C:9E:BD:F0:BE:BE")
     */
    open class DeviceListEntry(val name: String, val fullAddress: String, val bonded: Boolean) {
        val prefix get() = fullAddress[0]
        val address get() = fullAddress.substring(1)

        override fun toString(): String {
            return "DeviceListEntry(name=${name.anonymize}, addr=${address.anonymize}, bonded=$bonded)"
        }

        val isBLE: Boolean get() = prefix == 'x'
        val isUSB: Boolean get() = prefix == 's'
        val isTCP: Boolean get() = prefix == 't'

        val isMock: Boolean get() = prefix == 'm'
        val isDisconnect: Boolean get() = prefix == 'n'
    }

    @SuppressLint("MissingPermission")
    class BLEDeviceListEntry(device: BluetoothDevice) : DeviceListEntry(
        device.name ?: "unnamed-${device.address}", // some devices might not have a name
        "x${device.address}",
        device.bondState == BluetoothDevice.BOND_BONDED
    )

    class USBDeviceListEntry(
        radioInterfaceService: RadioInterfaceService,
        usbManager: UsbManager,
        val usb: UsbSerialDriver,
    ) : DeviceListEntry(
        usb.device.deviceName,
        radioInterfaceService.toInterfaceAddress(InterfaceId.SERIAL, usb.device.deviceName),
        usbManager.hasPermission(usb.device),
    )

    override fun onCleared() {
        super.onCleared()
        debug("BTScanModel cleared")
    }

    fun setErrorText(text: String) {
        errorText.value = text
    }

    private var scanJob: Job? = null

    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    val selectedNotNullFlow: StateFlow<String> = selectedAddressFlow
        .map { it ?: NO_DEVICE_SELECTED }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SHARING_STARTED_TIMEOUT_MS),
            selectedAddressFlow.value ?: NO_DEVICE_SELECTED
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

    @SuppressLint("MissingPermission")
    fun startScan() {
        debug("starting classic scan")

        _spinner.value = true
        scanJob = bluetoothRepository.scan()
            .onEach { result ->
                val fullAddress = radioInterfaceService.toInterfaceAddress(
                    InterfaceId.BLUETOOTH,
                    result.device.address
                )
                // prevent log spam because we'll get lots of redundant scan results
                val isBonded = result.device.bondState == BluetoothDevice.BOND_BONDED
                val oldDevs = scanResult.value!!
                val oldEntry = oldDevs[fullAddress]
                // Don't spam the GUI with endless updates for non changing nodes
                if (oldEntry == null || oldEntry.bonded != isBonded) {
                    val entry = DeviceListEntry(result.device.name, fullAddress, isBonded)
                    oldDevs[entry.fullAddress] = entry
                    scanResult.value = oldDevs
                }
            }.catch { ex ->
                serviceRepository.setErrorMessage("Unexpected Bluetooth scan failure: ${ex.message}")
            }.launchIn(viewModelScope)
    }

    private fun changeDeviceAddress(address: String) {
        try {
            serviceRepository.meshService?.let { service ->
                MeshService.changeDeviceAddress(context, service, address)
            }
            devices.value = devices.value // Force a GUI update
        } catch (ex: RemoteException) {
            errormsg("changeDeviceSelection failed, probably it is shutting down", ex)
            // ignore the failure and the GUI won't be updating anyways
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestBonding(it: DeviceListEntry) {
        val device = bluetoothRepository.getRemoteDevice(it.address) ?: return
        info("Starting bonding for ${device.anonymize}")

        bluetoothRepository.createBond(device)
            .onEach { state ->
                debug("Received bond state changed $state")
                if (state != BluetoothDevice.BOND_BONDING) {
                    debug("Bonding completed, state=$state")
                    if (state == BluetoothDevice.BOND_BONDED) {
                        setErrorText(context.getString(R.string.pairing_completed))
                        changeDeviceAddress(it.fullAddress)
                    } else {
                        setErrorText(context.getString(R.string.pairing_failed_try_again))
                    }
                }
            }.catch { ex ->
                // We ignore missing BT adapters, because it lets us run on the emulator
                warn("Failed creating Bluetooth bond: ${ex.message}")
            }.launchIn(viewModelScope)
    }

    private fun requestPermission(it: USBDeviceListEntry) {
        usbRepository.requestPermission(it.usb.device)
            .onEach { granted ->
                if (granted) {
                    info("User approved USB access")
                    changeDeviceAddress(it.fullAddress)
                } else {
                    errormsg("USB permission denied for device ${it.address}")
                }
            }.launchIn(viewModelScope)
    }

    private fun getRecentAddresses(): List<Pair<String, String>> {
        val jsonAddresses = preferences.getString("recent-ip-addresses", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonAddresses)
        var needsMigration = false

        val listAddresses = (0 until jsonArray.length()).mapNotNull { i ->
            when (val item = jsonArray.get(i)) {
                is JSONObject -> {
                    // Modern format: JSONObject with address and name
                    item.getString("address") to item.getString("name")
                }

                is String -> {
                    // Old format: just the address string
                    needsMigration = true
                    item to context.getString(R.string.meshtastic) // [3]
                }

                else -> {
                    // Unknown format, log or handle as an error if necessary
                    warn("Unknown item type in recent IP addresses: $item")
                    null
                }
            }
        }

        // If migration was needed for any item, rewrite the entire list in the new format
        if (needsMigration) {
            setRecentAddresses(listAddresses)
        }
        return listAddresses
    }

    private fun setRecentAddresses(addresses: List<Pair<String, String>>) {
        val jsonArray = JSONArray()
        addresses.forEach { (address, name) ->
            val obj = JSONObject()
            obj.put("address", address)
            obj.put("name", name)
            jsonArray.put(obj)
        }
        preferences.edit {
            putString("recent-ip-addresses", jsonArray.toString())
        }
        recentIpAddresses.value = addresses
    }

    // Remove 'name' parameter from addRecentAddress and related logic
    fun addRecentAddress(address: String, overrideName: String? = null) {
        if (!address.startsWith("t")) return
        val existingItems = getRecentAddresses()
        val updatedList = mutableListOf<Pair<String, String>>()
        val displayName = overrideName ?: context.getString(R.string.meshtastic)
        updatedList.add(address to displayName)
        updatedList.addAll(existingItems.filter { it.first != address }.take(2))
        setRecentAddresses(updatedList)
    }

    fun removeRecentAddress(address: String) {
        val existingItems = getRecentAddresses()
        val updatedList = existingItems.filter { it.first != address }
        setRecentAddresses(updatedList)
    }

    // Called by the GUI when a new device has been selected by the user
    // @returns true if we were able to change to that item
    fun onSelected(it: DeviceListEntry): Boolean {
        // If the device is paired, let user select it, otherwise start the pairing flow
        if (it.bonded) {
            addRecentAddress(it.fullAddress, connectedNodeLongName)
            changeDeviceAddress(it.fullAddress)
            return true
        } else {
            // Handle requesting USB or bluetooth permissions for the device
            debug("Requesting permissions for the device")

            if (it.isBLE) {
                requestBonding(it)
            }

            if (it.isUSB) {
                requestPermission(it as USBDeviceListEntry)
            }

            return false
        }
    }

    private val _spinner = MutableStateFlow(false)
    val spinner: StateFlow<Boolean> get() = _spinner.asStateFlow()

    // Add a new property to hold the connected node's long name
    var connectedNodeLongName: String? = null
}

const val NO_DEVICE_SELECTED = "n"
private const val SHARING_STARTED_TIMEOUT_MS = 5000L
