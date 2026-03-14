package org.meshtastic.core.ble

import com.juul.kable.State

/**
 * Maps Kable's [State] to Meshtastic's [BleConnectionState].
 * 
 * @param hasStartedConnecting whether we have seen a Connecting state.
 * This is used to ignore the initial Disconnected state emitted by StateFlow upon subscription.
 * @return the mapped [BleConnectionState], or null if the state should be ignored.
 */
fun State.toBleConnectionState(hasStartedConnecting: Boolean): BleConnectionState? {
    return when (this) {
        is State.Connecting -> BleConnectionState.Connecting
        is State.Connected -> BleConnectionState.Connected
        is State.Disconnecting -> BleConnectionState.Disconnecting
        is State.Disconnected -> {
            if (!hasStartedConnecting) return null
            BleConnectionState.Disconnected
        }
    }
}
