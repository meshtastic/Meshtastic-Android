/*
 * Copyright (c) 2025-2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.geeksville.mesh.repository.usb

import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import com.geeksville.mesh.util.ignoreException
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import org.meshtastic.core.model.util.await
import java.nio.BufferOverflowException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

internal class SerialConnectionImpl(
    private val usbManagerLazy: dagger.Lazy<UsbManager?>,
    private val device: UsbSerialDriver,
    private val listener: SerialConnectionListener,
) : SerialConnection {
    private val port = device.ports[0] // Most devices have just one port (port 0)
    private val closedLatch = CountDownLatch(1)
    private val closed = AtomicBoolean(false)
    private val ioRef = AtomicReference<SerialInputOutputManager>()

    @Suppress("TooGenericExceptionCaught")
    override fun sendBytes(bytes: ByteArray) {
        ioRef.get()?.let {
            Logger.d { "writing ${bytes.size} byte(s }" }
            try {
                it.writeAsync(bytes)
            } catch (e: BufferOverflowException) {
                Logger.e(e) { "Buffer overflow while writing to serial port" }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to write to serial port" }
            }
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
                Logger.d { "Waiting for USB manager to stop..." }
                closedLatch.await(1.seconds)
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

        Logger.d { "Starting serial reader thread" }
        val io =
            SerialInputOutputManager(
                port,
                object : SerialInputOutputManager.Listener {
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
                },
            )
                .apply {
                    readTimeout = 200 // To save battery we only timeout ever so often
                    ioRef.set(this)
                }

        io.start()
        listener.onConnected()
    }
}
