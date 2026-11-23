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
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.sdk.transport.websocket.WebSocketTransport
import org.meshtastic.sdk.RadioTransport as SdkRadioTransport

@Single(binds = [VirtualRadioSessionFactory::class])
internal class SdkPhoneApiSessions(
    private val projection: Lazy<RadioSessionProjection>,
    private val connectionManager: Lazy<MeshConnectionManager>,
    private val lockdownCoordinator: Lazy<LockdownCoordinator>,
) : VirtualRadioSessionFactory {
    /** Per URL, because each retry builds a fresh SDK transport. The service creates transports under one lock. */
    private val webSocketHistories = mutableMapOf<String, WebSocketSessionHistory>()

    fun open(
        service: RadioInterfaceService,
        webSocketHistory: WebSocketSessionHistory? = null,
        transport: suspend () -> SdkRadioTransport,
    ): RadioTransport = SdkPhoneApiRadioTransport(
        transport,
        service,
        projection,
        connectionManager,
        lockdownCoordinator,
        webSocketHistory,
    )

    fun webSocket(url: String, service: RadioInterfaceService): RadioTransport {
        val history = webSocketHistories.getOrPut(url) { WebSocketSessionHistory(url) }
        return open(service, history) {
            history.beforeConnect()
            WebSocketTransport(url)
        }
    }

    override fun demo(address: String, service: RadioInterfaceService): RadioTransport =
        open(service) { MockRadioTransport(service.serviceScope, address) }

    override fun replay(address: String, frames: ByteArray, service: RadioInterfaceService): RadioTransport =
        open(service) { ReplayRadioTransport(service.serviceScope, address, frames) }
}
