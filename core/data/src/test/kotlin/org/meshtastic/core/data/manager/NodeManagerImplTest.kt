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

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User

class NodeManagerImplTest {

    private val nodeRepository: NodeRepository = mockk(relaxed = true)
    private val serviceBroadcasts: ServiceBroadcasts = mockk(relaxed = true)
    private val serviceNotifications: MeshServiceNotifications = mockk(relaxed = true)

    private lateinit var nodeManager: NodeManagerImpl

    @Before
    fun setUp() {
        nodeManager = NodeManagerImpl(nodeRepository, serviceBroadcasts, serviceNotifications)
    }

    @Test
    fun `getOrCreateNode creates default user for unknown node`() {
        val nodeNum = 1234
        val result = nodeManager.getOrCreateNode(nodeNum)

        assertNotNull(result)
        assertEquals(nodeNum, result.num)
        assertTrue(result.user.long_name.startsWith("Meshtastic"))
        assertEquals(DataPacket.nodeNumToDefaultId(nodeNum), result.user.id)
    }

    @Test
    fun `handleReceivedUser preserves existing user if incoming is default`() {
        val nodeNum = 1234
        val existingUser =
            User(id = "!12345678", long_name = "My Custom Name", short_name = "MCN", hw_model = HardwareModel.TLORA_V2)

        // Setup existing node
        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingDefaultUser =
            User(id = "!12345678", long_name = "Meshtastic 5678", short_name = "5678", hw_model = HardwareModel.UNSET)

        nodeManager.handleReceivedUser(nodeNum, incomingDefaultUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("My Custom Name", result!!.user.long_name)
        assertEquals(HardwareModel.TLORA_V2, result.user.hw_model)
    }

    @Test
    fun `handleReceivedUser updates user if incoming is higher detail`() {
        val nodeNum = 1234
        val existingUser =
            User(id = "!12345678", long_name = "Meshtastic 5678", short_name = "5678", hw_model = HardwareModel.UNSET)

        nodeManager.updateNode(nodeNum) { it.copy(user = existingUser) }

        val incomingDetailedUser =
            User(id = "!12345678", long_name = "Real User", short_name = "RU", hw_model = HardwareModel.TLORA_V1)

        nodeManager.handleReceivedUser(nodeNum, incomingDetailedUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("Real User", result!!.user.long_name)
        assertEquals(HardwareModel.TLORA_V1, result.user.hw_model)
    }

    @Test
    fun `handleReceivedPosition updates node position`() {
        val nodeNum = 1234
        val position = Position(latitude_i = 450000000, longitude_i = 900000000)

        nodeManager.handleReceivedPosition(nodeNum, 9999, position, 0)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result!!.position)
        assertEquals(45.0, result.latitude, 0.0001)
        assertEquals(90.0, result.longitude, 0.0001)
    }

    @Test
    fun `clear resets internal state`() {
        nodeManager.updateNode(1234) { it.copy(user = it.user.copy(long_name = "Test")) }
        nodeManager.clear()

        assertTrue(nodeManager.nodeDBbyNodeNum.isEmpty())
        assertTrue(nodeManager.nodeDBbyID.isEmpty())
        assertNull(nodeManager.myNodeNum)
    }
}
