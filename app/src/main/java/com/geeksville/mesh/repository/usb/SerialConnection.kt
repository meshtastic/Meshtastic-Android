package com.geeksville.mesh.repository.usb

/**
 * USB serial connection.
 */
interface SerialConnection : AutoCloseable {
    /**
     * Called to initiate the serial connection.
     */
    fun connect()

    /**
     * Send data (asynchronously) to the serial device.  If the connection is not presently
     * established then the data provided is ignored / dropped.
     */
    fun sendBytes(bytes: ByteArray)

    /**
     * Close the USB serial connection.
     *
     * @param waitForStopped if true, waits for the connection to terminate before returning
     */
    fun close(waitForStopped: Boolean)

    override fun close()
}