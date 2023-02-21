package com.geeksville.mesh.ui

import android.bluetooth.BluetoothDevice
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.analytics.DataPair
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.hideKeyboard
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.android.*
import com.geeksville.mesh.databinding.SettingsFragmentBinding
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.repository.radio.MockInterface
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.SoftwareUpdateService
import com.geeksville.mesh.util.exceptionToSnackbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

object SLogging : Logging

/// Change to a new macaddr selection, updating GUI and radio
fun changeDeviceSelection(context: MainActivity, newAddr: String?) {
    // FIXME, this is a kinda yucky way to find the service
    context.model.meshService?.let { service ->
        MeshService.changeDeviceAddress(context, service, newAddr)
    }
}

@AndroidEntryPoint
class SettingsFragment : ScreenFragment("Settings"), Logging {
    private var _binding: SettingsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val scanModel: BTScanModel by activityViewModels()
    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val model: UIViewModel by activityViewModels()

    @Inject
    internal lateinit var usbRepository: UsbRepository

    @Inject
    internal lateinit var locationRepository: LocationRepository
    private var receivingLocationUpdates: Job? = null

    private val myActivity get() = requireActivity() as MainActivity

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
        val connected = model.connectionState.value

        binding.nodeSettings.visibility = if (model.isConnected()) View.VISIBLE else View.GONE
        binding.provideLocationCheckbox.visibility = if (model.isConnected()) View.VISIBLE else View.GONE

        if (connected == MeshService.ConnectionState.DISCONNECTED)
            model.setOwner("")

        if (requireContext().hasGps() && model.config.position.gpsEnabled) {
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
        spinner.isEnabled = true

        // If actively connected possibly let the user update firmware
        refreshUpdateButton(model.isConnected())

        // Update the status string (highest priority messages first)
        val info = model.myNodeInfo.value
        val statusText = binding.scanStatusText
        when (connected) {
            MeshService.ConnectionState.CONNECTED -> {
                statusText.text = if (region.number == 0) getString(R.string.must_set_region)
                else getString(R.string.connected_to).format(info?.firmwareString ?: "unknown")
            }
            MeshService.ConnectionState.DISCONNECTED ->
                statusText.text = getString(R.string.not_connected)
            MeshService.ConnectionState.DEVICE_SLEEP ->
                statusText.text = getString(R.string.connected_sleeping)
            else -> {}
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
    private val regions = ConfigProtos.Config.LoRaConfig.RegionCode.values().filter {
        it != ConfigProtos.Config.LoRaConfig.RegionCode.UNRECOGNIZED
    }.map {
        it.name
    }.sorted()

    private fun initCommonUI() {

        val associationResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) {
            it.data
                ?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
                ?.let { device ->
                    scanModel.onSelected(
                        myActivity,
                        BTScanModel.DeviceListEntry(
                            device.name,
                            "x${device.address}",
                            device.bondState == BluetoothDevice.BOND_BONDED
                        )
                    )
                }
        }

        val requestBackgroundAndCheckLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    binding.provideLocationCheckbox.isChecked = true
                } else {
                    debug("User denied background permission")
                    showSnackbar(getString(R.string.why_background_required))
                }
            }

        val requestLocationAndBackgroundLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    // Older versions of android only need Location permission
                    if (myActivity.hasBackgroundPermission()) {
                        binding.provideLocationCheckbox.isChecked = true
                    } else requestBackgroundAndCheckLauncher.launch(myActivity.getBackgroundPermissions())
                } else {
                    debug("User denied location permission")
                    showSnackbar(getString(R.string.why_background_required))
                }
            }

        // init our region spinner
        val spinner = binding.regionSpinner
        val regionAdapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, regions)
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = regionAdapter

        bluetoothViewModel.enabled.observe(viewLifecycleOwner) { enabled ->
            if (enabled) scanModel.setupScan()
        }

        model.ownerName.observe(viewLifecycleOwner) { name ->
            binding.usernameEditText.isEnabled = !name.isNullOrEmpty()
            binding.usernameEditText.setText(name)
        }

        // Only let user edit their name or set software update while connected to a radio
        model.connectionState.observe(viewLifecycleOwner) {
            updateNodeInfo()
            updateDevicesButtons(scanModel.devices.value)
        }

        model.localConfig.asLiveData().observe(viewLifecycleOwner) {
            if (!model.isConnected()) {
                val configCount = it.allFields.size
                val configTotal = ConfigProtos.Config.getDescriptor().fields.size
                if (configCount > 0)
                    binding.scanStatusText.text = "Device config ($configCount / $configTotal)"
            } else updateNodeInfo()
        }

        model.moduleConfig.asLiveData().observe(viewLifecycleOwner) {
            if (!model.isConnected()) {
                val moduleCount = it.allFields.size
                val moduleTotal = ModuleConfigProtos.ModuleConfig.getDescriptor().fields.size
                if (moduleCount > 0)
                    binding.scanStatusText.text = "Module config ($moduleCount / $moduleTotal)"
            } else updateNodeInfo()
        }

        model.channels.asLiveData().observe(viewLifecycleOwner) {
            if (!model.isConnected()) it.protobuf.let { ch ->
                val maxChannels = model.myNodeInfo.value?.maxChannels ?: "8"
                if (!ch.hasLoraConfig() && ch.settingsCount > 0)
                    binding.scanStatusText.text = "Channels (${ch.settingsCount} / $maxChannels)"
            }
        }

        // Also watch myNodeInfo because it might change later
        model.myNodeInfo.observe(viewLifecycleOwner) {
            updateNodeInfo()
        }

        scanModel.devices.observe(viewLifecycleOwner) { devices ->
            updateDevicesButtons(devices)
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

        binding.usernameEditText.on(EditorInfo.IME_ACTION_DONE) {
            debug("did IME action")
            val n = binding.usernameEditText.text.toString().trim()
            if (n.isNotEmpty())
                model.setOwner(n)
            requireActivity().hideKeyboard()
        }

        // Observe receivingLocationUpdates state and update provideLocationCheckbox
        if (receivingLocationUpdates?.isActive == true) return
        else receivingLocationUpdates = locationRepository.receivingLocationUpdates
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { binding.provideLocationCheckbox.isChecked = it }
            .launchIn(lifecycleScope)

        binding.provideLocationCheckbox.setOnCheckedChangeListener { view, isChecked ->
            // Don't check the box until the system setting changes
            view.isChecked = isChecked && myActivity.hasBackgroundPermission()

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
                            if (!myActivity.hasLocationPermission()) {
                                requestLocationAndBackgroundLauncher.launch(myActivity.getLocationPermissions())
                            } else {
                                requestBackgroundAndCheckLauncher.launch(myActivity.getBackgroundPermissions())
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
                    Toast.makeText(requireContext(), "Bug report sent!", Toast.LENGTH_LONG).show()
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

            b.isChecked =
                scanModel.onSelected(myActivity, device)
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

        // get rid of the warning text once at least one device is paired.
        // If we are running on an emulator, always leave this message showing so we can test the worst case layout
        val curRadio = scanModel.selectedAddress

        if (curRadio != null && !MockInterface.addressValid(requireContext(), usbRepository, "")) {
            binding.warningNotPaired.visibility = View.GONE
            // binding.scanStatusText.text = getString(R.string.current_pair).format(curRadio)
        } else if (bluetoothViewModel.enabled.value == true){
            binding.warningNotPaired.visibility = View.VISIBLE
            binding.scanStatusText.text = getString(R.string.not_paired_yet)
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
            scanModel.startScan()
        } else {
            scanning = false
            scanModel.stopScan()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initCommonUI()

        val requestPermissionAndScanLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    checkBTEnabled()
                    if (!scanModel.hasCompanionDeviceApi) checkLocationEnabled()
                    scanLeDevice()
                } else {
                    errormsg("User denied scan permissions")
                    showSnackbar(requireContext().permissionMissing)
                }
                bluetoothViewModel.permissionsUpdated()
            }

        binding.changeRadioButton.setOnClickListener {
            debug("User clicked changeRadioButton")
            scanLeDevice()
            if (scanModel.hasBluetoothPermission) {
                checkBTEnabled()
                if (!scanModel.hasCompanionDeviceApi) checkLocationEnabled()
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.required_permissions))
                    .setMessage(requireContext().permissionMissing)
                    .setNeutralButton(R.string.cancel) { _, _ ->
                        warn("User bailed due to permissions")
                    }
                    .setPositiveButton(R.string.accept) { _, _ ->
                        info("requesting scan permissions")
                        requestPermissionAndScanLauncher.launch(myActivity.getBluetoothPermissions())
                    }
                    .show()
            }
        }
    }

    // If the user has not turned on location access throw up a warning
    private fun checkLocationEnabled(
        // Default warning valid only for classic bluetooth scan
        warningReason: String = getString(R.string.location_disabled_warning)
    ) {
        if (requireContext().gpsDisabled()) {
            warn("Telling user we need need location access")
            showSnackbar(warningReason)
        }
    }

    private fun checkBTEnabled(
        warningReason: String = getString(R.string.bluetooth_disabled)
    ) {
        if (bluetoothViewModel.enabled.value == false) {
            warn("Telling user bluetooth is disabled")
            Toast.makeText(requireContext(), warningReason, Toast.LENGTH_LONG).show()
        }
    }

    private val updateProgressFilter = IntentFilter(SoftwareUpdateService.ACTION_UPDATE_PROGRESS)

    private val updateProgressReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshUpdateButton(true)
        }
    }

    private fun showSnackbar(msg: String) {
        if (isAdded) {
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                .apply { view.findViewById<TextView>(R.id.snackbar_text).isSingleLine = false }
                .setAction(R.string.okay) {
                    // dismiss
                }
                .show()
        }
    }

    override fun onPause() {
        super.onPause()

        requireActivity().unregisterReceiver(updateProgressReceiver)
    }

    override fun onResume() {
        super.onResume()

        scanModel.setupScan()

        // system permissions might have changed while we were away
        binding.provideLocationCheckbox.isChecked = myActivity.hasBackgroundPermission() && (model.provideLocation.value ?: false)

        myActivity.registerReceiver(updateProgressReceiver, updateProgressFilter)

        // Warn user if BLE device is selected but BLE disabled
        if (scanModel.selectedBluetooth) checkBTEnabled()

        // Warn user if provide location is selected but location disabled
        if (binding.provideLocationCheckbox.isChecked)
            checkLocationEnabled(getString(R.string.location_disabled))
    }
    companion object {
        const val SCAN_PERIOD: Long = 10000 // Stops scanning after 10 seconds
    }
}
