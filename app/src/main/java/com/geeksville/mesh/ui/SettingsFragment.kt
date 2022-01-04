package com.geeksville.mesh.ui

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.BOND_BONDED
import android.bluetooth.BluetoothDevice.BOND_BONDING
import android.bluetooth.le.*
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.RemoteException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.android.isGooglePlayAvailable
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.RadioConfigProtos
import com.geeksville.mesh.android.bluetoothManager
import com.geeksville.mesh.android.hasLocationPermission
import com.geeksville.mesh.android.hasBackgroundPermission
import com.geeksville.mesh.android.usbManager
import com.geeksville.mesh.databinding.SettingsFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.*
import com.geeksville.mesh.service.SoftwareUpdateService.Companion.ACTION_UPDATE_PROGRESS
import com.geeksville.mesh.service.SoftwareUpdateService.Companion.ProgressNotStarted
import com.geeksville.mesh.service.SoftwareUpdateService.Companion.ProgressSuccess
import com.geeksville.util.anonymize
import com.geeksville.util.exceptionReporter
import com.geeksville.util.exceptionToSnackbar
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.regex.Pattern

object SLogging : Logging

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
    SLogging.info("Starting bonding for ${device.anonymize}")

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

    private val context: Context get() = getApplication<Application>().applicationContext

    init {
        debug("BTScanModel created")
    }

    open class DeviceListEntry(val name: String, val address: String, val bonded: Boolean) {
        val bluetoothAddress
            get() =
                if (isBluetooth)
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

    val bluetoothAdapter = context.bluetoothManager?.adapter
    private val usbManager get() = context.usbManager

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

            if ((result.device.name?.startsWith("Mesh") == true)) {
                val addr = result.device.address
                val fullAddr = "x$addr" // full address with the bluetooh prefix
                // prevent logspam because weill get get lots of redundant scan results
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

        return if (bluetoothAdapter == null || MockInterface.addressValid(context, "")) {
            warn("No bluetooth adapter.  Running under emulation?")

            val testnodes = listOf(
                DeviceListEntry("Included simulator", "m", true),
                DeviceListEntry("Complete simulator", "t10.0.2.2", true),
                DeviceListEntry(context.getString(R.string.none), "n", true)
                /* Don't populate fake bluetooth devices, because we don't want testlab inside of google
                to try and use them.

                DeviceListEntry("Meshtastic_ab12", "xaa", false),
                DeviceListEntry("Meshtastic_32ac", "xbb", true) */
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
                                // Samsung doesn't seem to filter properly by service so this can't work
                                // see https://stackoverflow.com/questions/57981986/altbeacon-android-beacon-library-not-working-after-device-has-screen-off-for-a-s/57995960#57995960
                                // and https://stackoverflow.com/a/45590493
                                // .setServiceUuid(ParcelUuid(BluetoothInterface.BTM_SERVICE_UUID))
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

            exceptionReporter {
                val bleAddress = it.bluetoothAddress
                if (bleAddress != null) {
                    // Request bonding for bluetooth
                    // We ignore missing BT adapters, because it lets us run on the emulator
                    bluetoothAdapter
                        ?.getRemoteDevice(bleAddress)?.let { device ->
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
    private var _binding: SettingsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

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

    private val myActivity get() = requireActivity() as MainActivity

    override fun onDestroy() {
        guiJob.cancel()
        super.onDestroy()
    }

    private fun doFirmwareUpdate() {
        model.meshService?.let { service ->

            debug("User started firmware update")
            binding.updateFirmwareButton.isEnabled = false // Disable until things complete
            binding.updateProgressBar.visibility = View.VISIBLE
            binding.updateProgressBar.progress = 0 // start from scratch

            exceptionToSnackbar(requireView()) {
                // We rely on our broadcast receiver to show progress as this progresses
                service.startFirmwareUpdate()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    /// Set the correct update button configuration based on current progress
    private fun refreshUpdateButton(enable: Boolean) {
        debug("Reiniting the update button")
        val info = model.myNodeInfo.value
        val service = model.meshService
        if (model.isConnected.value == MeshService.ConnectionState.CONNECTED && info != null && info.shouldUpdate && info.couldUpdate && service != null) {
            binding.updateFirmwareButton.visibility = View.VISIBLE
            binding.updateFirmwareButton.text =
                getString(R.string.update_to).format(getString(R.string.short_firmware_version))

            val progress = service.updateStatus

            binding.updateFirmwareButton.isEnabled = enable &&
                (progress < 0) // if currently doing an upgrade disable button

            if (progress >= 0) {
                binding.updateProgressBar.progress = progress // update partial progress
                binding.scanStatusText.setText(R.string.updating_firmware)
                binding.updateProgressBar.visibility = View.VISIBLE
            } else
                when (progress) {
                    ProgressSuccess -> {
                        binding.scanStatusText.setText(R.string.update_successful)
                        binding.updateProgressBar.visibility = View.GONE
                    }
                    ProgressNotStarted -> {
                        // Do nothing - because we don't want to overwrite the status text in this case
                        binding.updateProgressBar.visibility = View.GONE
                    }
                    else -> {
                        binding.scanStatusText.setText(R.string.update_failed)
                        binding.updateProgressBar.visibility = View.VISIBLE
                    }
                }
            binding.updateProgressBar.isEnabled = false

        } else {
            binding.updateFirmwareButton.visibility = View.GONE
            binding.updateProgressBar.visibility = View.GONE
        }
    }

    /**
     * Pull the latest device info from the model and into the GUI
     */
    private fun updateNodeInfo() {
        val connected = model.isConnected.value

        val isConnected = connected == MeshService.ConnectionState.CONNECTED
        binding.nodeSettings.visibility = if (isConnected) View.VISIBLE else View.GONE

        if (connected == MeshService.ConnectionState.DISCONNECTED)
            model.ownerName.value = ""

        // update the region selection from the device
        val region = model.region
        val spinner = binding.regionSpinner
        val unsetIndex = regions.indexOf(RadioConfigProtos.RegionCode.Unset.name)
        spinner.onItemSelectedListener = null

        debug("current region is $region")
        var regionIndex = regions.indexOf(region.name)
        if (regionIndex == -1) // Not found, probably because the device has a region our app doesn't yet understand.  Punt and say Unset
            regionIndex = unsetIndex

        // We don't want to be notified of our own changes, so turn off listener while making them
        spinner.setSelection(regionIndex, false)
        spinner.onItemSelectedListener = regionSpinnerListener
        spinner.isEnabled = true

        // If actively connected possibly let the user update firmware
        refreshUpdateButton(region != RadioConfigProtos.RegionCode.Unset)

        // Update the status string (highest priority messages first)
        val info = model.myNodeInfo.value
        val statusText = binding.scanStatusText
        val permissionsWarning = myActivity.getMissingMessage()
        when {
            permissionsWarning != null ->
                statusText.text = permissionsWarning

            region == RadioConfigProtos.RegionCode.Unset ->
                statusText.text = getString(R.string.must_set_region)

            connected == MeshService.ConnectionState.CONNECTED -> {
                val fwStr = info?.firmwareString ?: "unknown"
                statusText.text = getString(R.string.connected_to).format(fwStr)
            }
            connected == MeshService.ConnectionState.DISCONNECTED ->
                statusText.text = getString(R.string.not_connected)
            connected == MeshService.ConnectionState.DEVICE_SLEEP ->
                statusText.text = getString(R.string.connected_sleeping)
        }
    }

    private val regionSpinnerListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>,
            view: View,
            position: Int,
            id: Long
        ) {
            val item = parent.getItemAtPosition(position) as String?
            val asProto = item!!.let { RadioConfigProtos.RegionCode.valueOf(it) }
            exceptionToSnackbar(requireView()) {
                model.region = asProto
            }
            updateNodeInfo() // We might have just changed Unset to set
        }

        override fun onNothingSelected(parent: AdapterView<*>) {
            //TODO("Not yet implemented")
        }
    }

    /// the sorted list of region names like arrayOf("US", "CN", "EU488")
    private val regions = RadioConfigProtos.RegionCode.values().filter {
        it != RadioConfigProtos.RegionCode.UNRECOGNIZED
    }.map {
        it.name
    }.sorted()

    /// Setup the ui widgets unrelated to BLE scanning
    private fun initCommonUI() {

        // init our region spinner
        val spinner = binding.regionSpinner
        val regionAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, regions)
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = regionAdapter

        model.ownerName.observe(viewLifecycleOwner, { name ->
            binding.usernameEditText.setText(name)
        })

        // Only let user edit their name or set software update while connected to a radio
        model.isConnected.observe(viewLifecycleOwner, Observer { _ ->
            updateNodeInfo()
        })

        // Also watch myNodeInfo because it might change later
        model.myNodeInfo.observe(viewLifecycleOwner, Observer {
            updateNodeInfo()
        })

        binding.updateFirmwareButton.setOnClickListener {
            doFirmwareUpdate()
        }

        binding.usernameEditText.on(EditorInfo.IME_ACTION_DONE) {
            debug("did IME action")
            val n = binding.usernameEditText.text.toString().trim()
            if (n.isNotEmpty())
                model.setOwner(n)
            requireActivity().hideKeyboard()
        }

        binding.provideLocationCheckbox.isEnabled = isGooglePlayAvailable(requireContext())
        binding.provideLocationCheckbox.setOnCheckedChangeListener { view, isChecked ->
            if (view.isChecked) {
                debug("User changed location tracking to $isChecked")
                if (view.isPressed) { // We want to ignore changes caused by code (as opposed to the user)
                    val hasLocationPermission = myActivity.hasLocationPermission()
                    val hasBackgroundPermission = myActivity.hasBackgroundPermission()

                    // Don't check the box until the system setting changes
                    view.isChecked = hasLocationPermission && hasBackgroundPermission

                    if (!hasLocationPermission) // Make sure we have location permission (prerequisite)
                        myActivity.requestLocationPermission()
                    if (hasLocationPermission && !hasBackgroundPermission)
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.background_required)
                            .setMessage(R.string.why_background_required)
                            .setNeutralButton(R.string.cancel) { _, _ ->
                                debug("User denied background permission")
                            }
                            .setPositiveButton(getString(R.string.accept)) { _, _ ->
                                myActivity.requestBackgroundPermission()
                            }
                            .show()

                    if (view.isChecked)
                        model.provideLocation.value = isChecked
                        model.meshService?.setupProvideLocation()
                }
            }
            else {
                model.provideLocation.value = isChecked
                model.meshService?.stopProvideLocation()
            }
        }

        val app = (requireContext().applicationContext as GeeksvilleApplication)

        // Set analytics checkbox
        binding.analyticsOkayCheckbox.isChecked = app.isAnalyticsAllowed

        binding.analyticsOkayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            debug("User changed analytics to $isChecked")
            app.isAnalyticsAllowed = isChecked
            binding.reportBugButton.isEnabled = app.isAnalyticsAllowed
        }

        // report bug button only enabled if analytics is allowed
        binding.reportBugButton.isEnabled = app.isAnalyticsAllowed
        binding.reportBugButton.setOnClickListener {
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
        binding.deviceRadioGroup.addView(b)

        // Once we have at least one device, don't show the "looking for" animation - it makes uers think
        // something is busted
        binding.scanProgressBar.visibility = View.INVISIBLE

        b.setOnClickListener {
            if (!device.bonded) // If user just clicked on us, try to bond
                binding.scanStatusText.setText(R.string.starting_pairing)

            b.isChecked =
                scanModel.onSelected(myActivity, device)

            if (!b.isSelected)
                binding.scanStatusText.text = getString(R.string.please_pair)
        }
    }

    /// Show the GUI for classic scanning
    private fun showClassicWidgets(visible: Int) {
        binding.scanProgressBar.visibility = visible
        binding.deviceRadioGroup.visibility = visible
    }

    private fun updateDevicesButtons(devices: MutableMap<String, BTScanModel.DeviceListEntry>?) {
        // Remove the old radio buttons and repopulate
        binding.deviceRadioGroup.removeAllViews()

        if (devices == null) return

        val adapter = scanModel.bluetoothAdapter
        var hasShownOurDevice = false
        devices.values.forEach { device ->
            if (device.address == scanModel.selectedNotNull)
                hasShownOurDevice = true
            addDeviceButton(device, true)
        }

        // The selected device is not in the scan; it is either offline, or it doesn't advertise
        // itself (most BLE devices don't advertise when connected).
        // Show it in the list, greyed out based on connection status.
        if (!hasShownOurDevice) {
            // Note: we pull this into a tempvar, because otherwise some other thread can change selectedAddress after our null check
            // and before use
            val bleAddr = scanModel.selectedBluetooth

            if (bleAddr != null && adapter != null && adapter.isEnabled) {
                val bDevice =
                    adapter.getRemoteDevice(bleAddr)
                if (bDevice.name != null) { // ignore nodes that node have a name, that means we've lost them since they appeared
                    val curDevice = BTScanModel.DeviceListEntry(
                        bDevice.name,
                        scanModel.selectedAddress!!,
                        bDevice.bondState == BOND_BONDED
                    )
                    addDeviceButton(
                        curDevice,
                        model.isConnected.value == MeshService.ConnectionState.CONNECTED
                    )
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

        // get rid of the warning text once at least one device is paired.
        // If we are running on an emulator, always leave this message showing so we can test the worst case layout
        binding.warningNotPaired.visibility =
            if (hasBonded && !MockInterface.addressValid(requireContext(), ""))
                View.GONE
            else
                View.VISIBLE
    }

    /// Setup the GUI to do a classic (pre SDK 26 BLE scan)
    private fun initClassicScan() {
        // Turn off the widgets for the new API (we turn on/off hte classic widgets when we start scanning
        binding.changeRadioButton.visibility = View.GONE

        showClassicWidgets(View.VISIBLE)

        model.bluetoothEnabled.observe(viewLifecycleOwner, Observer { enabled ->
            if (enabled)
                scanModel.startScan()
            else
                scanModel.stopScan()
        })

        scanModel.errorText.observe(viewLifecycleOwner, Observer { errMsg ->
            if (errMsg != null) {
                binding.scanStatusText.text = errMsg
            }
        })

        scanModel.devices.observe(
            viewLifecycleOwner,
            Observer { devices -> updateDevicesButtons(devices) })

        model.isConnected.observe(
            viewLifecycleOwner,
            { updateDevicesButtons(scanModel.devices.value) })
    }

    /// Start running the modern scan, once it has one result we enable the
    private fun startBackgroundScan() {
        // Disable the change button until our scan has some results
        binding.changeRadioButton.isEnabled = false

        // To skip filtering based on name and supported feature flags (UUIDs),
        // don't include calls to setNamePattern() and addServiceUuid(),
        // respectively. This example uses Bluetooth.
        // We only look for Mesh (rather than the full name) because NRF52 uses a very short name
        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("Mesh.*"))
            // .addServiceUuid(ParcelUuid(RadioInterfaceService.BTM_SERVICE_UUID), null)
            .build()

        // The argument provided in setSingleDevice() determines whether a single
        // device name or a list of device names is presented to the user as
        // pairing options.
        val pairingRequest: AssociationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(false)
            .build()

        // When the app tries to pair with the Bluetooth device, show the
        // appropriate pairing request dialog to the user.
        deviceManager.associate(
            pairingRequest,
            object : CompanionDeviceManager.Callback() {

                override fun onDeviceFound(chooserLauncher: IntentSender) {
                    debug("Found one device - enabling button")
                    binding.changeRadioButton.isEnabled = true
                    binding.changeRadioButton.setOnClickListener {
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
        binding.scanProgressBar.visibility = View.GONE
        binding.deviceRadioGroup.visibility = View.GONE
        binding.changeRadioButton.visibility = View.VISIBLE

        val curRadio = RadioInterfaceService.getBondedDeviceAddress(requireContext())

        if (curRadio != null) {
            binding.scanStatusText.text = getString(R.string.current_pair).format(curRadio)
            binding.changeRadioButton.text = getString(R.string.change_radio)
        } else {
            binding.scanStatusText.text = getString(R.string.not_paired_yet)
            binding.changeRadioButton.setText(R.string.select_radio)
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

    // If the user has not turned on location access throw up a toast warning
    private fun checkLocationEnabled() {

        fun hasGps(): Boolean =
            myActivity.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

        // FIXME If they don't have google play for now we don't check for location enabled
        if (hasGps() && isGooglePlayAvailable(requireContext())) {
            // We do this painful process because LocationManager.isEnabled is only SDK28 or latet
            val builder = LocationSettingsRequest.Builder()
            builder.setNeedBle(true)

            val request = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            builder.addLocationRequest(request) // Make sure we are granted high accuracy permission

            val locationSettingsResponse = LocationServices.getSettingsClient(requireActivity())
                .checkLocationSettings(builder.build())

            fun weNeedAccess() {
                context?.let { c ->
                    warn("Telling user we need need location accesss")
                    Toast.makeText(
                        c,
                        getString(R.string.location_disabled_warning),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            locationSettingsResponse.addOnSuccessListener {
                if (!it.locationSettingsStates.isBleUsable || !it.locationSettingsStates.isLocationUsable)
                    weNeedAccess()
                else
                    debug("We have location access")
            }

            locationSettingsResponse.addOnFailureListener { _ ->
                errormsg("Failed to get location access")
                // We always show the toast regardless of what type of exception we receive.  Because even non
                // resolvable api exceptions mean user still needs to fix something.

                ///if (exception is ResolvableApiException) {

                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.

                // Show the dialog by calling startResolutionForResult(),
                // and check the result in onActivityResult().
                // exception.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)

                // For now just punt and show a dialog

                // The context might be gone (if activity is going away) by the time this handler is called
                weNeedAccess()

                //} else
                //    Exceptions.report(exception)
            }
        }
    }

    private val updateProgressFilter = IntentFilter(ACTION_UPDATE_PROGRESS)

    private val updateProgressReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshUpdateButton(true)
        }
    }

    override fun onPause() {
        super.onPause()
        scanModel.stopScan()

        requireActivity().unregisterReceiver(updateProgressReceiver)
    }

    override fun onResume() {
        super.onResume()

        if (!hasCompanionDeviceApi)
            scanModel.startScan()

        // system permissions might have changed while we were away
        binding.provideLocationCheckbox.isChecked = myActivity.hasLocationPermission() && myActivity.hasBackgroundPermission() && (model.provideLocation.value ?: false) && isGooglePlayAvailable(requireContext())

        myActivity.registerReceiver(updateProgressReceiver, updateProgressFilter)

        // Keep reminding user BLE is still off
        val hasUSB = SerialInterface.findDrivers(myActivity).isNotEmpty()
        if (!hasUSB) {
            // First warn about permissions, and then if needed warn about settings
            if (!myActivity.warnMissingPermissions()) {
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
}
