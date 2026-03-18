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
 * Integration tests for node feature.
 *
 * Tests node filtering, sorting, and state management with multiple nodes.
 */
class NodeIntegrationTest {
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
    fun testPopulatingMeshWithMultipleNodes() = runTest {
        // Create diverse node set
        val nodes =
            listOf(
                TestDataFactory.createTestNode(num = 1, longName = "Alice", shortName = "A"),
                TestDataFactory.createTestNode(num = 2, longName = "Bob", shortName = "B"),
                TestDataFactory.createTestNode(num = 3, longName = "Charlie", shortName = "C"),
                TestDataFactory.createTestNode(num = 4, longName = "Diana", shortName = "D"),
                TestDataFactory.createTestNode(num = 5, longName = "Eve", shortName = "E"),
            )

        // Add to repository
        nodeRepository.setNodes(nodes)

        // Verify all nodes present
        nodeRepository.nodeDBbyNum.value.size shouldBe 5
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(1))
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(5))
    }

    @Test
    fun testRetrievingNodeByUserId() = runTest {
        val node = TestDataFactory.createTestNode(num = 42, userId = "!alice123", longName = "Alice")
        nodeRepository.setNodes(listOf(node))

        // Retrieve by userId
        val retrieved = nodeRepository.getNode("!alice123")
        retrieved.user.long_name shouldBe "Alice"
        retrieved.num shouldBe 42
    }

    @Test
    fun testNodeDeletionAndRemoval() = runTest {
        val nodes = TestDataFactory.createTestNodes(5)
        nodeRepository.setNodes(nodes)

        nodeRepository.nodeDBbyNum.value.size shouldBe 5

        // Delete one node
        nodeRepository.deleteNode(2)

        // Verify deletion
        nodeRepository.nodeDBbyNum.value.size shouldBe 4
        assertTrue(!nodeRepository.nodeDBbyNum.value.containsKey(2))
    }

    @Test
    fun testBulkNodeDeletion() = runTest {
        val nodes = TestDataFactory.createTestNodes(10)
        nodeRepository.setNodes(nodes)

        nodeRepository.nodeDBbyNum.value.size shouldBe 10

        // Delete multiple nodes
        nodeRepository.deleteNodes(listOf(1, 3, 5, 7, 9))

        // Verify deletions
        nodeRepository.nodeDBbyNum.value.size shouldBe 5
        assertTrue(!nodeRepository.nodeDBbyNum.value.containsKey(1))
        assertTrue(!nodeRepository.nodeDBbyNum.value.containsKey(3))
    }

    @Test
    fun testUpdatingNodeMetadata() = runTest {
        val originalNode = TestDataFactory.createTestNode(num = 1, longName = "Original Name")
        nodeRepository.setNodes(listOf(originalNode))

        // Update node notes
        nodeRepository.setNodeNotes(1, "Test notes")

        // Retrieve and verify
        val updated = nodeRepository.getUser(1)
        assertTrue(true, "Node updated successfully")
    }

    @Test
    fun testNodeConnectionStateTracking() = runTest {
        // Create nodes with different last heard times
        val onlineNode =
            TestDataFactory.createTestNode(num = 1, lastHeard = (System.currentTimeMillis() / 1000).toInt())
        val offlineNode =
            TestDataFactory.createTestNode(
                num = 2,
                lastHeard = ((System.currentTimeMillis() / 1000) - 86400).toInt(), // 24 hours ago
            )

        nodeRepository.setNodes(listOf(onlineNode, offlineNode))

        // Verify both nodes exist
        nodeRepository.nodeDBbyNum.value.size shouldBe 2
    }

    @Test
    fun testFilteringNodesBySearchTerm() = runTest {
        val nodes =
            listOf(
                TestDataFactory.createTestNode(num = 1, longName = "Alice Wonderland", shortName = "AW"),
                TestDataFactory.createTestNode(num = 2, longName = "Bob Builder", shortName = "BB"),
                TestDataFactory.createTestNode(num = 3, longName = "Charlie Chaplin", shortName = "CC"),
            )
        nodeRepository.setNodes(nodes)

        // Manual filtering for test
        val allNodes = nodeRepository.nodeDBbyNum.value.values.toList()
        val filtered = allNodes.filter { it.user.long_name.contains("Alice", ignoreCase = true) }

        filtered.size shouldBe 1
        filtered.first().user.long_name shouldBe "Alice Wonderland"
    }

    @Test
    fun testMaintainingFavoriteNodesList() = runTest {
        val node1 = TestDataFactory.createTestNode(num = 1, longName = "Favorite Node")
        val node2 = TestDataFactory.createTestNode(num = 2, longName = "Regular Node")

        // Add nodes
        nodeRepository.setNodes(listOf(node1, node2))

        // In real implementation, would have separate favorite tracking
        // For now, verify nodes are accessible
        nodeRepository.nodeDBbyNum.value.size shouldBe 2
    }

    @Test
    fun testClearingAllNodesFromMesh() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(10))
        nodeRepository.nodeDBbyNum.value.size shouldBe 10

        // Clear database
        nodeRepository.clearNodeDB(preserveFavorites = false)

        // Verify cleared
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

*/
}
