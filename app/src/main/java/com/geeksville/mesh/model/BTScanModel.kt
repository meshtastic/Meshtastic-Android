package com.geeksville.mesh.model

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.*
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.nsd.NsdServiceInfo
import android.os.RemoteException
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.android.*
import com.geeksville.mesh.repository.bluetooth.BluetoothRepository
import com.geeksville.mesh.repository.nsd.NsdRepository
import com.geeksville.mesh.repository.radio.MockInterface
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.radio.SerialInterface
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.ui.SLogging
import com.geeksville.mesh.ui.changeDeviceSelection
import com.geeksville.mesh.util.anonymize
import com.geeksville.mesh.util.exceptionReporter
import com.hoho.android.usbserial.driver.UsbSerialDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.regex.Pattern
import javax.inject.Inject

/// Show the UI asking the user to bond with a device, call changeSelection() if/when bonding completes
@SuppressLint("MissingPermission")
private fun requestBonding(
    activity: MainActivity,
    device: BluetoothDevice,
    onComplete: (Int) -> Unit
) {
    SLogging.info("Starting bonding for ${device.anonymize}")

    // We need this receiver to get informed when the bond attempt finished
    val bondChangedReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context,
            intent: Intent
        ) = exceptionReporter {
            val state =
                intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            SLogging.debug("Received bond state changed $state")

            if (state != BluetoothDevice.BOND_BONDING) {
                context.unregisterReceiver(this) // we stay registered until bonding completes (either with BONDED or NONE)
                SLogging.debug("Bonding completed, state=$state")
                onComplete(state)
            }
        }
    }

    val filter = IntentFilter()
    filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    activity.registerReceiver(bondChangedReceiver, filter)

    // We ignore missing BT adapters, because it lets us run on the emulator
    try {
        device.createBond()
    } catch (ex: Throwable) {
        SLogging.warn("Failed creating Bluetooth bond: ${ex.message}")
    }
}

