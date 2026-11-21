/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.core.data.manager

import org.koin.core.annotation.Single
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.radio.DesktopSdkTransportFactory
import org.meshtastic.core.network.transport.parseTcpRadioAddress
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.sdk.transport.ble.BleTransport
import org.meshtastic.sdk.transport.serial.JvmSerialPorts
import org.meshtastic.sdk.transport.tcp.TcpTransport

@Single(binds = [DesktopSdkTransportFactory::class])
internal class SdkDesktopRadioTransportFactory(private val sessions: SdkPhoneApiSessions) : DesktopSdkTransportFactory {
    override fun create(interfaceId: InterfaceId, endpoint: String, service: RadioInterfaceService): RadioTransport =
        when (interfaceId) {
            InterfaceId.TCP ->
                sessions.open(service) {
                    val (host, port) = parseTcpRadioAddress(endpoint)
                    TcpTransport(host, port)
                }

            InterfaceId.WEBSOCKET -> sessions.webSocket(endpoint, service)

            InterfaceId.SERIAL -> sessions.open(service) { JvmSerialPorts.open(endpoint) }

            InterfaceId.BLUETOOTH -> sessions.open(service) { BleTransport(address = endpoint) }

            else -> error("Unsupported SDK desktop transport: $interfaceId")
        }
}
