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
import org.meshtastic.core.network.radio.AndroidSdkTcpTransportFactory
import org.meshtastic.core.network.transport.parseTcpRadioAddress
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.sdk.transport.tcp.TcpTransport

@Single(binds = [AndroidSdkTcpTransportFactory::class])
internal class SdkTcpRadioTransportFactory(
    private val configManager: Lazy<MeshConfigFlowManagerImpl>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
    private val nodeManager: Lazy<NodeManager>,
) : AndroidSdkTcpTransportFactory {
    override fun create(address: String, service: RadioInterfaceService): RadioTransport {
        val (host, port) = parseTcpRadioAddress(address)
        return sdkPhoneApiTransport(
            TcpTransport(host, port),
            service,
            configManager,
            connectionManager,
            messageProcessor,
            nodeManager,
        )
    }
}
