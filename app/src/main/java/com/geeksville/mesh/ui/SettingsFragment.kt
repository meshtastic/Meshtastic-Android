package com.geeksville.mesh.ui

import android.net.InetAddresses
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.*
import com.geeksville.mesh.databinding.SettingsFragmentBinding
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.repository.location.LocationRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.util.exceptionToSnackbar
import com.geeksville.mesh.util.onEditorAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
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

    private val hasGps by lazy { requireContext().hasGps() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
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

        val requestLocationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                if (permissions.entries.all { it.value }) {
                    model.provideLocation.value = true
                    model.meshService?.startProvideLocation()
                } else {
                    debug("User denied location permission")
                    model.showSnackbar(getString(R.string.why_background_required))
                }
                bluetoothViewModel.permissionsUpdated()
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
            if (model.isConnected()) updateNodeInfo()
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

        var scanDialog: AlertDialog? = null
        scanModel.scanResult.observe(viewLifecycleOwner) { results ->
            val devices = results.values.ifEmpty { return@observe }
            scanDialog?.dismiss()
            scanDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Select a Bluetooth device")
                .setSingleChoiceItems(
                    devices.map { it.name }.toTypedArray(),
                    -1
                ) { dialog, position ->
                    val selectedDevice = devices.elementAt(position)
                    scanModel.onSelected(selectedDevice)
                    scanModel.clearScanResults()
                    dialog.dismiss()
                    scanDialog = null
                }
                .setPositiveButton(R.string.cancel) { dialog, _ ->
                    scanModel.clearScanResults()
                    dialog.dismiss()
                    scanDialog = null
                }
                .show()
        }

        // show the spinner when [spinner] is true
        scanModel.spinner.observe(viewLifecycleOwner) { show ->
            binding.changeRadioButton.isEnabled = !show
            binding.scanProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        }

        binding.usernameEditText.onEditorAction(EditorInfo.IME_ACTION_DONE) {
            debug("received IME_ACTION_DONE")
            val n = binding.usernameEditText.text.toString().trim()
            if (n.isNotEmpty()) model.setOwner(n)
            requireActivity().hideKeyboard()
        }

        // Observe receivingLocationUpdates state and update provideLocationCheckbox
        locationRepository.receivingLocationUpdates.asLiveData().observe(viewLifecycleOwner) {
            binding.provideLocationCheckbox.isChecked = it
        }

        binding.provideLocationCheckbox.setOnCheckedChangeListener { view, isChecked ->
            // Don't check the box until the system setting changes
            view.isChecked = isChecked && requireContext().hasLocationPermission()

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
                                requestLocationPermissionLauncher.launch(requireContext().getLocationPermissions())
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
        val isGooglePlayAvailable = requireContext().isGooglePlayAvailable()
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
        binding.reportBugButton.setOnClickListener(::showReportBugDialog)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun showReportBugDialog(view: View) {
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

    private var tapCount = 0
    private var lastTapTime: Long = 0

    private fun addDeviceButton(device: BTScanModel.DeviceListEntry, enabled: Boolean) {
        val b = RadioButton(requireActivity())
        b.text = device.name
        b.id = View.generateViewId()
        b.isEnabled = enabled
        b.isChecked = device.fullAddress == scanModel.selectedNotNull
        binding.deviceRadioGroup.addView(b)

        b.setOnClickListener {
            if (device.fullAddress == "n") {
                val currentTapTime = System.currentTimeMillis()
                if (currentTapTime - lastTapTime > TAP_THRESHOLD) {
                    tapCount = 0
                }
                lastTapTime = currentTapTime
                tapCount++

                if (tapCount >= TAP_TRIGGER) {
                    model.showSnackbar("Demo Mode enabled")
                    scanModel.showMockInterface()
                }
            }
            if (!device.bonded) // If user just clicked on us, try to bond
                binding.scanStatusText.setText(R.string.starting_pairing)
            b.isChecked = scanModel.onSelected(device)
        }
    }

    private fun addManualDeviceButton() {
        val deviceSelectIPAddress = binding.radioButtonManual
        val inputIPAddress = binding.editManualAddress

        deviceSelectIPAddress.isEnabled = inputIPAddress.text.isIPAddress()
        deviceSelectIPAddress.setOnClickListener {
            deviceSelectIPAddress.isChecked = scanModel.onSelected(BTScanModel.DeviceListEntry("", "t" + inputIPAddress.text, true))
        }

        binding.deviceRadioGroup.addView(deviceSelectIPAddress)
        binding.deviceRadioGroup.addView(inputIPAddress)

        inputIPAddress.doAfterTextChanged {
            deviceSelectIPAddress.isEnabled = inputIPAddress.text.isIPAddress()
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
                val curDevice = BTScanModel.DeviceListEntry(curAddr.substring(1), curAddr, false)
                addDeviceButton(curDevice, model.isConnected())
            }
        }

        addManualDeviceButton()

        // get rid of the warning text once at least one device is paired.
        // If we are running on an emulator, always leave this message showing so we can test the worst case layout
        val curRadio = scanModel.selectedAddress

        if (curRadio != null && curRadio != "m") {
            binding.warningNotPaired.visibility = View.GONE
        } else if (bluetoothViewModel.enabled.value == true) {
            binding.warningNotPaired.visibility = View.VISIBLE
            scanModel.setErrorText(getString(R.string.not_paired_yet))
        }
    }

    // per https://developer.android.com/guide/topics/connectivity/bluetooth/find-ble-devices
    private var scanning = false
    private fun scanLeDevice() {
        if (!checkBTEnabled()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) checkLocationEnabled()

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
                    info("Bluetooth permissions granted")
                    scanLeDevice()
                } else {
                    warn("Bluetooth permissions denied")
                    model.showSnackbar(requireContext().permissionMissing)
                }
                bluetoothViewModel.permissionsUpdated()
            }

        binding.changeRadioButton.setOnClickListener {
            debug("User clicked changeRadioButton")
            val bluetoothPermissions = requireContext().getBluetoothPermissions()
            if (bluetoothPermissions.isEmpty()) {
                scanLeDevice()
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

    private fun checkBTEnabled(): Boolean = (bluetoothViewModel.enabled.value == true).also { enabled ->
        if (!enabled) {
            warn("Telling user bluetooth is disabled")
            model.showSnackbar(R.string.bluetooth_disabled)
        }
    }

    override fun onResume() {
        super.onResume()

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
        private const val TAP_TRIGGER: Int = 7
        private const val TAP_THRESHOLD: Long = 500 // max 500 ms between taps

    }

    private fun Editable.isIPAddress(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            InetAddresses.isNumericAddress(this.toString())
        } else {
            @Suppress("DEPRECATION")
            Patterns.IP_ADDRESS.matcher(this).matches()
        }
    }

}
