package com.geeksville.mesh.repository.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import com.geeksville.mesh.util.registerReceiverCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@RequiresPermission("android.permission.BLUETOOTH_CONNECT")
internal fun BluetoothDevice.createBond(context: Context): Flow<Int> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            trySend(state)

            // we stay registered until bonding completes (either with BONDED or NONE)
            if (state != BluetoothDevice.BOND_BONDING) {
                close()
            }
        }
    }
    val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    context.registerReceiverCompat(receiver, filter)
    createBond()

    awaitClose { context.unregisterReceiver(receiver) }
}
