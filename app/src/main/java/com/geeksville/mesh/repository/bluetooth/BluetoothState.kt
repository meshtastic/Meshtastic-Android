package com.geeksville.mesh.repository.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * A snapshot in time of the state of the bluetooth subsystem.
 */
data class BluetoothState(
    /** Whether we have adequate permissions to query bluetooth state */
    val hasPermissions: Boolean = false,
    /** If we have adequate permissions and bluetooth is enabled */
    val enabled: Boolean = false,
    /** If enabled, a cold flow of the currently bonded devices */
    val bondedDevices: Flow<Set<BluetoothDevice>> = flowOf(emptySet())
)
