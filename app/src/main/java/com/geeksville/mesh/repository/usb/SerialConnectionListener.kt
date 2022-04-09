package com.geeksville.mesh.repository.usb

/**
 * Callbacks indicating state changes in the USB serial connection.
 */
interface SerialConnectionListener {
    /**
     * Unable to initiate the connection due to missing permissions.  This is a terminal
     * state.
     */
    fun onMissingPermission() {}

    /**
     * Called when a connection has been established.
     */
    fun onConnected() {}

    /**
     * Called when serial data is received.
     */
    fun onDataReceived(bytes: ByteArray) {}

    /**
     * Called when the connection has been terminated.
     */
    fun onDisconnected(thrown: Exception?) {}
}