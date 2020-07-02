package com.geeksville.mesh.ui

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.ParcelUuid
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.concurrent.handledLaunch
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.BluetoothInterface
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.RadioInterfaceService
import com.geeksville.mesh.service.SerialInterface
import com.geeksville.util.Exceptions
import com.geeksville.util.anonymize
import com.geeksville.util.exceptionReporter
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.android.synthetic.main.settings_fragment.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.regex.Pattern

object SLogging : Logging {}

/// Change to a new macaddr selection, updating GUI and radio
fun changeDeviceSelection(context: MainActivity, newAddr: String?) {
    // FIXME, this is a kinda yucky way to find the service
    context.model.meshService?.let { service ->
        MeshService.changeDeviceAddress(context, service, newAddr)
    }
}

/// Show the UI asking the user to bond with a device, call changeSelection() if/when bonding completes
private fun requestBonding(
    activity: MainActivity,
    device: BluetoothDevice,
    onComplete: (Int) -> Unit
) {
    SLogging.info("Starting bonding for $device")

    // We need this receiver to get informed when the bond attempt finished
    val bondChangedReceiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context,
            intent: Intent
        ) = exceptionReporter {
            val state =
                intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    -1
                )
            SLogging.debug("Received bond state changed $state")

            if (state != BOND_BONDING) {
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
    device.createBond()
}


class BTScanModel(app: Application) : AndroidViewModel(app), Logging {

    private val context = getApplication<Application>().applicationContext

    init {
        debug("BTScanModel created")
    }

    open class DeviceListEntry(val name: String, val address: String, val bonded: Boolean) {
        val bluetoothAddress
            get() =
                if (address[0] == 'x')
                    address.substring(1)
                else
                    null


        override fun toString(): String {
            return "DeviceListEntry(name=${name.anonymize}, addr=${address.anonymize})"
        }

        val isBluetooth: Boolean get() = address[0] == 'x'
        val isSerial: Boolean get() = address[0] == 's'
    }

    class USBDeviceListEntry(usbManager: UsbManager, val usb: UsbSerialDriver) : DeviceListEntry(
        usb.device.deviceName,
        SerialInterface.toInterfaceName(usb.device.deviceName),
        SerialInterface.assumePermission || usbManager.hasPermission(usb.device)
    )

    override fun onCleared() {
        super.onCleared()
        debug("BTScanModel cleared")
    }

    /// Note: may be null on platforms without a bluetooth driver (ie. the emulator)
    val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    var selectedAddress: String? = null
    val errorText = object : MutableLiveData<String?>(null) {}

    private var scanner: BluetoothLeScanner? = null

    /// If this address is for a bluetooth device, return the macaddr portion, else null
    val selectedBluetooth: String?
        get() = selectedAddress?.let { a ->
            if (a[0] == 'x')
                a.substring(1)
            else
                null
        }

    /// If this address is for a USB device, return the macaddr portion, else null
    val selectedUSB: String?
        get() = selectedAddress?.let { a ->
            if (a[0] == 's')
                a.substring(1)
            else
                null
        }

