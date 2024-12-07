/*
 * Copyright (c) 2024 Meshtastic LLC
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
            val onConnect: () -> Unit = { super.connect() }
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