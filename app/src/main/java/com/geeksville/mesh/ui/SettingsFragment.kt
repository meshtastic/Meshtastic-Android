package com.geeksville.mesh.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.ParcelUuid
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.RadioButton
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.geeksville.android.Logging
import com.geeksville.android.hideKeyboard
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.RadioInterfaceService
import com.geeksville.util.exceptionReporter
import kotlinx.android.synthetic.main.settings_fragment.*


class BTScanModel(app: Application) : AndroidViewModel(app), Logging {

    private val context = getApplication<Application>().applicationContext

    init {
        debug("BTScanModel created")
    }

    data class BTScanEntry(val name: String, val macAddress: String, val bonded: Boolean) {
        // val isSelected get() = macAddress == selectedMacAddr
    }

    override fun onCleared() {
        super.onCleared()
        debug("BTScanModel cleared")
    }

    /// Note: may be null on platforms without a bluetooth driver (ie. the emulator)
    val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    var selectedMacAddr: String? = null
    val errorText = object : MutableLiveData<String?>(null) {}


    private var scanner: BluetoothLeScanner? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            val msg = "Unexpected bluetooth scan failure: $errorCode"
            // error code2 seeems to be indicate hung bluetooth stack
            errorText.value = msg
        }

        // For each device that appears in our scan, ask for its GATT, when the gatt arrives,
        // check if it is an eligable device and store it in our list of candidates
        // if that device later disconnects remove it as a candidate
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val addr = result.device.address
            // prevent logspam because weill get get lots of redundant scan results
            val isBonded = result.device.bondState == BluetoothDevice.BOND_BONDED
            val oldDevs = devices.value!!
            val oldEntry = oldDevs[addr]
            if (oldEntry == null || oldEntry.bonded != isBonded) {
                val entry = BTScanEntry(
                    result.device.name,
                    addr,
                    isBonded
                )
                debug("onScanResult ${entry}")

                // If nothing was selected, by default select the first thing we see
                if (selectedMacAddr == null && entry.bonded)
                    changeSelection(context, addr)

                devices.value = oldDevs + Pair(addr, entry) // trigger gui updates
            }
        }
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

    fun startScan() {
        debug("BTScan component active")
        selectedMacAddr = RadioInterfaceService.getBondedDeviceAddress(context)

        if (bluetoothAdapter == null) {
            warn("No bluetooth adapter.  Running under emulation?")

            val testnodes = listOf(
                BTScanEntry("Meshtastic_ab12", "xx", false),
                BTScanEntry("Meshtastic_32ac", "xb", true)
            )

            devices.value = (testnodes.map { it.macAddress to it }).toMap()

            // If nothing was selected, by default select the first thing we see
            if (selectedMacAddr == null)
                changeSelection(context, testnodes.first().macAddress)
        } else {
            /// The following call might return null if the user doesn't have bluetooth access permissions
            val s: BluetoothLeScanner? = bluetoothAdapter.bluetoothLeScanner

            if (s == null) {
                errorText.value =
                    context.getString(R.string.requires_bluetooth)
            } else {
                debug("starting scan")

                // filter and only accept devices that have a sw update service
                val filter =
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(RadioInterfaceService.BTM_SERVICE_UUID))
                        .build()

                val settings =
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                s.startScan(listOf(filter), settings, scanCallback)
                scanner = s
            }
        }
    }

    val devices = object : MutableLiveData<Map<String, BTScanEntry>>(mapOf()) {


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
    fun onSelected(it: BTScanEntry): Boolean {
        // If the device is paired, let user select it, otherwise start the pairing flow
        if (it.bonded) {
            changeSelection(context, it.macAddress)
            return true
        } else {
            info("Starting bonding for $it")

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
                    debug("Received bond state changed $state")
                    context.unregisterReceiver(this)
                    if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_BONDING) {
                        debug("Bonding completed, connecting service")
                        changeSelection(
                            context,
                            it.macAddress
                        )

                        // Force the GUI to redraw
                        devices.value = devices.value
                    }
                }
            }

            val filter = IntentFilter()
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(bondChangedReceiver, filter)

            // We ignore missing BT adapters, because it lets us run on the emulator
            bluetoothAdapter
                ?.getRemoteDevice(it.macAddress)
                ?.createBond()

            return false
        }
    }

    /// Change to a new macaddr selection, updating GUI and radio
    fun changeSelection(context: Context, newAddr: String) {
        info("Changing BT device to $newAddr")
        selectedMacAddr = newAddr
        RadioInterfaceService.setBondedDeviceAddress(context, newAddr)
    }
}


class SettingsFragment : ScreenFragment("Settings"), Logging {

    private val scanModel: BTScanModel by activityViewModels()
    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.settings_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model.ownerName.observe(viewLifecycleOwner, Observer { name ->
            usernameEditText.setText(name)
        })

        usernameEditText.on(EditorInfo.IME_ACTION_DONE) {
            debug("did IME action")
            val n = usernameEditText.text.toString().trim()
            if (n.isNotEmpty())
                model.setOwner(requireContext(), n)

            requireActivity().hideKeyboard()
        }

        analyticsOkayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // FIXME, preserve this in settings
            analyticsOkayCheckbox.isChecked = true // so users will complain and I'll fix the bug
        }

        scanModel.errorText.observe(viewLifecycleOwner, Observer { errMsg ->
            if (errMsg != null) {
                scanStatusText.text = errMsg
            }
        })

        scanModel.devices.observe(viewLifecycleOwner, Observer { devices ->
            // Remove the old radio buttons and repopulate
            deviceRadioGroup.removeAllViews()

            devices.values.forEach { device ->
                val b = RadioButton(requireActivity())
                b.text = device.name
                b.id = View.generateViewId()
                b.isEnabled =
                    true // Now we always want to enable, if the user clicks we'll try to bond device.bonded
                b.isSelected = device.macAddress == scanModel.selectedMacAddr
                deviceRadioGroup.addView(b)

                b.setOnClickListener {
                    b.isChecked = scanModel.onSelected(device)
                }
            }

            val hasBonded = RadioInterfaceService.getBondedDeviceAddress(requireContext()) != null

            // get rid of the warning text once at least one device is paired
            warningNotPaired.visibility = if (hasBonded) View.GONE else View.VISIBLE
        })
    }

    override fun onPause() {
        super.onPause()
        scanModel.stopScan()
    }

    override fun onResume() {
        super.onResume()
        scanModel.startScan()
    }
}

