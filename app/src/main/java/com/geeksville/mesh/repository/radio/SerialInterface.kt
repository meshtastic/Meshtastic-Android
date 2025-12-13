/*
 * Copyright (c) 2025 Meshtastic LLC
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

import com.geeksville.mesh.repository.usb.SerialConnection
import com.geeksville.mesh.repository.usb.SerialConnectionListener
import com.geeksville.mesh.repository.usb.UsbRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/** An interface that assumes we are talking to a meshtastic device via USB serial */
class SerialInterface
@AssistedInject
constructor(
    service: RadioInterfaceService,
    private val serialInterfaceSpec: SerialInterfaceSpec,
    private val usbRepository: UsbRepository,
    @Assisted private val address: String,
) : StreamInterface(service) {
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
            Timber.e("[$address] Serial device not found at address")
        } else {
            val connectStart = System.currentTimeMillis()
            Timber.i("[$address] Opening serial device: $device")

            var packetsReceived = 0
            var bytesReceived = 0L
            var connectionStartTime = 0L

            val onConnect: () -> Unit = {
                connectionStartTime = System.currentTimeMillis()
                val connectionTime = connectionStartTime - connectStart
                Timber.i("[$address] Serial device connected in ${connectionTime}ms")
                super.connect()
            }

            usbRepository
                .createSerialConnection(
                    device,
                    object : SerialConnectionListener {
                        override fun onMissingPermission() {
                            Timber.e(
                                "[$address] Serial connection failed - missing USB permissions for device: $device",
                            )
                        }

                        override fun onConnected() {
                            onConnect.invoke()
                        }

                        override fun onDataReceived(bytes: ByteArray) {
                            packetsReceived++
                            bytesReceived += bytes.size
                            Timber.d(
                                "[$address] Serial received packet #$packetsReceived - " +
                                    "${bytes.size} byte(s) (Total RX: $bytesReceived bytes)",
                            )
                            bytes.forEach(::readChar)
                        }

                        override fun onDisconnected(thrown: Exception?) {
                            val uptime =
                                if (connectionStartTime > 0) {
                                    System.currentTimeMillis() - connectionStartTime
                                } else {
                                    0
                                }
                            thrown?.let { e -> Timber.e(e, "[$address] Serial error after ${uptime}ms: ${e.message}") }
                            Timber.w(
                                "[$address] Serial device disconnected - " +
                                    "Device: $device, " +
                                    "Uptime: ${uptime}ms, " +
                                    "Packets RX: $packetsReceived ($bytesReceived bytes)",
                            )
                            onDeviceDisconnect(false)
                        }
                    },
                )
                .also { conn ->
                    connRef.set(conn)
                    conn.connect()
                }
        }
    }

    override fun sendBytes(p: ByteArray) {
        val conn = connRef.get()
        if (conn != null) {
            Timber.d("[$address] Serial sending ${p.size} bytes")
            conn.sendBytes(p)
        } else {
            Timber.w("[$address] Serial connection not available, cannot send ${p.size} bytes")
        }
    }
}
