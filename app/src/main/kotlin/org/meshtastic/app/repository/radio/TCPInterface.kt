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
package org.meshtastic.app.repository.radio

import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.network.transport.TcpTransport
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport

/**
 * Android TCP radio interface — thin adapter over the shared [TcpTransport] from `core:network`.
 *
 * Manages the mapping between the Android-specific [StreamInterface]/[RadioTransport] contract and the shared transport
 * layer.
 */
open class TCPInterface(
    service: RadioInterfaceService,
    private val dispatchers: CoroutineDispatchers,
    private val address: String,
) : StreamInterface(service) {

    companion object {
        const val SERVICE_PORT = StreamFrameCodec.DEFAULT_TCP_PORT
    }

    private val transport =
        TcpTransport(
            dispatchers = dispatchers,
            scope = service.serviceScope,
            listener =
            object : TcpTransport.Listener {
                override fun onConnected() {
                    super@TCPInterface.connect()
                }

                override fun onDisconnected() {
                    // Transport already performed teardown; only propagate lifecycle to StreamInterface.
                    super@TCPInterface.onDeviceDisconnect(false)
                }

                override fun onPacketReceived(bytes: ByteArray) {
                    service.handleFromRadio(bytes)
                }
            },
            logTag = "TCPInterface[$address]",
        )

    init {
        connect()
    }

    override fun sendBytes(p: ByteArray) {
        // Direct byte sending is handled by the transport; this is used by StreamInterface for serial compat
        Logger.d { "[$address] TCPInterface.sendBytes delegated to transport" }
    }

    override fun onDeviceDisconnect(waitForStopped: Boolean) {
        transport.stop()
        super.onDeviceDisconnect(waitForStopped)
    }

    override fun connect() {
        transport.start(address)
    }

    override fun keepAlive() {
        Logger.d { "[$address] TCP keepAlive" }
        service.serviceScope.handledLaunch { transport.sendHeartbeat() }
    }

    override fun handleSendToRadio(p: ByteArray) {
        service.serviceScope.handledLaunch { transport.sendPacket(p) }
    }
}
