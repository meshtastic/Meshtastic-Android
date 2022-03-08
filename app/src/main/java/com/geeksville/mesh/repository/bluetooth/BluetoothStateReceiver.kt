package com.geeksville.mesh.repository.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.util.exceptionReporter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A helper class to call onChanged when bluetooth is enabled or disabled
 */
class BluetoothStateReceiver @Inject constructor(
    private val dispatchers: CoroutineDispatchers,
    private val bluetoothRepository: BluetoothRepository,
    private val processLifecycle: Lifecycle,
) : BroadcastReceiver() {
    internal val intentFilter get() = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED) // Can be used for registering

    override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            when (intent.bluetoothAdapterState) {
                // Simulate a disconnection if the user disables bluetooth entirely
                BluetoothAdapter.STATE_OFF -> emitState(false)
                BluetoothAdapter.STATE_ON -> emitState(true)
            }
        }
    }

    private fun emitState(newState: Boolean) {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            bluetoothRepository.enabledInternal.emit(newState)
        }
    }

    private val Intent.bluetoothAdapterState: Int
        get() = getIntExtra(BluetoothAdapter.EXTRA_STATE,-1)
}