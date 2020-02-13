package com.geeksville.mesh.ui

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import androidx.compose.Composable
import androidx.compose.Context
import androidx.compose.ambient
import androidx.compose.onActive
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.layout.Column
import androidx.ui.tooling.preview.Preview
import com.geeksville.android.Logging
import com.geeksville.mesh.service.RadioInterfaceService

object BTLog : Logging

@Composable
fun BTScanScreen() {
    val context = ambient(ContextAmbient)

    /// Note: may be null on platforms without a bluetooth driver (ie. the emulator)
    val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter

    onActive {

        if (bluetoothAdapter == null)
            BTLog.warn("No bluetooth adapter.  Running under emulation?")
        else {
            val scanner = bluetoothAdapter.bluetoothLeScanner

            val scanCallback = object : ScanCallback() {
                override fun onScanFailed(errorCode: Int) {
                    TODO() // FIXME, update gui with message about this
                }

                // For each device that appears in our scan, ask for its GATT, when the gatt arrives,
                // check if it is an eligable device and store it in our list of candidates
                // if that device later disconnects remove it as a candidate
                override fun onScanResult(callbackType: Int, result: ScanResult) {

                    BTLog.info("onScanResult ${result.device.address}")

                    // We don't need any more results now
                    // scanner.stopScan(this)
                }
            }

            BTLog.debug("starting scan")

            // filter and only accept devices that have a sw update service
            val filter =
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(RadioInterfaceService.BTM_SERVICE_UUID))
                    .build()

            /* ScanSettings.CALLBACK_TYPE_FIRST_MATCH seems to trigger a bug returning an error of
            SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES (error #5)
             */
            val settings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).
                    // setMatchMode(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).
                    // setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).
                    build()
            scanner.startScan(listOf(filter), settings, scanCallback)

            onDispose {
                BTLog.debug("stopping scan")
                scanner.stopScan(scanCallback)
            }
        }
    }

    Column {
        Text("FIXME")
    }
}


@Preview
@Composable
fun btScanScreenPreview() {
    BTScanScreen()
}