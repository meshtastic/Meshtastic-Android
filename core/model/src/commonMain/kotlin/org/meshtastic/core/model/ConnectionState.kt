package org.meshtastic.core.model

sealed class ConnectionState {
    /** We are disconnected from the device, and we should be trying to reconnect. */
    data object Disconnected : ConnectionState()

    /** We are currently attempting to connect to the device. */
    data object Connecting : ConnectionState()

    /** We are connected to the device and communicating normally. */
    data object Connected : ConnectionState()

    /** The device is in a light sleep state, and we are waiting for it to wake up and reconnect to us. */
    data object DeviceSleep : ConnectionState()

    fun isConnected() = this == Connected

    fun isConnecting() = this == Connecting

    fun isDisconnected() = this == Disconnected

    fun isDeviceSleep() = this == DeviceSleep
}
