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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.network.repository.SerialConnection
import org.meshtastic.core.network.repository.SerialConnectionListener
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.network.transport.HeartbeatSender
import org.meshtastic.core.repository.RadioTransportCallback
import java.util.concurrent.atomic.AtomicReference

/** An Android USB/serial [RadioTransport] implementation. */
class SerialRadioTransport(
    callback: RadioTransportCallback,
    scope: CoroutineScope,
    private val usbRepository: UsbRepository,
    private val address: String,
) : StreamTransport(callback, scope) {
    private var connRef = AtomicReference<SerialConnection?>()

    private val heartbeatSender = HeartbeatSender(sendToRadio = ::handleSendToRadio, logTag = "Serial[$address]")

    override fun start() {
        connect()
    }

    override fun onDeviceDisconnect(waitForStopped: Boolean, isPermanent: Boolean) {
        connRef.get()?.close(waitForStopped)
        super.onDeviceDisconnect(waitForStopped, isPermanent)
    }

    override fun connect() {
        val deviceMap = usbRepository.serialDevices.value
        val device = deviceMap[address] ?: deviceMap.values.firstOrNull()
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
                                // USB errors are common when unplugging; log as warning to avoid Crashlytics noise
                                Logger.w(e) { "[$address] Serial error after ${uptime}ms: ${e.message}" }
                            }
                            Logger.w {
                                "[$address] Serial device disconnected - " +
                                    "Device: $device, " +
                                    "Uptime: ${uptime}ms, " +
                                    "Packets RX: $packetsReceived ($bytesReceived bytes)"
                            }
                            // USB unplug / cable error is transient — the transport will reconnect when
                            // the device is replugged or the OS re-enumerates the port. Only an explicit
                            // close() (user disconnects) should signal a permanent disconnect.
                            onDeviceDisconnect(waitForStopped = false, isPermanent = false)
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
        // Delegate to HeartbeatSender which sends a ToRadio heartbeat to prove the serial
        // link is alive and keep the local node's lastHeard timestamp current.
        scope.handledLaunch { heartbeatSender.sendHeartbeat() }
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
