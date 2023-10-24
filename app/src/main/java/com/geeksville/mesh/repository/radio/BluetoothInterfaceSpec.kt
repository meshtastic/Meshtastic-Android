package com.geeksville.mesh.repository.radio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.util.anonymize
import javax.inject.Inject

/**
 * Bluetooth backend implementation.
 */
class BluetoothInterfaceSpec @Inject constructor(
    private val factory: BluetoothInterfaceFactory,
    private val bluetoothAdapter: dagger.Lazy<BluetoothAdapter?>

): InterfaceSpec<BluetoothInterface>, Logging {
    override fun createInterface(rest: String): BluetoothInterface {
        return factory.create(rest)
    }

    /** Return true if this address is still acceptable. For BLE that means, still bonded */
    @SuppressLint("MissingPermission")
    override fun addressValid(rest: String): Boolean {
        val allPaired = bluetoothAdapter.get()?.bondedDevices.orEmpty()
            .map { it.address }.toSet()
        return if (!allPaired.contains(rest)) {
            warn("Ignoring stale bond to ${rest.anonymize}")
            false
        } else
            true
    }
}