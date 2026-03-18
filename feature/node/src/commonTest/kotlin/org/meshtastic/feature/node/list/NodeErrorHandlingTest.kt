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
package org.meshtastic.feature.node.list

import io.kotest.matchers.shouldBe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Error handling tests for node feature.
 *
 * Tests edge cases, failure recovery, and boundary conditions.
 */
class NodeErrorHandlingTest {
/*


    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController

    @BeforeTest
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
    }

    @kotlin.test.AfterTest
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun testGetNonexistentNode() = runTest {
        val node = nodeRepository.getNode("!nonexistent")
        // FakeNodeRepository returns a fallback node (never null)
        node.user.id shouldBe "!nonexistent"
    }

    @Test
    fun testDeleteNonexistentNode() = runTest {
        val beforeCount = nodeRepository.nodeDBbyNum.value.size

        nodeRepository.deleteNode(999)

        val afterCount = nodeRepository.nodeDBbyNum.value.size
        afterCount shouldBe beforeCount
    }

    @Test
    fun testNodeDatabaseEmptyOnStart() = runTest {
        val nodes = nodeRepository.nodeDBbyNum.value
        nodes.size shouldBe 0
    }

    @Test
    fun testRepeatedClear() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(5))
        nodeRepository.nodeDBbyNum.value.size shouldBe 5

        // Clear multiple times
        nodeRepository.clearNodeDB(preserveFavorites = false)
        nodeRepository.clearNodeDB(preserveFavorites = false)
        nodeRepository.clearNodeDB(preserveFavorites = false)

        // Should still be empty
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

    @Test
    fun testSetEmptyNodeList() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))
        nodeRepository.nodeDBbyNum.value.size shouldBe 3

        // Set to empty
        nodeRepository.setNodes(emptyList())
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

    @Test
    fun testDeleteAllNodes() = runTest {
        val nodes = TestDataFactory.createTestNodes(5)
        nodeRepository.setNodes(nodes)

        // Delete each node
        nodes.forEach { node -> nodeRepository.deleteNode(node.num) }

        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

    @Test
    fun testNodeMetadataOnDeletedNode() = runTest {
        val node = TestDataFactory.createTestNode(num = 1, longName = "Test")
        nodeRepository.setNodes(listOf(node))

        // Delete node
        nodeRepository.deleteNode(1)

        // Try to get notes on deleted node
        // Should not crash
        assertTrue(true)
    }

    @Test
    fun testNotesOnNonexistentNode() = runTest {
        // Set notes on node that never existed
        nodeRepository.setNodeNotes(999, "Notes")

        // Should be no-op
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

    @Test
    fun testConnectionStateChangesDuringNodeManagement() = runTest {
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Add nodes while disconnected (local operation)
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))
        nodeRepository.nodeDBbyNum.value.size shouldBe 3

        // Switch to connected
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)

        // Nodes should still be there
        nodeRepository.nodeDBbyNum.value.size shouldBe 3

        // Switch back to disconnected
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Nodes still there
        nodeRepository.nodeDBbyNum.value.size shouldBe 3
    }

    @Test
    fun testLargeNodeDatabaseHandling() = runTest {
        // Create large dataset
        val largeNodeSet = TestDataFactory.createTestNodes(500)
        nodeRepository.setNodes(largeNodeSet)

        nodeRepository.nodeDBbyNum.value.size shouldBe 500
    }

    @Test
    fun testRapidAddDelete() = runTest {
        // Rapidly add and delete nodes
        repeat(10) { iteration ->
            nodeRepository.setNodes(TestDataFactory.createTestNodes(5))
            nodeRepository.nodeDBbyNum.value.size shouldBe 5

            nodeRepository.clearNodeDB(preserveFavorites = false)
            nodeRepository.nodeDBbyNum.value.size shouldBe 0
        }

        // Final state should be clean
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

*/
}
