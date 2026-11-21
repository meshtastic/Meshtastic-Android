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
import org.meshtastic.core.network.radio.MockRadioTransport
import org.meshtastic.core.network.radio.ReplayRadioTransport
import org.meshtastic.core.network.radio.VirtualRadioSessionFactory
import org.meshtastic.core.repository.AdminPacketHandler
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.sdk.transport.websocket.WebSocketTransport
import org.meshtastic.sdk.RadioTransport as SdkRadioTransport

@Single(binds = [VirtualRadioSessionFactory::class])
internal class SdkPhoneApiSessions(
    private val configManager: Lazy<MeshConfigFlowManagerImpl>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
    private val nodeManager: Lazy<NodeManager>,
    private val lockdownCoordinator: Lazy<LockdownCoordinator>,
    private val adminPacketHandler: Lazy<AdminPacketHandler>,
) : VirtualRadioSessionFactory {
    fun open(service: RadioInterfaceService, transport: suspend () -> SdkRadioTransport): RadioTransport =
        SdkPhoneApiRadioTransport(
            transport,
            service,
            configManager,
            connectionManager,
            messageProcessor,
            nodeManager,
            lockdownCoordinator,
            adminPacketHandler,
        )

    fun webSocket(url: String, service: RadioInterfaceService): RadioTransport =
        open(service) { WebSocketTransport(url) }

    override fun demo(address: String, service: RadioInterfaceService): RadioTransport =
        open(service) { MockRadioTransport(service.serviceScope, address) }

    override fun replay(address: String, frames: ByteArray, service: RadioInterfaceService): RadioTransport =
        open(service) { ReplayRadioTransport(service.serviceScope, address, frames) }
}
