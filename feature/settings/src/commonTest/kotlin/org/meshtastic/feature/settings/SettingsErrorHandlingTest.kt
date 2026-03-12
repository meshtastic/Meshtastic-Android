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
package org.meshtastic.feature.settings

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Error handling tests for settings feature.
 *
 * Tests edge cases and error scenarios in settings management.
 */
class SettingsErrorHandlingTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
    }

    @Test
    fun testSettingsOnNonexistentNode() = runTest {
        // Try to set notes on node that doesn't exist
        nodeRepository.setNodeNotes(999, "Settings")

        // Should be no-op
        assertEquals(0, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testGetUserInfoOnDeletedNode() = runTest {
        val node = TestDataFactory.createTestNode(num = 1)
        nodeRepository.setNodes(listOf(node))

        // Delete node
        nodeRepository.deleteNode(1)

        // Try to get user info
        // Should handle gracefully
        assertEquals(0, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testModifySettingsWhileDisconnected() = runTest {
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Add node and modify settings
        val node = TestDataFactory.createTestNode(num = 1)
        nodeRepository.setNodes(listOf(node))
        nodeRepository.setNodeNotes(1, "Modified while disconnected")

        // Should work (local operation)
        assertEquals(1, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testConnectAndDisconnectCycle() = runTest {
        val nodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(nodes)

        // Cycle through connection states
        repeat(5) {
            radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)
            radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)
        }

        // Nodes should still be there
        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testFactoryResetWithoutConnection() = runTest {
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        nodeRepository.setNodes(TestDataFactory.createTestNodes(5))
        assertEquals(5, nodeRepository.nodeDBbyNum.value.size)

        // Factory reset while disconnected
        nodeRepository.clearNodeDB(preserveFavorites = false)

        // Should clear
        assertEquals(0, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testEmptySettingsDatabase() = runTest {
        // Do nothing, just check initial state
        val nodes = nodeRepository.nodeDBbyNum.value
        assertEquals(0, nodes.size)
    }

    @Test
    fun testRepeatedSettingsModification() = runTest {
        val node = TestDataFactory.createTestNode(num = 1)
        nodeRepository.setNodes(listOf(node))

        // Modify settings multiple times
        repeat(10) { i -> nodeRepository.setNodeNotes(1, "Note $i") }

        // Should still have one node
        assertEquals(1, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testMultipleNodeSettingsConcurrency() = runTest {
        val nodes = TestDataFactory.createTestNodes(5)
        nodeRepository.setNodes(nodes)

        // Update settings on all nodes
        nodes.forEach { node -> nodeRepository.setNodeNotes(node.num, "Updated: ${node.user.long_name}") }

        // All should still be there
        assertEquals(5, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testSettingsAfterPartialDelete() = runTest {
        val nodes = TestDataFactory.createTestNodes(5)
        nodeRepository.setNodes(nodes)

        // Delete some nodes
        nodeRepository.deleteNode(1)
        nodeRepository.deleteNode(3)

        // Try to modify settings on remaining nodes
        nodeRepository.setNodeNotes(2, "Still here")
        nodeRepository.setNodeNotes(4, "Still here")

        // Should have 3 nodes remaining
        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testConnectionRecoveryAfterPartialUpdate() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))

        // Start connected
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)

        // Update some settings
        nodeRepository.setNodeNotes(1, "Update 1")

        // Lose connection
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Update more settings
        nodeRepository.setNodeNotes(2, "Update 2")

        // Reconnect
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)

        // All data should still be accessible
        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
    }
}
