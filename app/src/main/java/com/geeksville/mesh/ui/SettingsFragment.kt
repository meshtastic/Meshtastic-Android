package com.geeksville.mesh.ui

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.android.*
import com.geeksville.mesh.databinding.SettingsFragmentBinding
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getInitials
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.SoftwareUpdateService
import com.geeksville.mesh.util.PendingIntentCompat
import com.geeksville.mesh.util.anonymize
import com.geeksville.mesh.util.exceptionReporter
import com.geeksville.mesh.util.exceptionToSnackbar
import com.geeksville.mesh.util.getParcelableExtraCompat
import com.geeksville.mesh.util.onEditorAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : ScreenFragment("Settings"), Logging {
    private var _binding: SettingsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val scanModel: BTScanModel by activityViewModels()
    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val model: UIViewModel by activityViewModels()

    @Inject
    internal lateinit var locationRepository: LocationRepository

    private val myActivity get() = requireActivity() as MainActivity

    private val hasGps by lazy { requireContext().hasGps() }
    private val hasCompanionDeviceApi by lazy { requireContext().hasCompanionDeviceApi() }
    private val useCompanionDeviceApi by lazy {
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && hasCompanionDeviceApi
    }

    private fun doFirmwareUpdate() {
        model.meshService?.let { service ->

            debug("User started firmware update")
            GeeksvilleApplication.analytics.track(
                "firmware_update",
                DataPair("content_type", "start")
            )
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
    ): View {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    /// Set the correct update button configuration based on current progress
    private fun refreshUpdateButton(enable: Boolean) {
        debug("Reiniting the update button")
        val info = model.myNodeInfo.value
        val service = model.meshService
        if (model.isConnected() && info != null && info.shouldUpdate && info.couldUpdate && service != null) {
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
                    SoftwareUpdateService.ProgressSuccess -> {
                        GeeksvilleApplication.analytics.track(
                            "firmware_update",
                            DataPair("content_type", "success")
                        )
                        binding.scanStatusText.setText(R.string.update_successful)
                        binding.updateProgressBar.visibility = View.GONE
                    }
                    SoftwareUpdateService.ProgressNotStarted -> {
                        // Do nothing - because we don't want to overwrite the status text in this case
                        binding.updateProgressBar.visibility = View.GONE
                    }
                    else -> {
                        GeeksvilleApplication.analytics.track(
                            "firmware_update",
                            DataPair("content_type", "failure")
                        )
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
        val connectionState = model.connectionState.value
        val isConnected = connectionState == MeshService.ConnectionState.CONNECTED

        binding.nodeSettings.visibility = if (isConnected) View.VISIBLE else View.GONE
        binding.provideLocationCheckbox.visibility = if (isConnected) View.VISIBLE else View.GONE

        binding.usernameEditText.isEnabled = isConnected && !model.isManaged

        if (hasGps) {
            binding.provideLocationCheckbox.isEnabled = true
        } else {
            binding.provideLocationCheckbox.isChecked = false
            binding.provideLocationCheckbox.isEnabled = false
        }

        // update the region selection from the device
        val region = model.region
        val spinner = binding.regionSpinner
        val unsetIndex = regions.indexOf(ConfigProtos.Config.LoRaConfig.RegionCode.UNSET.name)
        spinner.onItemSelectedListener = null

        debug("current region is $region")
        var regionIndex = regions.indexOf(region.name)
        if (regionIndex == -1) // Not found, probably because the device has a region our app doesn't yet understand.  Punt and say Unset
            regionIndex = unsetIndex

        // We don't want to be notified of our own changes, so turn off listener while making them
        spinner.setSelection(regionIndex, false)
        spinner.onItemSelectedListener = regionSpinnerListener
        spinner.isEnabled = !model.isManaged

        // If actively connected possibly let the user update firmware
        refreshUpdateButton(isConnected)

        // Update the status string (highest priority messages first)
        val info = model.myNodeInfo.value
        when (connectionState) {
            MeshService.ConnectionState.CONNECTED ->
                if (region.number == 0) R.string.must_set_region else R.string.connected_to
            MeshService.ConnectionState.DISCONNECTED -> R.string.not_connected
            MeshService.ConnectionState.DEVICE_SLEEP -> R.string.connected_sleeping
            else -> null
        }?.let {
            val firmwareString = info?.firmwareString ?: getString(R.string.unknown)
            scanModel.setErrorText(getString(it, firmwareString))
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
            val asProto = item!!.let { ConfigProtos.Config.LoRaConfig.RegionCode.valueOf(it) }
            exceptionToSnackbar(requireView()) {
                debug("regionSpinner onItemSelected $asProto")
                if (asProto != model.region) model.region = asProto
            }
            updateNodeInfo() // We might have just changed Unset to set
        }

        override fun onNothingSelected(parent: AdapterView<*>) {
            //TODO("Not yet implemented")
        }
    }

    /// the sorted list of region names like arrayOf("US", "CN", "EU488")
    private val regions = ConfigProtos.Config.LoRaConfig.RegionCode.entries.filter {
        it != ConfigProtos.Config.LoRaConfig.RegionCode.UNRECOGNIZED
    }.map {
        it.name
    }.sorted()

    private fun initCommonUI() {

        val associationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            it.data
                ?.getParcelableExtraCompat<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
                ?.let { device -> onSelected(BTScanModel.BLEDeviceListEntry(device)) }
        }

        val requestBackgroundAndCheckLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    binding.provideLocationCheckbox.isChecked = true
                } else {
                    debug("User denied background permission")
                    model.showSnackbar(getString(R.string.why_background_required))
                }
            }

        val requestLocationAndBackgroundLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    // Older versions of android only need Location permission
                    if (requireContext().hasBackgroundPermission()) {
                        binding.provideLocationCheckbox.isChecked = true
                    } else requestBackgroundAndCheckLauncher.launch(requireContext().getBackgroundPermissions())
                } else {
                    debug("User denied location permission")
                    model.showSnackbar(getString(R.string.why_background_required))
                }
            }

        // init our region spinner
        val spinner = binding.regionSpinner
        val regionAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, regions)
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = regionAdapter

        model.ourNodeInfo.asLiveData().observe(viewLifecycleOwner) { node ->
            binding.usernameEditText.setText(node?.user?.longName.orEmpty())
        }

        scanModel.devices.observe(viewLifecycleOwner) { devices ->
            updateDevicesButtons(devices)
        }

        // Only let user edit their name or set software update while connected to a radio
        model.connectionState.observe(viewLifecycleOwner) {
            updateNodeInfo()
        }

        model.localConfig.asLiveData().observe(viewLifecycleOwner) {
            if (!model.isConnected()) {
                val configCount = it.allFields.size
                val configTotal = ConfigProtos.Config.getDescriptor().fields.size
                if (configCount > 0)
                    scanModel.setErrorText("Device config ($configCount / $configTotal)")
            } else updateNodeInfo()
        }

        model.moduleConfig.asLiveData().observe(viewLifecycleOwner) {
            if (!model.isConnected()) {
                val moduleCount = it.allFields.size
                val moduleTotal = ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size
                if (moduleCount > 0)
                    scanModel.setErrorText("Module config ($moduleCount / $moduleTotal)")
            } else updateNodeInfo()
        }

        model.channels.asLiveData().observe(viewLifecycleOwner) {
            if (!model.isConnected()) {
                val maxChannels = model.maxChannels
                if (!it.hasLoraConfig() && it.settingsCount > 0)
                    scanModel.setErrorText("Channels (${it.settingsCount} / $maxChannels)")
            }
        }

        // Also watch myNodeInfo because it might change later
        model.myNodeInfo.asLiveData().observe(viewLifecycleOwner) {
            updateNodeInfo()
        }

        scanModel.errorText.observe(viewLifecycleOwner) { errMsg ->
            if (errMsg != null) {
                binding.scanStatusText.text = errMsg
            }
        }

        // show the spinner when [spinner] is true
        scanModel.spinner.observe(viewLifecycleOwner) { show ->
            binding.changeRadioButton.isEnabled = !show
            binding.scanProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        }

        scanModel.associationRequest.observe(viewLifecycleOwner) { request ->
            request?.let {
                associationResultLauncher.launch(request)
                scanModel.clearAssociationRequest()
            }
        }

        binding.updateFirmwareButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("${getString(R.string.update_firmware)}?")
                .setNeutralButton(R.string.cancel) { _, _ ->
                }
                .setPositiveButton(getString(R.string.okay)) { _, _ ->
                    doFirmwareUpdate()
                }
                .show()
        }

        binding.usernameEditText.onEditorAction(EditorInfo.IME_ACTION_DONE) {
            debug("received IME_ACTION_DONE")
            val n = binding.usernameEditText.text.toString().trim()
            model.ourNodeInfo.value?.user?.let {
                val user = it.copy(longName = n, shortName = getInitials(n))
                if (n.isNotEmpty()) model.setOwner(user)
            }
            requireActivity().hideKeyboard()
        }

        // Observe receivingLocationUpdates state and update provideLocationCheckbox
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationRepository.receivingLocationUpdates.collect {
                    binding.provideLocationCheckbox.isChecked = it
                }
            }
        }

        binding.provideLocationCheckbox.setOnCheckedChangeListener { view, isChecked ->
            // Don't check the box until the system setting changes
            view.isChecked = isChecked && requireContext().hasBackgroundPermission()

            if (view.isPressed) { // We want to ignore changes caused by code (as opposed to the user)
                debug("User changed location tracking to $isChecked")
                model.provideLocation.value = isChecked
                if (isChecked && !view.isChecked)
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.background_required)
                        .setMessage(R.string.why_background_required)
                        .setNeutralButton(R.string.cancel) { _, _ ->
                            debug("User denied background permission")
                        }
                        .setPositiveButton(getString(R.string.accept)) { _, _ ->
                            // Make sure we have location permission (prerequisite)
                            if (!requireContext().hasLocationPermission()) {
                                requestLocationAndBackgroundLauncher.launch(requireContext().getLocationPermissions())
                            } else {
                                requestBackgroundAndCheckLauncher.launch(requireContext().getBackgroundPermissions())
                            }
                        }
                        .show()
            }
            if (view.isChecked) {
                checkLocationEnabled(getString(R.string.location_disabled))
                model.meshService?.startProvideLocation()
            } else {
                model.meshService?.stopProvideLocation()
            }
        }

        val app = (requireContext().applicationContext as GeeksvilleApplication)
        val isGooglePlayAvailable = isGooglePlayAvailable(requireContext())
        val isAnalyticsAllowed = app.isAnalyticsAllowed && isGooglePlayAvailable

        // Set analytics checkbox
        binding.analyticsOkayCheckbox.isEnabled = isGooglePlayAvailable
        binding.analyticsOkayCheckbox.isChecked = isAnalyticsAllowed

        binding.analyticsOkayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            debug("User changed analytics to $isChecked")
            app.isAnalyticsAllowed = isChecked
            binding.reportBugButton.isEnabled = isAnalyticsAllowed
        }

        // report bug button only enabled if analytics is allowed
        binding.reportBugButton.isEnabled = isAnalyticsAllowed
        binding.reportBugButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.report_a_bug)
                .setMessage(getString(R.string.report_bug_text))
                .setNeutralButton(R.string.cancel) { _, _ ->
                    debug("Decided not to report a bug")
                }
                .setPositiveButton(getString(R.string.report)) { _, _ ->
                    reportError("Clicked Report A Bug")
                    model.showSnackbar("Bug report sent!")
                }
                .show()
        }
    }

    private fun addDeviceButton(device: BTScanModel.DeviceListEntry, enabled: Boolean) {
        val b = RadioButton(requireActivity())
        b.text = device.name
        b.id = View.generateViewId()
        b.isEnabled = enabled
        b.isChecked = device.fullAddress == scanModel.selectedNotNull
        binding.deviceRadioGroup.addView(b)

        b.setOnClickListener {
            if (!device.bonded) // If user just clicked on us, try to bond
                binding.scanStatusText.setText(R.string.starting_pairing)

            b.isChecked = onSelected(device)
        }
    }

    private fun addManualDeviceButton() {
        val b = binding.radioButtonManual
        val e = binding.editManualAddress

        b.isEnabled = false

        binding.deviceRadioGroup.addView(b)


        b.setOnClickListener {


            b.isChecked = onSelected(BTScanModel.DeviceListEntry("", "t" + e.text, true))

        }
        binding.deviceRadioGroup.addView(e)
        e.doAfterTextChanged {
            b.isEnabled = Patterns.IP_ADDRESS.matcher(e.text).matches()
        }
    }

    private fun updateDevicesButtons(devices: MutableMap<String, BTScanModel.DeviceListEntry>?) {
        // Remove the old radio buttons and repopulate
        binding.deviceRadioGroup.removeAllViews()

        if (devices == null) return

        var hasShownOurDevice = false
        devices.values.forEach { device ->
            if (device.fullAddress == scanModel.selectedNotNull)
                hasShownOurDevice = true
            addDeviceButton(device, true)
        }

        // The selected device is not in the scan; it is either offline, or it doesn't advertise
        // itself (most BLE devices don't advertise when connected).
        // Show it in the list, greyed out based on connection status.
        if (!hasShownOurDevice) {
            // Note: we pull this into a tempvar, because otherwise some other thread can change selectedAddress after our null check
            // and before use
            val curAddr = scanModel.selectedAddress
            if (curAddr != null) {
                val curDevice = scanModel.getDeviceListEntry(curAddr)
                addDeviceButton(curDevice, model.isConnected())
            }
        }

        addManualDeviceButton()

        // get rid of the warning text once at least one device is paired.
        // If we are running on an emulator, always leave this message showing so we can test the worst case layout
        val curRadio = scanModel.selectedAddress

        if (curRadio != null && !scanModel.isMockInterfaceAddressValid) {
            binding.warningNotPaired.visibility = View.GONE
        } else if (bluetoothViewModel.enabled.value == true) {
            binding.warningNotPaired.visibility = View.VISIBLE
            scanModel.setErrorText(getString(R.string.not_paired_yet))
        }
    }

    // per https://developer.android.com/guide/topics/connectivity/bluetooth/find-ble-devices
    private fun scanLeDevice() {
        var scanning = false

        if (!scanning) { // Stops scanning after a pre-defined scan period.
            Handler(Looper.getMainLooper()).postDelayed({
                scanning = false
                scanModel.stopScan()
            }, SCAN_PERIOD)
            scanning = true
            scanModel.startScan(requireActivity().takeIf { useCompanionDeviceApi })
        } else {
            scanning = false
            scanModel.stopScan()
        }
    }

    private fun changeDeviceAddress(address: String) {
        try {
            model.meshService?.let { service ->
                MeshService.changeDeviceAddress(requireActivity(), service, address)
            }
            scanModel.changeSelectedAddress(address) // if it throws the change will be discarded
        } catch (ex: RemoteException) {
            errormsg("changeDeviceSelection failed, probably it is shutting down $ex.message")
            // ignore the failure and the GUI won't be updating anyways
        }
    }

    /// Called by the GUI when a new device has been selected by the user
    /// Returns true if we were able to change to that item
    private fun onSelected(it: BTScanModel.DeviceListEntry): Boolean {
        // If the device is paired, let user select it, otherwise start the pairing flow
        if (it.bonded) {
            changeDeviceAddress(it.fullAddress)
            return true
        } else {
            // Handle requesting USB or bluetooth permissions for the device
            debug("Requesting permissions for the device")

            exceptionReporter {
                if (it.isBLE) {
                    // Request bonding for bluetooth
                    // We ignore missing BT adapters, because it lets us run on the emulator
                    scanModel.getRemoteDevice(it.address)?.let { device ->
                        requestBonding(device) { state ->
                            if (state == BluetoothDevice.BOND_BONDED) {
                                scanModel.setErrorText(getString(R.string.pairing_completed))
                                changeDeviceAddress(it.fullAddress)
                            } else {
                                scanModel.setErrorText(getString(R.string.pairing_failed_try_again))
                            }
                        }
                    }
                }
            }

            if (it.isUSB) {
                it as BTScanModel.USBDeviceListEntry

                val usbReceiver = object : BroadcastReceiver() {

                    override fun onReceive(context: Context, intent: Intent) {
                        if (BTScanModel.ACTION_USB_PERMISSION != intent.action) return

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            info("User approved USB access")
                            changeDeviceAddress(it.fullAddress)
                        } else {
                            errormsg("USB permission denied for device ${it.address}")
                        }
                        // We don't need to stay registered
                        requireActivity().unregisterReceiver(this)
                    }
                }

                val permissionIntent = PendingIntent.getBroadcast(
                    activity,
                    0,
                    Intent(BTScanModel.ACTION_USB_PERMISSION),
                    PendingIntentCompat.FLAG_MUTABLE
                )
                val filter = IntentFilter(BTScanModel.ACTION_USB_PERMISSION)
                requireActivity().registerReceiver(usbReceiver, filter)
                requireContext().usbManager.requestPermission(it.usb.device, permissionIntent)
            }

            return false
        }
    }

    /// Show the UI asking the user to bond with a device, call changeSelection() if/when bonding completes
    @SuppressLint("MissingPermission")
    private fun requestBonding(
        device: BluetoothDevice,
        onComplete: (Int) -> Unit
    ) {
        info("Starting bonding for ${device.anonymize}")

        // We need this receiver to get informed when the bond attempt finished
        val bondChangedReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                debug("Received bond state changed $state")

                if (state != BluetoothDevice.BOND_BONDING) {
                    context.unregisterReceiver(this) // we stay registered until bonding completes (either with BONDED or NONE)
                    debug("Bonding completed, state=$state")
                    onComplete(state)
                }
            }
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        requireActivity().registerReceiver(bondChangedReceiver, filter)

        // We ignore missing BT adapters, because it lets us run on the emulator
        try {
            device.createBond()
        } catch (ex: Throwable) {
            warn("Failed creating Bluetooth bond: ${ex.message}")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initCommonUI()

        val requestPermissionAndScanLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    info("Bluetooth permissions granted")
                    checkBTEnabled()
                    if (!hasCompanionDeviceApi) checkLocationEnabled()
                    scanLeDevice()
                } else {
                    warn("Bluetooth permissions denied")
                    model.showSnackbar(requireContext().permissionMissing)
                }
                bluetoothViewModel.permissionsUpdated()
            }

        binding.changeRadioButton.setOnClickListener {
            debug("User clicked changeRadioButton")
            scanLeDevice()
            val bluetoothPermissions = requireContext().getBluetoothPermissions()
            if (bluetoothPermissions.isEmpty()) {
                checkBTEnabled()
                if (!hasCompanionDeviceApi) checkLocationEnabled()
            } else {
                requireContext().rationaleDialog(
                    shouldShowRequestPermissionRationale(bluetoothPermissions)
                ) {
                    requestPermissionAndScanLauncher.launch(bluetoothPermissions)
                }
            }
        }
    }

    // If the user has not turned on location access throw up a warning
    private fun checkLocationEnabled(
        // Default warning valid only for classic bluetooth scan
        warningReason: String = getString(R.string.location_disabled_warning)
    ) {
        if (requireContext().gpsDisabled()) {
            warn("Telling user we need location access")
            model.showSnackbar(warningReason)
        }
    }

    private fun checkBTEnabled() {
        if (bluetoothViewModel.enabled.value == false) {
            warn("Telling user bluetooth is disabled")
            model.showSnackbar(R.string.bluetooth_disabled)
        }
    }

    private val updateProgressFilter = IntentFilter(SoftwareUpdateService.ACTION_UPDATE_PROGRESS)

    private val updateProgressReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshUpdateButton(true)
        }
    }

    override fun onPause() {
        super.onPause()

        requireActivity().unregisterReceiver(updateProgressReceiver)
    }

    override fun onResume() {
        super.onResume()

        // system permissions might have changed while we were away
        binding.provideLocationCheckbox.isChecked = requireContext().hasBackgroundPermission() && (model.provideLocation.value ?: false)

        myActivity.registerReceiver(updateProgressReceiver, updateProgressFilter)

        // Warn user if BLE device is selected but BLE disabled
        if (scanModel.selectedBluetooth) checkBTEnabled()

        // Warn user if provide location is selected but location disabled
        if (binding.provideLocationCheckbox.isChecked)
            checkLocationEnabled(getString(R.string.location_disabled))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val SCAN_PERIOD: Long = 10000 // Stops scanning after 10 seconds
    }
}