    /// Use the string for the NopInterface
    val selectedNotNull: String get() = selectedAddress ?: "n"

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            val msg = "Unexpected bluetooth scan failure: $errorCode"
            errormsg(msg)
            // error code2 seeems to be indicate hung bluetooth stack
            errorText.value = msg
        }

        // For each device that appears in our scan, ask for its GATT, when the gatt arrives,
        // check if it is an eligable device and store it in our list of candidates
        // if that device later disconnects remove it as a candidate
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val addr = result.device.address
            val fullAddr = "x$addr" // full address with the bluetooh prefix
            // prevent logspam because weill get get lots of redundant scan results
            val isBonded = result.device.bondState == BluetoothDevice.BOND_BONDED
            val oldDevs = devices.value!!
            val oldEntry = oldDevs[fullAddr]
            if (oldEntry == null || oldEntry.bonded != isBonded) { // Don't spam the GUI with endless updates for non changing nodes

                val skipBogus = try {
                    // Note Soyes XS has a buggy BLE scan implementation and returns devices we didn't ask for.  So we check to see if the
                    // last two chars of the name matches the last two of the address - if not we skip it

                    // Note: the address is always two more than the hex string we show in the name

                    // nasty parsing of a string that ends with ab:45 as four hex digits
                    val lastAddr = (addr.substring(
                        addr.length - 5,
                        addr.length - 3
                    ) + addr.substring(addr.length - 2)).toInt(16)

                    val lastName =
                        result.device.name.substring(result.device.name.length - 4).toInt(16)

                    (lastAddr - 2) != lastName
                } catch (ex: Throwable) {
                    false // If we fail parsing anything, don't do this nasty hack
                }

                if (skipBogus)
                    errormsg("Skipping bogus BLE entry $result")
                else {
                    val entry = DeviceListEntry(
                        result.device.name
                            ?: "unnamed-$addr", // autobug: some devices might not have a name, if someone is running really old device code?
                        fullAddr,
                        isBonded
                    )
                    debug("onScanResult ${entry}")

                    // If nothing was selected, by default select the first valid thing we see
                    val activity =
                        GeeksvilleApplication.currentActivity as MainActivity? // Can be null if app is shutting down
                    if (selectedAddress == null && entry.bonded && activity != null)
                        changeScanSelection(
                            activity,
                            fullAddr
                        )
                    addDevice(entry) // Add/replace entry
                }
            }
        }
    }

    private fun addDevice(entry: DeviceListEntry) {
        val oldDevs = devices.value!!
        oldDevs[entry.address] = entry // Add/replace entry
        devices.value = oldDevs // trigger gui updates
    }

    fun stopScan() {
        if (scanner != null) {
            debug("stopping scan")
            try {
                scanner?.stopScan(scanCallback)
            } catch (ex: Throwable) {
                warn("Ignoring error stopping scan, probably BT adapter was disabled suddenly: ${ex.message}")
            }
            scanner = null
        }
    }

    /**
     * returns true if we could start scanning, false otherwise
     */
    fun startScan(): Boolean {
        debug("BTScan component active")
        selectedAddress = RadioInterfaceService.getDeviceAddress(context)

        return if (bluetoothAdapter == null) {
            warn("No bluetooth adapter.  Running under emulation?")

            val testnodes = listOf(
                DeviceListEntry("Meshtastic_ab12", "xaa", false),
                DeviceListEntry("Meshtastic_32ac", "xbb", true)
            )

            devices.value = (testnodes.map { it.address to it }).toMap().toMutableMap()

            // If nothing was selected, by default select the first thing we see
            if (selectedAddress == null)
                changeScanSelection(
                    GeeksvilleApplication.currentActivity as MainActivity,
                    testnodes.first().address
                )

            true
        } else {
            /// The following call might return null if the user doesn't have bluetooth access permissions
            val s: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

            val usbDrivers = SerialInterface.findDrivers(context)

            /* model.bluetoothEnabled.value */

            if (s == null && usbDrivers.isEmpty()) {
                errorText.value =
                    context.getString(R.string.requires_bluetooth)

                false
            } else {
                if (scanner == null) {

                    // Clear the old device list
                    devices.value?.clear()

                    // Include a placeholder for "None"
                    addDevice(DeviceListEntry(context.getString(R.string.none), "n", true))

                    usbDrivers.forEach { d ->
                        addDevice(
                            USBDeviceListEntry(usbManager, d)
                        )
                    }

                    if (s != null) { // could be null if bluetooth is disabled
                        debug("starting scan")

                        // filter and only accept devices that have our service
                        val filter =
                            ScanFilter.Builder()
                                .setServiceUuid(ParcelUuid(BluetoothInterface.BTM_SERVICE_UUID))
                                .build()

                        val settings =
                            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                                .build()
                        s.startScan(listOf(filter), settings, scanCallback)
                        scanner = s
                    }
                } else {
                    debug("scan already running")
                }

                true
            }
        }
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
            changeScanSelection(activity, it.address)
            return true
        } else {
            // Handle requestng USB or bluetooth permissions for the device
            debug("Requesting permissions for the device")

            if (it.isBluetooth) {
                // Request bonding for bluetooth
                // We ignore missing BT adapters, because it lets us run on the emulator
                bluetoothAdapter
                    ?.getRemoteDevice(it.bluetoothAddress)?.let { device ->
                        requestBonding(activity, device) { state ->
                            if (state == BOND_BONDED) {
                                errorText.value = activity.getString(R.string.pairing_completed)
                                changeScanSelection(
                                    activity,
                                    it.address
                                )
                            } else {
                                errorText.value =
                                    activity.getString(R.string.pairing_failed_try_again)
                            }

                            // Force the GUI to redraw
                            devices.value = devices.value
                        }
                    }
            }

            if (it.isSerial) {
                it as USBDeviceListEntry

                val ACTION_USB_PERMISSION = "com.geeksville.mesh.USB_PERMISSION"

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
                                changeScanSelection(activity, it.address)

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
                    PendingIntent.getBroadcast(activity, 0, Intent(ACTION_USB_PERMISSION), 0)
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
}


@SuppressLint("NewApi")
class SettingsFragment : ScreenFragment("Settings"), Logging {

    private val scanModel: BTScanModel by activityViewModels()
    private val model: UIViewModel by activityViewModels()

    // FIXME - move this into a standard GUI helper class
    private val guiJob = Job()
    private val mainScope = CoroutineScope(Dispatchers.Main + guiJob)


    private val hasCompanionDeviceApi: Boolean by lazy {
        BluetoothInterface.hasCompanionDeviceApi(requireContext())
    }

    private val deviceManager: CompanionDeviceManager by lazy {
        requireContext().getSystemService(CompanionDeviceManager::class.java)
    }

    override fun onDestroy() {
        guiJob.cancel()
        super.onDestroy()
    }

    private fun doFirmwareUpdate() {
        model.meshService?.let { service ->

            mainScope.handledLaunch {
                debug("User started firmware update")
                updateFirmwareButton.isEnabled = false // Disable until things complete
                updateProgressBar.visibility = View.VISIBLE
                updateProgressBar.progress = 0 // start from scratch

                scanStatusText.text = "Updating firmware, wait up to eight minutes..."
                service.startFirmwareUpdate()
                while (service.updateStatus >= 0) {
                    updateProgressBar.progress = service.updateStatus
                    delay(2000) // Only check occasionally
                }
                scanStatusText.text =
                    if (service.updateStatus == -1) "Update successful" else "Update failed"
                updateProgressBar.isEnabled = false
                updateFirmwareButton.isEnabled = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_fragment, container, false)
    }

    private fun initNodeInfo() {
        val connected = model.isConnected.value

        // If actively connected possibly let the user update firmware
        val info = model.myNodeInfo.value
        if (connected == MeshService.ConnectionState.CONNECTED && info != null && info.shouldUpdate && info.couldUpdate) {
            updateFirmwareButton.visibility = View.VISIBLE
            updateFirmwareButton.text =
                getString(R.string.update_to).format(getString(R.string.cur_firmware_version))
        } else {
            updateFirmwareButton.visibility = View.GONE
            updateProgressBar.visibility = View.GONE
        }

        when (connected) {
            MeshService.ConnectionState.CONNECTED -> {
                val fwStr = info?.firmwareString ?: ""
                scanStatusText.text = getString(R.string.connected_to).format(fwStr)
            }
            MeshService.ConnectionState.DISCONNECTED ->
                scanStatusText.text = getString(R.string.not_connected)
            MeshService.ConnectionState.DEVICE_SLEEP ->
                scanStatusText.text = getString(R.string.connected_sleeping)
        }
    }

    /// Setup the ui widgets unrelated to BLE scanning
    private fun initCommonUI() {
        model.ownerName.observe(viewLifecycleOwner, Observer { name ->
            usernameEditText.setText(name)
        })

        // Only let user edit their name or set software update while connected to a radio
        model.isConnected.observe(viewLifecycleOwner, Observer { connected ->
            usernameView.isEnabled = connected == MeshService.ConnectionState.CONNECTED
            initNodeInfo()
        })

        // Also watch myNodeInfo because it might change later
        model.myNodeInfo.observe(viewLifecycleOwner, Observer {
            initNodeInfo()
        })

        updateFirmwareButton.setOnClickListener {
            doFirmwareUpdate()
        }

        usernameEditText.on(EditorInfo.IME_ACTION_DONE) {
            debug("did IME action")
            val n = usernameEditText.text.toString().trim()
            if (n.isNotEmpty())
                model.setOwner(n)

            requireActivity().hideKeyboard()
        }

        val app = (requireContext().applicationContext as GeeksvilleApplication)

        // Set analytics checkbox
        analyticsOkayCheckbox.isChecked = app.isAnalyticsAllowed

        analyticsOkayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            debug("User changed analytics to $isChecked")
            app.isAnalyticsAllowed = isChecked
            reportBugButton.isEnabled = app.isAnalyticsAllowed
        }

        // report bug button only enabled if analytics is allowed
        reportBugButton.isEnabled = app.isAnalyticsAllowed
        reportBugButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.report_a_bug)
                .setMessage(getString(R.string.report_bug_text))
                .setNeutralButton(R.string.cancel) { _, _ ->
                    debug("Decided not to report a bug")
                }
                .setPositiveButton(getString(R.string.report)) { _, _ ->
                    reportError("Clicked Report A Bug")
                }
                .show()
        }
    }

    private fun addDeviceButton(device: BTScanModel.DeviceListEntry, enabled: Boolean) {
        val b = RadioButton(requireActivity())
        b.text = device.name
        b.id = View.generateViewId()
        b.isEnabled = enabled
        b.isChecked =
            device.address == scanModel.selectedNotNull && device.bonded // Only show checkbox if device is still paired
        deviceRadioGroup.addView(b)

        // Once we have at least one device, don't show the "looking for" animation - it makes uers think
        // something is busted
        scanProgressBar.visibility = View.INVISIBLE

        b.setOnClickListener {
            if (!device.bonded) // If user just clicked on us, try to bond
                scanStatusText.setText(R.string.starting_pairing)

            b.isChecked =
                scanModel.onSelected(requireActivity() as MainActivity, device)

            if (!b.isSelected)
                scanStatusText.setText(getString(R.string.please_pair))
        }
    }

    /// Show the GUI for classic scanning
    private fun showClassicWidgets(visible: Int) {
        scanProgressBar.visibility = visible
        deviceRadioGroup.visibility = visible
    }

    /// Setup the GUI to do a classic (pre SDK 26 BLE scan)
    private fun initClassicScan() {
        // Turn off the widgets for the new API (we turn on/off hte classic widgets when we start scanning
        changeRadioButton.visibility = View.GONE

        showClassicWidgets(View.VISIBLE)

        model.bluetoothEnabled.observe(viewLifecycleOwner, Observer { enabled ->
            if (enabled)
                scanModel.startScan()
            else
                scanModel.stopScan()
        })

        scanModel.errorText.observe(viewLifecycleOwner, Observer { errMsg ->
            if (errMsg != null) {
                scanStatusText.text = errMsg
            }
        })

        scanModel.devices.observe(viewLifecycleOwner, Observer { devices ->
            // Remove the old radio buttons and repopulate
            deviceRadioGroup.removeAllViews()

            val adapter = scanModel.bluetoothAdapter

            var hasShownOurDevice = false
            devices.values.forEach { device ->
                if (device.address == scanModel.selectedNotNull)
                    hasShownOurDevice = true
                addDeviceButton(device, true)
            }

            // The device the user is already paired with is offline currently, still show it
            // it in the list, but greyed out
            if (!hasShownOurDevice) {
                if (scanModel.selectedBluetooth != null && adapter != null && adapter.isEnabled) {
                    val bDevice =
                        adapter.getRemoteDevice(scanModel.selectedBluetooth)
                    if (bDevice.name != null) { // ignore nodes that node have a name, that means we've lost them since they appeared
                        val curDevice = BTScanModel.DeviceListEntry(
                            bDevice.name,
                            scanModel.selectedAddress!!,
                            bDevice.bondState == BOND_BONDED
                        )
                        addDeviceButton(curDevice, false)
                    }
                } else if (scanModel.selectedUSB != null) {
                    // Must be a USB device, show a placeholder disabled entry
                    val curDevice = BTScanModel.DeviceListEntry(
                        scanModel.selectedUSB!!,
                        scanModel.selectedAddress!!,
                        false
                    )
                    addDeviceButton(curDevice, false)
                }
            }

            val hasBonded =
                RadioInterfaceService.getBondedDeviceAddress(requireContext()) != null

            // get rid of the warning text once at least one device is paired
            warningNotPaired.visibility = if (hasBonded) View.GONE else View.VISIBLE
        })
    }

    /// Start running the modern scan, once it has one result we enable the
    private fun startBackgroundScan() {
        // Disable the change button until our scan has some results
        changeRadioButton.isEnabled = false

        // To skip filtering based on name and supported feature flags (UUIDs),
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("Meshtastic_.*"))
            // .addServiceUuid(ParcelUuid(RadioInterfaceService.BTM_SERVICE_UUID), null)
            .build()

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(false)
            .build()

        //val mainActivity = requireActivity() as MainActivity

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        deviceManager.associate(
            pairingRequest,
            object : CompanionDeviceManager.Callback() {

                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    debug("Found one device - enabling button")
                    changeRadioButton.isEnabled = true
                    changeRadioButton.setOnClickListener {
                        debug("User clicked BLE change button")

                        // Request code seems to be ignored anyways
                        startIntentSenderForResult(
                            chooserLauncher,
                            MainActivity.RC_SELECT_DEVICE, null, 0, 0, 0, null
                        )
                    }
                }

                override fun onFailure(error: CharSequence?) {
                    warn("BLE selection service failed $error")
                    // changeDeviceSelection(mainActivity, null) // deselect any device
                }
            }, null
        )
    }

    private fun initModernScan() {
        // Turn off the widgets for the classic API
        scanProgressBar.visibility = View.GONE
        deviceRadioGroup.visibility = View.GONE
        changeRadioButton.visibility = View.VISIBLE

        val curRadio = RadioInterfaceService.getBondedDeviceAddress(requireContext())

        if (curRadio != null) {
            scanStatusText.text = getString(R.string.current_pair).format(curRadio)
            changeRadioButton.text = getString(R.string.change_radio)
        } else {
            scanStatusText.text = getString(R.string.not_paired_yet)
            changeRadioButton.setText(R.string.select_radio)
        }

        startBackgroundScan()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initCommonUI()
        if (hasCompanionDeviceApi)
            initModernScan()
        else
            initClassicScan()
    }

    override fun onPause() {
        super.onPause()
        scanModel.stopScan()
    }

    /**
     * If the user has not turned on location access throw up a toast warning
     */
    private fun checkLocationEnabled() {
        // We do this painful process because LocationManager.isEnabled is only SDK28 or latet
        val builder = LocationSettingsRequest.Builder()
        builder.setNeedBle(true)

        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        builder.addLocationRequest(request) // Make sure we are granted high accuracy permission

        val locationSettingsResponse = LocationServices.getSettingsClient(requireActivity())
            .checkLocationSettings(builder.build())

        locationSettingsResponse.addOnSuccessListener {
            debug("We have location access")
        }

        locationSettingsResponse.addOnFailureListener { exception ->
            errormsg("Failed to get location access")
            if (exception is ResolvableApiException) {
                exceptionReporter {
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.

                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    // exception.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)

                    // For now just punt and show a dialog
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.location_disabled_warning),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else
                Exceptions.report(exception)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasCompanionDeviceApi)
            scanModel.startScan()

        // Keep reminding user BLE is still off
        val hasUSB = activity?.let { SerialInterface.findDrivers(it).isNotEmpty() } ?: true
        if (!hasUSB) {
            // Warn user if BLE is disabled
            if (scanModel.bluetoothAdapter?.isEnabled != true) {
                Toast.makeText(
                    requireContext(),
                    R.string.error_bluetooth,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                checkLocationEnabled()
            }
        }
    }
}

