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
package org.meshtastic.core.service

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DirectRadioControllerImplTest {

    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val router: MeshRouter = mock(MockMode.autofill)
    private val actionHandler: MeshActionHandler = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val locationManager: MeshLocationManager = mock(MockMode.autofill)

    private fun createController(
        serviceRepository: ServiceRepository = ServiceRepositoryImpl(),
        myNodeNum: Int? = 1234,
    ): DirectRadioControllerImpl {
        every { router.actionHandler } returns actionHandler
        every { nodeManager.myNodeNum } returns MutableStateFlow(myNodeNum)
        return DirectRadioControllerImpl(
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            commandSender = commandSender,
            router = router,
            nodeManager = nodeManager,
            radioInterfaceService = radioInterfaceService,
            locationManager = locationManager,
        )
    }

    @Test
    fun connectionStateAndClientNotificationDelegateToServiceRepository() {
        val serviceRepository = ServiceRepositoryImpl()
        val controller = createController(serviceRepository = serviceRepository)
        val notification = ClientNotification()

        assertSame(serviceRepository.connectionState, controller.connectionState)
        assertSame(serviceRepository.clientNotification, controller.clientNotification)

        serviceRepository.setConnectionState(ConnectionState.Connecting)
        serviceRepository.setClientNotification(notification)

        assertEquals(ConnectionState.Connecting, controller.connectionState.value)
        assertSame(notification, controller.clientNotification.value)

        controller.clearClientNotification()

        assertNull(serviceRepository.clientNotification.value)
    }

    @Test
    fun sendMessageDelegatesToActionHandlerWithLocalNodeNumber() = runTest {
        val controller = createController(myNodeNum = 456)
        val packet = DataPacket(to = DataPacket.ID_BROADCAST, channel = 1, text = "ping")

        controller.sendMessage(packet)

        verify { actionHandler.handleSend(packet, 456) }
    }

    @Test
    fun sendSharedContactEmitsActionAndWaitsForResult() = runTest {
        val serviceRepository = ServiceRepositoryImpl()
        val controller = createController(serviceRepository = serviceRepository)
        val nodeNum = 321
        val user = User(id = DataPacket.nodeNumToDefaultId(nodeNum), long_name = "Remote Node", short_name = "RN")
        val node = Node(num = nodeNum, user = user, manuallyVerified = true)
        every { nodeRepository.getNode(DataPacket.nodeNumToDefaultId(nodeNum)) } returns node

        val emittedAction = async { serviceRepository.serviceAction.first() }
        val sendResult = async { controller.sendSharedContact(nodeNum) }

        val action = emittedAction.await()
        assertTrue(action is ServiceAction.SendContact)
        assertEquals(node.num, action.contact.node_num)
        assertEquals(node.user, action.contact.user)
        assertEquals(node.manuallyVerified, action.contact.manually_verified)

        action.result.complete(true)

        assertTrue(sendResult.await())
    }

    @Test
    fun requestConfigOperationsDelegateToActionHandler() = runTest {
        val controller = createController()

        controller.getOwner(destNum = 101, packetId = 1)
        controller.getConfig(destNum = 102, configType = 2, packetId = 3)
        controller.getModuleConfig(destNum = 103, moduleConfigType = 4, packetId = 5)
        controller.getChannel(destNum = 104, index = 6, packetId = 7)
        controller.getRingtone(destNum = 105, packetId = 8)
        controller.getCannedMessages(destNum = 106, packetId = 9)
        controller.getDeviceConnectionStatus(destNum = 107, packetId = 10)

        verify { actionHandler.handleGetRemoteOwner(1, 101) }
        verify { actionHandler.handleGetRemoteConfig(3, 102, 2) }
        verify { actionHandler.handleGetModuleConfig(5, 103, 4) }
        verify { actionHandler.handleGetRemoteChannel(7, 104, 6) }
        verify { actionHandler.handleGetRingtone(8, 105) }
        verify { actionHandler.handleGetCannedMessages(9, 106) }
        verify { actionHandler.handleGetDeviceConnectionStatus(10, 107) }
    }

    @Test
    fun stopProvideLocationDelegatesToLocationManager() {
        val controller = createController()

        controller.stopProvideLocation()

        verify { locationManager.stop() }
    }

    @Test
    fun setDeviceAddressUpdatesLastAddressAndTransportAddress() {
        val controller = createController()

        controller.setDeviceAddress("tcp:192.168.1.1")

        verify { actionHandler.handleUpdateLastAddress("tcp:192.168.1.1") }
        verify { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") }
    }
}
