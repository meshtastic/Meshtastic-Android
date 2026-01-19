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
package com.geeksville.mesh.service

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.proto.MeshProtos
import org.meshtastic.proto.user

class MeshNodeManagerTest {

    private val nodeRepository: NodeRepository = mockk(relaxed = true)
    private val serviceBroadcasts: MeshServiceBroadcasts = mockk(relaxed = true)
    private val serviceNotifications: MeshServiceNotifications = mockk(relaxed = true)

    private lateinit var nodeManager: MeshNodeManager

    @Before
    fun setUp() {
        nodeManager = MeshNodeManager(nodeRepository, serviceBroadcasts, serviceNotifications)
    }

    @Test
    fun `getOrCreateNodeInfo creates default user for unknown node`() {
        val nodeNum = 1234
        val result = nodeManager.getOrCreateNodeInfo(nodeNum)

        assertNotNull(result)
        assertEquals(nodeNum, result.num)
        assertTrue(result.user.longName.startsWith("Meshtastic"))
        assertEquals(DataPacket.nodeNumToDefaultId(nodeNum), result.user.id)
    }

    @Test
    fun `handleReceivedUser preserves existing user if incoming is default`() {
        val nodeNum = 1234
        val existingUser = user {
            id = "!12345678"
            longName = "My Custom Name"
            shortName = "MCN"
            hwModel = MeshProtos.HardwareModel.TLORA_V2
        }

        // Setup existing node
        nodeManager.updateNodeInfo(nodeNum) { it.user = existingUser }

        val incomingDefaultUser = user {
            id = "!12345678"
            longName = "Meshtastic 5678"
            shortName = "5678"
            hwModel = MeshProtos.HardwareModel.UNSET
        }

        nodeManager.handleReceivedUser(nodeNum, incomingDefaultUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("My Custom Name", result!!.user.longName)
        assertEquals(MeshProtos.HardwareModel.TLORA_V2, result.user.hwModel)
    }

    @Test
    fun `handleReceivedUser updates user if incoming is higher detail`() {
        val nodeNum = 1234
        val existingUser = user {
            id = "!12345678"
            longName = "Meshtastic 5678"
            shortName = "5678"
            hwModel = MeshProtos.HardwareModel.UNSET
        }

        nodeManager.updateNodeInfo(nodeNum) { it.user = existingUser }

        val incomingDetailedUser = user {
            id = "!12345678"
            longName = "Real User"
            shortName = "RU"
            hwModel = MeshProtos.HardwareModel.TLORA_V1
        }

        nodeManager.handleReceivedUser(nodeNum, incomingDetailedUser)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertEquals("Real User", result!!.user.longName)
        assertEquals(MeshProtos.HardwareModel.TLORA_V1, result.user.hwModel)
    }

    @Test
    fun `handleReceivedPosition updates node position`() {
        val nodeNum = 1234
        val position =
            MeshProtos.Position.newBuilder()
                .apply {
                    latitudeI = 450000000
                    longitudeI = 900000000
                }
                .build()

        nodeManager.handleReceivedPosition(nodeNum, 9999, position)

        val result = nodeManager.nodeDBbyNodeNum[nodeNum]
        assertNotNull(result!!.position)
        assertEquals(45.0, result.latitude, 0.0001)
        assertEquals(90.0, result.longitude, 0.0001)
    }

    @Test
    fun `clear resets internal state`() {
        nodeManager.updateNodeInfo(1234) { it.longName = "Test" }
        nodeManager.clear()

        assertTrue(nodeManager.nodeDBbyNodeNum.isEmpty())
        assertTrue(nodeManager.nodeDBbyID.isEmpty())
        assertNull(nodeManager.myNodeNum)
    }
}
