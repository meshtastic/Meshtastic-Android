package com.geeksville.mesh.service

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.geeksville.util.exceptionReporter

/**
 * A helper class to call onChanged when bluetooth is enabled or disabled
 */
class BluetoothStateReceiver(
    private val onChanged: (Boolean) -> Unit
) : BroadcastReceiver() {

    val intentFilter get() = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED) // Can be used for registering

    override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            when (intent.bluetoothAdapterState) {
                // Simulate a disconnection if the user disables bluetooth entirely
                BluetoothAdapter.STATE_OFF -> onChanged(false)
                BluetoothAdapter.STATE_ON -> onChanged(true)
            }
        }
    }

    private val Intent.bluetoothAdapterState: Int
        get() = getIntExtra(
            BluetoothAdapter.EXTRA_STATE,
            -1
        )
}