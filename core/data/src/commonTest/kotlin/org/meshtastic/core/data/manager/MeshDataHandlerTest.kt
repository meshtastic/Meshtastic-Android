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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.mock
import org.meshtastic.core.model.util.MeshDataMapper
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.MeshPacket
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class MeshDataHandlerTest {

    private lateinit var handler: MeshDataHandlerImpl
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val serviceRepository: ServiceRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val serviceBroadcasts: ServiceBroadcasts = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val serviceNotifications: MeshServiceNotifications = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)
    private val dataMapper: MeshDataMapper = mock(MockMode.autofill)
    private val configHandler: MeshConfigHandler = mock(MockMode.autofill)
    private val configFlowManager: MeshConfigFlowManager = mock(MockMode.autofill)
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val historyManager: HistoryManager = mock(MockMode.autofill)
    private val connectionManager: MeshConnectionManager = mock(MockMode.autofill)
    private val tracerouteHandler: TracerouteHandler = mock(MockMode.autofill)
    private val neighborInfoHandler: NeighborInfoHandler = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val messageFilter: MessageFilter = mock(MockMode.autofill)

    @BeforeTest
    fun setUp() {
        handler =
            MeshDataHandlerImpl(
                nodeManager = nodeManager,
                packetHandler = packetHandler,
                serviceRepository = serviceRepository,
                packetRepository = lazy { packetRepository },
                serviceBroadcasts = serviceBroadcasts,
                notificationManager = notificationManager,
                serviceNotifications = serviceNotifications,
                analytics = analytics,
                dataMapper = dataMapper,
                configHandler = lazy { configHandler },
                configFlowManager = lazy { configFlowManager },
                commandSender = commandSender,
                historyManager = historyManager,
                connectionManager = lazy { connectionManager },
                tracerouteHandler = tracerouteHandler,
                neighborInfoHandler = neighborInfoHandler,
                radioConfigRepository = radioConfigRepository,
                messageFilter = messageFilter,
            )
    }

    @Test
    fun testInitialization() {
        assertNotNull(handler)
    }

    @Test
    fun `handleReceivedData processes packet`() {
        val packet = MeshPacket()
        handler.handleReceivedData(packet, 123)
    }
}