@HiltViewModel
class BTScanModel @Inject constructor(
    private val application: Application,
    private val bluetoothRepository: BluetoothRepository,
    private val usbRepository: UsbRepository,
    private val nsdRepository: NsdRepository,
    private val radioInterfaceService: RadioInterfaceService,
) : ViewModel(), Logging {

    private val context: Context get() = application.applicationContext

    init {
        debug("BTScanModel created")
    }

    /** *fullAddress* = interface prefix + address (example: "x7C:9E:BD:F0:BE:BE") */
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

    class USBDeviceListEntry(usbManager: UsbManager, val usb: UsbSerialDriver) : DeviceListEntry(
        usb.device.deviceName,
        SerialInterface.toInterfaceName(usb.device.deviceName),
        SerialInterface.assumePermission || usbManager.hasPermission(usb.device)
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

    private val deviceManager get() = context.deviceManager
    val hasCompanionDeviceApi get() = application.hasCompanionDeviceApi()
    val hasBluetoothPermission get() = application.hasBluetoothPermission()
    private val usbManager get() = context.usbManager

    var selectedAddress: String? = null
    val errorText = object : MutableLiveData<String?>(null) {}

    private var scanner: BluetoothLeScanner? = null

    val selectedBluetooth: Boolean get() = selectedAddress?.get(0) == 'x'

    /// Use the string for the NopInterface
    val selectedNotNull: String get() = selectedAddress ?: "n"

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            val msg = "Unexpected bluetooth scan failure: $errorCode"
            errormsg(msg)
            // error code2 seems to be indicate hung bluetooth stack
            errorText.value = msg
        }

        // For each device that appears in our scan, ask for its GATT, when the gatt arrives,
        // check if it is an eligible device and store it in our list of candidates
        // if that device later disconnects remove it as a candidate
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            if (result.device.name.let { it != null && it.matches(Regex(BLE_NAME_PATTERN)) }) {
                val addr = result.device.address
                val fullAddr = "x$addr" // full address with the bluetooth prefix added
                // prevent log spam because we'll get lots of redundant scan results
                val isBonded = result.device.bondState == BluetoothDevice.BOND_BONDED
                val oldDevs = devices.value!!
                val oldEntry = oldDevs[fullAddr]
                if (oldEntry == null || oldEntry.bonded != isBonded) { // Don't spam the GUI with endless updates for non changing nodes
                    val entry = DeviceListEntry(
                        result.device.name
                            ?: "unnamed-$addr", // autobug: some devices might not have a name, if someone is running really old device code?
                        fullAddr,
                        isBonded
                    )
                    // If nothing was selected, by default select the first valid thing we see
                    val activity: MainActivity? = try {
                        GeeksvilleApplication.currentActivity as MainActivity? // Can be null if app is shutting down
                    } catch (_: ClassCastException) {
                        // Buggy "Z812" phones apparently have the wrong class type for this
                        errormsg("Unexpected class for main activity")
                        null
                    }

                    if (selectedAddress == null && entry.bonded && activity != null)
                        changeScanSelection(activity, fullAddr)
                    addDevice(entry) // Add/replace entry
                }
            }
        }
    }

    private fun addDevice(entry: DeviceListEntry) {
        val oldDevs = devices.value!!
        oldDevs[entry.fullAddress] = entry // Add/replace entry
        devices.value = oldDevs // trigger gui updates
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        // Stop Network Service Discovery (for TCP)
        networkDiscovery?.cancel()

        if (scanner != null) {
            debug("stopping scan")
            try {
                scanner?.stopScan(scanCallback)
            } catch (ex: Throwable) {
                warn("Ignoring error stopping scan, probably BT adapter was disabled suddenly: ${ex.message}")
            } finally {
                scanner = null
                _spinner.value = false
            }
        } else _spinner.value = false
    }

    /**
     * returns true if we could start scanning, false otherwise
     */
    fun setupScan(): Boolean {
        selectedAddress = radioInterfaceService.getDeviceAddress()

        return if (MockInterface.addressValid(context, usbRepository, "")) {
            warn("Running under emulator/test lab")

            val testnodes = listOf(
                DeviceListEntry("Included simulator", "m", true),
                DeviceListEntry("Complete simulator", "t10.0.2.2", true),
                DeviceListEntry(context.getString(R.string.none), "n", true)
                /* Don't populate fake bluetooth devices, because we don't want testlab inside of google
                to try and use them.

                DeviceListEntry("Meshtastic_ab12", "xaa", false),
                DeviceListEntry("Meshtastic_32ac", "xbb", true) */
            )

            devices.value = (testnodes.map { it.fullAddress to it }).toMap().toMutableMap()

            // If nothing was selected, by default select the first thing we see
            val activity = GeeksvilleApplication.currentActivity
            if (selectedAddress == null && activity is MainActivity)
                changeScanSelection(
                    activity,
                    testnodes.first().fullAddress
                )

            true
        } else {
            if (scanner == null) {
                // Clear the old device list
                devices.value?.clear()

                // Include a placeholder for "None"
                addDevice(DeviceListEntry(context.getString(R.string.none), "n", true))

                // Include paired Bluetooth devices
                addBluetoothDevices()

                // Include Network Service Discovery
                nsdRepository.resolvedList?.forEach { service ->
                    addDevice(TCPDeviceListEntry(service))
                }

                val serialDevices by lazy { usbRepository.serialDevicesWithDrivers.value }
                serialDevices.forEach { (_, d) ->
                    addDevice(USBDeviceListEntry(usbManager, d))
                }
            } else {
                debug("scan already running")
            }
            true
        }
    }

    private var networkDiscovery: Job? = null
    fun startScan() {
        _spinner.value = true

        // Start Network Service Discovery (find TCP devices)
        networkDiscovery = nsdRepository.networkDiscoveryFlow()
            .onEach { addDevice(TCPDeviceListEntry(it)) }
            .launchIn(CoroutineScope(Dispatchers.Main))

        if (hasBluetoothPermission) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && hasCompanionDeviceApi)
                startCompanionScan() else startClassicScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startClassicScan() {
        /// The following call might return null if the user doesn't have bluetooth access permissions
        val bluetoothLeScanner = bluetoothRepository.getBluetoothLeScanner()

        if (bluetoothLeScanner != null) { // could be null if bluetooth is disabled
            debug("starting classic scan")

            // filter and only accept devices that have our service
            val filter =
                ScanFilter.Builder()
                    // Samsung doesn't seem to filter properly by service so this can't work
                    // see https://stackoverflow.com/questions/57981986/altbeacon-android-beacon-library-not-working-after-device-has-screen-off-for-a-s/57995960#57995960
                    // and https://stackoverflow.com/a/45590493
                    // .setServiceUuid(ParcelUuid(BluetoothInterface.BTM_SERVICE_UUID))
                    .build()

            val settings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
            bluetoothLeScanner.startScan(listOf(filter), settings, scanCallback)
            scanner = bluetoothLeScanner
        }
    }

    /**
     * @return DeviceListEntry from full Address (prefix + address).
     * If Bluetooth is enabled and BLE Address is valid, get remote device information.
     */
    @SuppressLint("MissingPermission")
    fun getDeviceListEntry(fullAddress: String, bonded: Boolean = false): DeviceListEntry {
        val address = fullAddress.substring(1)
        val device = bluetoothRepository.getRemoteDevice(address)
        return if (device != null && device.name != null) {
            DeviceListEntry(device.name, fullAddress, device.bondState != BluetoothDevice.BOND_NONE)
        } else {
            DeviceListEntry(address, fullAddress, bonded)
        }
    }

    @SuppressLint("MissingPermission")
    private fun addBluetoothDevices() {
        bluetoothRepository.getBondedDevices()
            ?.filter { it.name != null && it.name.matches(Regex(BLE_NAME_PATTERN)) }
            ?.forEach {
                addDevice(DeviceListEntry(it.name, "x${it.address}", true))
            }
    }

    private val _spinner = MutableLiveData(false)
    val spinner: LiveData<Boolean> get() = _spinner

    private val _associationRequest = MutableLiveData<IntentSenderRequest?>(null)
    val associationRequest: LiveData<IntentSenderRequest?> get() = _associationRequest

    /**
     * Called immediately after fragment observes CompanionDeviceManager activity result
     */
    fun clearAssociationRequest() {
        _associationRequest.value = null
    }

    @SuppressLint("NewApi")
    private fun associationRequest(): AssociationRequest {
        // To skip filtering based on name and supported feature flags (UUIDs),
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        // We only look for Mesh (rather than the full name) because NRF52 uses a very short name
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile(BLE_NAME_PATTERN))
            // .addServiceUuid(ParcelUuid(BluetoothInterface.BTM_SERVICE_UUID), null)
            .build()

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        return AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(false)
            .build()
    }

    @SuppressLint("NewApi")
    private fun startCompanionScan() {
        debug("starting companion scan")
        deviceManager?.associate(
            associationRequest(),
            @SuppressLint("NewApi")
            object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    debug("CompanionDeviceManager - device found")
                    _spinner.value = false
                    chooserLauncher.let {
                        val request: IntentSenderRequest = IntentSenderRequest.Builder(it).build()
                        _associationRequest.value = request
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    warn("BLE selection service failed $error")
                }
            }, null
        )
    }

    val devices = object : MutableLiveData<MutableMap<String, DeviceListEntry>>(mutableMapOf()) {

        /**
         * Called when the number of active observers change from 1 to 0.
         *
         *
         * This does not mean that there are no observers left, there may still be observers but their
         * lifecycle states aren't [Lifecycle.State.STARTED] or [Lifecycle.State.RESUMED]
         * (like an Activity in the back stack).
         *
         *
         * You can check if there are observers via [.hasObservers].
         */
        override fun onInactive() {
            super.onInactive()
            stopScan()
        }
    }

    /// Called by the GUI when a new device has been selected by the user
    /// Returns true if we were able to change to that item
    fun onSelected(activity: MainActivity, it: DeviceListEntry): Boolean {
        // If the device is paired, let user select it, otherwise start the pairing flow
        if (it.bonded) {
            changeScanSelection(activity, it.fullAddress)
            return true
        } else {
            // Handle requesting USB or bluetooth permissions for the device
            debug("Requesting permissions for the device")

            exceptionReporter {
                if (it.isBLE) {
                    // Request bonding for bluetooth
                    // We ignore missing BT adapters, because it lets us run on the emulator
                    bluetoothRepository
                        .getRemoteDevice(it.address)?.let { device ->
                            requestBonding(activity, device) { state ->
                                if (state == BluetoothDevice.BOND_BONDED) {
                                    errorText.value = activity.getString(R.string.pairing_completed)
                                    changeScanSelection(activity, it.fullAddress)
                                } else {
                                    errorText.value =
                                        activity.getString(R.string.pairing_failed_try_again)
                                }

                                // Force the GUI to redraw
                                devices.value = devices.value
                            }
                        }
                }
            }

            if (it.isUSB) {
                it as USBDeviceListEntry

                val usbReceiver = object : BroadcastReceiver() {

                    override fun onReceive(context: Context, intent: Intent) {
                        if (ACTION_USB_PERMISSION == intent.action) {

                            val device: UsbDevice =
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)!!

                            if (intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED,
                                    false
                                )
                            ) {
                                info("User approved USB access")
                                changeScanSelection(activity, it.fullAddress)

                                // Force the GUI to redraw
                                devices.value = devices.value
                            } else {
                                errormsg("USB permission denied for device $device")
                            }
                        }
                        // We don't need to stay registered
                        activity.unregisterReceiver(this)
                    }
                }

                val permissionIntent =
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                    PendingIntent.getBroadcast(activity, 0, Intent(ACTION_USB_PERMISSION), 0)
                } else {
                    PendingIntent.getBroadcast(activity, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
                }
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                activity.registerReceiver(usbReceiver, filter)
                usbManager.requestPermission(it.usb.device, permissionIntent)
            }

            return false
        }
    }

    /// Change to a new macaddr selection, updating GUI and radio
    fun changeScanSelection(context: MainActivity, newAddr: String) {
        try {
            info("Changing device to ${newAddr.anonymize}")
            changeDeviceSelection(context, newAddr)
            selectedAddress =
                newAddr // do this after changeDeviceSelection, so if it throws the change will be discarded
            devices.value = devices.value // Force a GUI update
        } catch (ex: RemoteException) {
            errormsg("Failed talking to service, probably it is shutting down $ex.message")
            // ignore the failure and the GUI won't be updating anyways
        }
    }
    companion object {
        const val BLE_NAME_PATTERN = "^\\S+\$"
        const val ACTION_USB_PERMISSION = "com.geeksville.mesh.USB_PERMISSION"
    }
}