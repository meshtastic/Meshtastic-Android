package com.geeksville.mesh.model

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.hardware.usb.UsbManager
import android.net.nsd.NsdServiceInfo
import android.os.RemoteException
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.repository.network.NetworkRepository
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class BTScanModel @Inject constructor(
    private val application: Application,
    private val serviceRepository: ServiceRepository,
    private val bluetoothRepository: BluetoothRepository,
    private val usbRepository: UsbRepository,
    private val usbManagerLazy: dagger.Lazy<UsbManager>,
    private val networkRepository: NetworkRepository,
    private val radioInterfaceService: RadioInterfaceService,
) : ViewModel(), Logging {

    private val context: Context get() = application.applicationContext
    val devices = MutableLiveData<MutableMap<String, DeviceListEntry>>(mutableMapOf())
    val errorText = MutableLiveData<String?>(null)

    private val showMockInterface = MutableStateFlow(radioInterfaceService.isMockInterface)

    fun showMockInterface() {
        showMockInterface.value = true
    }

    init {
        combine(
            bluetoothRepository.state,
            networkRepository.resolvedList,
            usbRepository.serialDevicesWithDrivers,
            showMockInterface,
        ) { ble, tcp, usb, showMockInterface ->
            devices.value = mutableMapOf<String, DeviceListEntry>().apply {
                fun addDevice(entry: DeviceListEntry) { this[entry.fullAddress] = entry }

                // Include a placeholder for "None"
                addDevice(DeviceListEntry(context.getString(R.string.none), "n", true))

                if (showMockInterface) {
                    addDevice(DeviceListEntry("Demo Mode", "m", true))
                }

                // Include paired Bluetooth devices
                ble.bondedDevices.forEach {
                    addDevice(BLEDeviceListEntry(it))
                }

                // Include Network Service Discovery
                tcp.forEach { service ->
                    addDevice(TCPDeviceListEntry(service))
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

    class TCPDeviceListEntry(val service: NsdServiceInfo) : DeviceListEntry(
        service.host.toString().substring(1),
        service.host.toString().replace("/", "t"),
        true
    )

    override fun onCleared() {
        super.onCleared()
        debug("BTScanModel cleared")
    }

    fun setErrorText(text: String) {
        errorText.value = text
    }

    private var scanJob: Job? = null

    val selectedAddress get() = radioInterfaceService.getDeviceAddress()
    val selectedBluetooth: Boolean get() = selectedAddress?.getOrNull(0) == 'x'

    /// Use the string for the NopInterface
    val selectedNotNull: String get() = selectedAddress ?: "n"

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

    // Called by the GUI when a new device has been selected by the user
    // @returns true if we were able to change to that item
    fun onSelected(it: DeviceListEntry): Boolean {
        // If the device is paired, let user select it, otherwise start the pairing flow
        if (it.bonded) {
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

    private val _spinner = MutableLiveData(false)
    val spinner: LiveData<Boolean> get() = _spinner
}
