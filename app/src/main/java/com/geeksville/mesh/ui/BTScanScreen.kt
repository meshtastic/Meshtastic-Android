package com.geeksville.mesh.ui

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.compose.*
import androidx.compose.frames.modelMapOf
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.layout.Column
import androidx.ui.material.CircularProgressIndicator
import androidx.ui.material.RadioGroup
import androidx.ui.tooling.preview.Preview
import com.geeksville.android.Logging
import com.geeksville.mesh.service.RadioInterfaceService

object BTLog : Logging


@Model
object ScanState {
    var selectedMacAddr: String? = null
    var errorText: String? = null
}

@Model
data class BTScanEntry(val name: String, val macAddress: String) {
    val isSelected get() = macAddress == ScanState.selectedMacAddr
}


@Composable
fun BTScanScreen() {
    val context = ambient(ContextAmbient)

    /// Note: may be null on platforms without a bluetooth driver (ie. the emulator)
    val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    val devices = modelMapOf<String, BTScanEntry>()

    ScanState.selectedMacAddr = RadioInterfaceService.getBondedDeviceAddress(context)

    fun changeSelection(newAddr: String) {
        ScanState.selectedMacAddr = newAddr
        RadioInterfaceService.setBondedDeviceAddress(context, newAddr)
    }

    onActive {

        if (bluetoothAdapter == null) {
            BTLog.warn("No bluetooth adapter.  Running under emulation?")

            val testnodes = listOf(
                BTScanEntry("Meshtastic_ab12", "xx"),
                BTScanEntry("Meshtastic_32ac", "xb")
            )

            devices.putAll(testnodes.map { it.macAddress to it })

            // If nothing was selected, by default select the first thing we see
            if (ScanState.selectedMacAddr == null)
                changeSelection(testnodes.first().macAddress)
        } else {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            // ScanState.scanner = scanner

            val scanCallback = object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    val msg = "Unexpected bluetooth scan failure: $errorCode"
                    ScanState.errorText = msg
                    BTLog.reportError(msg)
                }

                // For each device that appears in our scan, ask for its GATT, when the gatt arrives,
                // check if it is an eligable device and store it in our list of candidates
                // if that device later disconnects remove it as a candidate
                override fun onScanResult(callbackType: Int, result: ScanResult) {

                    val addr = result.device.address
                    BTLog.debug("onScanResult ${addr}")
                    devices[addr] =
                        BTScanEntry(result.device.name, addr)

                    // If nothing was selected, by default select the first thing we see
                    if (ScanState.selectedMacAddr == null)
                        changeSelection(addr)
                }
            }

            BTLog.debug("starting scan")

            // filter and only accept devices that have a sw update service
            val filter =
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(RadioInterfaceService.BTM_SERVICE_UUID))
                    .build()

            val settings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            scanner.startScan(listOf(filter), settings, scanCallback)

            onDispose {
                BTLog.debug("stopping scan")
                scanner.stopScan(scanCallback)
            }
        }
    }



    Column {
        if (ScanState.errorText != null) {
            Text("An unexpected error was encountered.  Please file a bug on our github: ${ScanState.errorText}")
        } else {
            if (devices.isEmpty())
                Text("Looking for Meshtastic devices... (zero found)")
            else {
                val allPaired = bluetoothAdapter?.bondedDevices.orEmpty().map { it.address }

                // Only let user select paired devices
                val paired = devices.values.filter { allPaired.contains(it.macAddress) }
                if (paired.size < devices.size) {
                    Text(
                        "Warning: there are nearby Meshtastic devices that are not paired with this phone.  Before you can select a device, you will need to pair it in Bluetooth Settings."
                    )
                }

                RadioGroup {
                    Column {
                        paired.forEach {
                            // disabled pending https://issuetracker.google.com/issues/149528535
                            //ProvideEmphasis(emphasis = if (allPaired.contains(it.macAddress)) EmphasisLevels().medium else EmphasisLevels().disabled) {
                            RadioGroupTextItem(
                                selected = (it.isSelected),
                                onSelect = { changeSelection(it.macAddress) },
                                text = it.name
                            )
                            //}
                        }
                    }
                }
            }

            CircularProgressIndicator() // Show that we are searching still
        }
    }
}


@Preview
@Composable
fun btScanScreenPreview() {
    BTScanScreen()
}