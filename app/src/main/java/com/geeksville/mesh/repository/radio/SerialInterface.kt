package com.geeksville.mesh.repository.radio

import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.repository.usb.SerialConnection
import com.geeksville.mesh.repository.usb.SerialConnectionListener
import com.geeksville.mesh.repository.usb.UsbRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.atomic.AtomicReference

/**
 * An interface that assumes we are talking to a meshtastic device via USB serial
 */
class SerialInterface @AssistedInject constructor(
    service: RadioInterfaceService,
    private val serialInterfaceSpec: SerialInterfaceSpec,
    private val usbRepository: UsbRepository,
    @Assisted private val address: String,
) : StreamInterface(service), Logging {
    private var connRef = AtomicReference<SerialConnection?>()

    init {
        connect()
    }

    override fun onDeviceDisconnect(waitForStopped: Boolean) {
        connRef.get()?.close(waitForStopped)
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        val device = serialInterfaceSpec.findSerial(address)
        if (device == null) {
            errormsg("Can't find device")
        } else {
            info("Opening $device")
            val onConnect: () -> Unit = {  super.connect() }
            usbRepository.createSerialConnection(device, object : SerialConnectionListener {
                override fun onMissingPermission() {
                    errormsg("Need permissions for port")
                }

                override fun onConnected() {
                    onConnect.invoke()
                }

                override fun onDataReceived(bytes: ByteArray) {
                    debug("Received ${bytes.size} byte(s)")
                    bytes.forEach(::readChar)
                }

                override fun onDisconnected(thrown: Exception?) {
                    thrown?.let { e ->
                        errormsg("Serial error: $e")
                    }
                    debug("$device disconnected")
                    onDeviceDisconnect(false)
                }
            }).also { conn ->
                connRef.set(conn)
                conn.connect()
            }
        }
    }

    override fun sendBytes(p: ByteArray) {
        connRef.get()?.sendBytes(p)
    }
}