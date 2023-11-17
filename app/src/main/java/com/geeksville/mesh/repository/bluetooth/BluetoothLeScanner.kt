package com.geeksville.mesh.repository.bluetooth

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@RequiresPermission("android.permission.BLUETOOTH_SCAN")
internal fun BluetoothLeScanner.scan(
    filters: List<ScanFilter> = emptyList(),
    scanSettings: ScanSettings = ScanSettings.Builder().build(),
): Flow<ScanResult> = callbackFlow {
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            trySend(result)
        }

        override fun onScanFailed(errorCode: Int) {
            cancel("onScanFailed() called with errorCode: $errorCode")
        }
    }
    startScan(filters, scanSettings, callback)

    awaitClose { stopScan(callback) }
}
