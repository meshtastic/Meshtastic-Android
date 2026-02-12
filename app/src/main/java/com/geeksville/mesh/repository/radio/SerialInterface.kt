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
package com.geeksville.mesh.repository.radio

import co.touchlab.kermit.Logger
import com.geeksville.mesh.repository.usb.SerialConnection
import com.geeksville.mesh.repository.usb.SerialConnectionListener
import com.geeksville.mesh.repository.usb.UsbRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.meshtastic.core.model.util.nowMillis
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
            Logger.e { "[$address] Serial device not found at address" }
        } else {
            val connectStart = nowMillis
            Logger.i { "[$address] Opening serial device: $device" }

            var packetsReceived = 0
            var bytesReceived = 0L
            var connectionStartTime = 0L

            val onConnect: () -> Unit = {
                connectionStartTime = nowMillis
                val connectionTime = connectionStartTime - connectStart
                Logger.i { "[$address] Serial device connected in ${connectionTime}ms" }
                super.connect()
            }

            usbRepository
                .createSerialConnection(
                    device,
                    object : SerialConnectionListener {
                        override fun onMissingPermission() {
                            Logger.e {
                                "[$address] Serial connection failed - missing USB permissions for device: $device"
                            }
                        }

                        override fun onConnected() {
                            onConnect.invoke()
                        }

                        override fun onDataReceived(bytes: ByteArray) {
                            packetsReceived++
                            bytesReceived += bytes.size
                            Logger.d {
                                "[$address] Serial received packet #$packetsReceived - " +
                                    "${bytes.size} byte(s) (Total RX: $bytesReceived bytes)"
                            }
                            bytes.forEach(::readChar)
                        }

                        override fun onDisconnected(thrown: Exception?) {
                            val uptime =
                                if (connectionStartTime > 0) {
                                    nowMillis - connectionStartTime
                                } else {
                                    0
                                }
                            thrown?.let { e ->
                                Logger.e(e) { "[$address] Serial error after ${uptime}ms: ${e.message}" }
                            }
                            Logger.w {
                                "[$address] Serial device disconnected - " +
                                    "Device: $device, " +
                                    "Uptime: ${uptime}ms, " +
                                    "Packets RX: $packetsReceived ($bytesReceived bytes)"
                            }
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

    override fun keepAlive() {
        Logger.d { "[$address] Serial keepAlive" }
    }

    override fun sendBytes(p: ByteArray) {
        val conn = connRef.get()
        if (conn != null) {
            Logger.d { "[$address] Serial sending ${p.size} bytes" }
            conn.sendBytes(p)
        } else {
            Logger.w { "[$address] Serial connection not available, cannot send ${p.size} bytes" }
        }
    }
}
