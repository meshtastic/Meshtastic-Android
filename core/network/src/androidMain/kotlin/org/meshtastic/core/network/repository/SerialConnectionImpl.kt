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
@file:Suppress("MagicNumber")

package org.meshtastic.core.network.repository

import android.hardware.usb.UsbManager
import co.touchlab.kermit.Logger
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.util.SerialInputOutputManager
import org.meshtastic.core.common.util.ignoreException
import java.nio.BufferOverflowException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class SerialConnectionImpl(
    private val usbManagerLazy: Lazy<UsbManager?>,
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
            Logger.d { "writing ${bytes.size} byte(s)" }
            try {
                it.writeAsync(bytes)
            } catch (e: BufferOverflowException) {
                Logger.w(e) { "Buffer overflow while writing to serial port" }
            } catch (e: Exception) {
                // USB disconnections often cause IOExceptions here; log as warning to avoid Crashlytics noise
                Logger.w(e) { "Failed to write to serial port (likely disconnected)" }
            }
        }
    }

    override fun close(waitForStopped: Boolean) {
        if (closed.compareAndSet(false, true)) {
            ignoreException(silent = true) { ioRef.get()?.stop() }
            ignoreException(silent = true) {
                port.close() // This will cause the reader thread to exit
            }
        }

        // Allow a short amount of time for the manager to quit (so the port can be cleanly closed)
        if (waitForStopped) {
            Logger.d { "Waiting for USB manager to stop..." }
            ignoreException(silent = true) { closedLatch.await(1, TimeUnit.SECONDS) }
        }
    }

    override fun close() {
        close(true)
    }

    override fun connect() {
        // We shouldn't be able to get this far without a USB subsystem so explode if that isn't true
        val usbManager = usbManagerLazy.value!!

        val usbDeviceConnection = usbManager.openDevice(device.device)
        if (usbDeviceConnection == null) {
            listener.onMissingPermission()
            closed.set(true)
            return
        }

        port.open(usbDeviceConnection)
        port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

        // Assert DTR/RTS so native USB-CDC firmware (RAK4631 / nRF52840) recognizes the host as
        // present and starts its serial-side Meshtastic protocol. Empirically, omitting these
        // signals causes the firmware to never respond to WAKE_BYTES, stalling the handshake at
        // Stage 1. Bridge-chip boards (CH340, CP210x, FTDI) tolerate the assertion.
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
                        // Connection is already failing, don't try to set DTR/RTS as it will just throw more
                        // IOExceptions
                        ignoreException(silent = true) { port.close() }
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
