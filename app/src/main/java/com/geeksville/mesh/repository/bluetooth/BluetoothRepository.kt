package com.geeksville.mesh.repository.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import androidx.annotation.RequiresPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.CoroutineDispatchers
import com.geeksville.mesh.android.hasBluetoothPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for maintaining and updating the state of Bluetooth availability.
 */
@Singleton
class BluetoothRepository @Inject constructor(
    private val application: Application,
    private val bluetoothAdapterLazy: dagger.Lazy<BluetoothAdapter?>,
    private val bluetoothBroadcastReceiverLazy: dagger.Lazy<BluetoothBroadcastReceiver>,
    private val dispatchers: CoroutineDispatchers,
    private val processLifecycle: Lifecycle,
) : Logging {
    private val _state = MutableStateFlow(BluetoothState(
        // Assume we have permission until we get our initial state update to prevent premature
        // notifications to the user.
        hasPermissions = true
    ))
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            updateBluetoothState()
            bluetoothBroadcastReceiverLazy.get().let { receiver ->
                application.registerReceiver(receiver, receiver.intentFilter)
            }
        }
    }

    fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            updateBluetoothState()
        }
    }

    /** @return true for a valid Bluetooth address, false otherwise */
    fun isValid(bleAddress: String): Boolean {
        return BluetoothAdapter.checkBluetoothAddress(bleAddress)
    }

    fun getRemoteDevice(address: String): BluetoothDevice? {
        return bluetoothAdapterLazy.get()
            ?.takeIf { application.hasBluetoothPermission() && isValid(address) }
            ?.getRemoteDevice(address)
    }

    private fun getBluetoothLeScanner(): BluetoothLeScanner? {
        return bluetoothAdapterLazy.get()
            ?.takeIf { application.hasBluetoothPermission() }
            ?.bluetoothLeScanner
    }

    @SuppressLint("MissingPermission")
    fun scan(): Flow<ScanResult> {
        val filter = ScanFilter.Builder()
            // Samsung doesn't seem to filter properly by service so this can't work
            // see https://stackoverflow.com/questions/57981986/altbeacon-android-beacon-library-not-working-after-device-has-screen-off-for-a-s/57995960#57995960
            // and https://stackoverflow.com/a/45590493
            // .setServiceUuid(ParcelUuid(BluetoothInterface.BTM_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return getBluetoothLeScanner()?.scan(listOf(filter), settings)
            ?.filter { it.device.name?.matches(Regex(BLE_NAME_PATTERN)) == true } ?: emptyFlow()
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    fun createBond(device: BluetoothDevice): Flow<Int> = device.createBond(application)

    @SuppressLint("MissingPermission")
    internal suspend fun updateBluetoothState() {
        val newState: BluetoothState = bluetoothAdapterLazy.get()?.takeIf {
            application.hasBluetoothPermission().also { hasPerms ->
                if (!hasPerms) errormsg("Still missing needed bluetooth permissions")
            }
        }?.let { adapter ->
            /// ask the adapter if we have access
            val enabled = adapter.isEnabled
            val bondedDevices = adapter.bondedDevices ?: emptySet()

            BluetoothState(
                hasPermissions = true,
                enabled = enabled,
                bondedDevices = if (!enabled) emptyList()
                else bondedDevices.filter { it.name?.matches(Regex(BLE_NAME_PATTERN)) == true },
            )
        } ?: BluetoothState()

        _state.emit(newState)
        debug("Detected our bluetooth access=$newState")
    }

    companion object {
        const val BLE_NAME_PATTERN = "^.*_([0-9a-fA-F]{4})$"
    }
}
