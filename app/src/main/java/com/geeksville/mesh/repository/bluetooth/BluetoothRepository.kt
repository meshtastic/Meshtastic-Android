package com.geeksville.mesh.repository.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.android.Logging
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.android.hasConnectPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for maintaining and updating the state of Bluetooth availability.
 */
@Singleton
class BluetoothRepository @Inject constructor(
    private val application: Application,
    private val bluetoothAdapterLazy: dagger.Lazy<BluetoothAdapter>,
    private val bluetoothStateReceiverLazy: dagger.Lazy<BluetoothStateReceiver>,
    private val dispatchers: CoroutineDispatchers,
    private val processLifecycle: Lifecycle,
) : Logging {
    internal val enabledInternal = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = enabledInternal

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            updateBluetoothEnabled()
            bluetoothStateReceiverLazy.get().let { receiver ->
                application.registerReceiver(receiver, receiver.intentFilter)
            }
        }
    }

    fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            updateBluetoothEnabled()
        }
    }

    private suspend fun updateBluetoothEnabled() {
        if (application.hasConnectPermission()) {
            /// ask the adapter if we have access
            bluetoothAdapterLazy.get()?.let { adapter ->
                enabledInternal.emit(adapter.isEnabled)
            }
        } else
            errormsg("Still missing needed bluetooth permissions")

        debug("Detected our bluetooth access=${enabled.value}")
    }
}