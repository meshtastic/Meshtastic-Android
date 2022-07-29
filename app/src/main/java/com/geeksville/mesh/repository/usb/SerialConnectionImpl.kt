package com.geeksville.mesh.repository.usb

import android.hardware.usb.UsbManager
import com.geeksville.android.Logging
import com.geeksville.util.ignoreException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class SerialConnectionImpl(
    private val usbManagerLazy: dagger.Lazy<UsbManager?>,
    private val device: UsbSerialDriver,
    private val listener: SerialConnectionListener
) : SerialConnection, Logging {
    private val port = device.ports[0]  // Most devices have just one port (port 0)
    private val closedLatch = CountDownLatch(1)
    private val closed = AtomicBoolean(false)
    private val ioRef = AtomicReference<SerialInputOutputManager>()

    override fun sendBytes(bytes: ByteArray) {
        ioRef.get()?.let {
            debug("writing ${bytes.size} byte(s)")
            it.writeAsync(bytes)
        }
    }

    override fun close(waitForStopped: Boolean) {
        ignoreException {
            if (closed.compareAndSet(false, true)) {
                ioRef.get()?.stop()
                port.close() // This will cause the reader thread to exit
            }

            // Allow a short amount of time for the manager to quit (so the port can be cleanly closed)
            if (waitForStopped) {
                debug("Waiting for USB manager to stop...")
                closedLatch.await(1, TimeUnit.SECONDS)
            }
        }
    }

    override fun close() {
        close(true)
    }

    override fun connect() {
        // We shouldn't be able to get this far without a USB subsystem so explode if that isn't true
        val usbManager = usbManagerLazy.get()!!

        val usbDeviceConnection = usbManager.openDevice(device.device)
        if (usbDeviceConnection == null) {
            listener.onMissingPermission()
            closed.set(true)
            return
        }

        port.open(usbDeviceConnection)
        port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port.dtr = true
        port.rts = true

        debug("Starting serial reader thread")
        val io = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                listener.onDataReceived(data)
            }

            override fun onRunError(e: Exception?) {
                closed.set(true)
                ignoreException {
                    port.dtr = false
                    port.rts = false
                    port.close()
                }
                closedLatch.countDown()
                listener.onDisconnected(e)
            }
        }).apply {
            readTimeout = 200  // To save battery we only timeout ever so often
            ioRef.set(this)
        }

        Thread(io).apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            name = "serial reader"
        }.start() // No need to keep reference to thread around, we quit by asking the ioManager to quit

        listener.onConnected()
    }
}