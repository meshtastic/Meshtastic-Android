package com.geeksville.mesh.repository.bluetooth

import android.bluetooth.BluetoothDevice
import com.geeksville.mesh.util.anonymize

/**
 * A snapshot in time of the state of the bluetooth subsystem.
 */
data class BluetoothState(
    /** Whether we have adequate permissions to query bluetooth state */
    val hasPermissions: Boolean = false,
    /** If we have adequate permissions and bluetooth is enabled */
    val enabled: Boolean = false,
    /** If enabled, a list of the currently bonded devices */
    val bondedDevices: List<BluetoothDevice> = emptyList()
) {
    override fun toString(): String =
        "BluetoothState(hasPermissions=$hasPermissions, enabled=$enabled, bondedDevices=${bondedDevices.map { it.anonymize }})"
}
