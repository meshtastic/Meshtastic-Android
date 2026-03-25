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
package org.meshtastic.core.testing

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeSortOption
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeNodeRepositoryTest {

    private val repository = FakeNodeRepository()

    @Test
    fun `getNodes sorting by name`() = runTest {
        val nodes = listOf(
            Node(num = 1, user = User(long_name = "Charlie")),
            Node(num = 2, user = User(long_name = "Alice")),
            Node(num = 3, user = User(long_name = "Bob")),
        )
        repository.setNodes(nodes)

        repository.getNodes(sort = NodeSortOption.ALPHABETICAL).test {
            val result = awaitItem()
            assertEquals("Alice", result[0].user.long_name)
            assertEquals("Bob", result[1].user.long_name)
            assertEquals("Charlie", result[2].user.long_name)
        }
    }

    @Test
    fun `getUnknownNodes returns nodes with UNSET hw_model`() = runTest {
        val node1 = Node(num = 1, user = User(hw_model = org.meshtastic.proto.HardwareModel.UNSET))
        val node2 = Node(num = 2, user = User(hw_model = org.meshtastic.proto.HardwareModel.TLORA_V2))
        repository.setNodes(listOf(node1, node2))

        val result = repository.getUnknownNodes()
        assertEquals(1, result.size)
        assertEquals(1, result[0].num)
    }

    @Test
    fun `getNodes filtering by onlyOnline`() = runTest {
        val node1 = Node(num = 1, lastHeard = 2000000000) // Online
        val node2 = Node(num = 2, lastHeard = 0) // Offline
        repository.setNodes(listOf(node1, node2))

        repository.getNodes(onlyOnline = true).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0].num)
        }
    }

    @Test
    fun `getNodes filtering by onlyDirect`() = runTest {
        val node1 = Node(num = 1, hopsAway = 0) // Direct
        val node2 = Node(num = 2, hopsAway = 1) // Indirect
        repository.setNodes(listOf(node1, node2))

        repository.getNodes(onlyDirect = true).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(1, result[0].num)
        }
    }

    @Test
    fun `insertMetadata updates node metadata`() = runTest {
        val nodeNum = 1234
        repository.upsert(Node(num = nodeNum))
        val metadata = org.meshtastic.proto.DeviceMetadata(firmware_version = "2.5.0")
        repository.insertMetadata(nodeNum, metadata)

        val node = repository.nodeDBbyNum.value[nodeNum]
        assertEquals("2.5.0", node?.metadata?.firmware_version)
    }

    @Test
    fun `deleteNodes removes multiple nodes`() = runTest {
        repository.setNodes(listOf(Node(num = 1), Node(num = 2), Node(num = 3)))
        repository.deleteNodes(listOf(1, 2))

        assertEquals(1, repository.nodeDBbyNum.value.size)
        assertTrue(repository.nodeDBbyNum.value.containsKey(3))
    }

    @Test
    fun `getNodesOlderThan returns correct nodes`() = runTest {
        val node1 = Node(num = 1, lastHeard = 100)
        val node2 = Node(num = 2, lastHeard = 200)
        repository.setNodes(listOf(node1, node2))

        val result = repository.getNodesOlderThan(150)
        assertEquals(1, result.size)
        assertEquals(1, result[0].num)
    }

    @Test
    fun `setNodeNotes persists notes`() = runTest {
        val nodeNum = 1234
        repository.upsert(Node(num = nodeNum))
        repository.setNodeNotes(nodeNum, "My Note")

        val node = repository.nodeDBbyNum.value[nodeNum]
        assertEquals("My Note", node?.notes)
    }

    @Test
    fun `clearNodeDB preserves favorites`() = runTest {
        val node1 = Node(num = 1, isFavorite = true)
        val node2 = Node(num = 2, isFavorite = false)
        repository.setNodes(listOf(node1, node2))

        repository.clearNodeDB(preserveFavorites = true)

        assertEquals(1, repository.nodeDBbyNum.value.size)
        assertTrue(repository.nodeDBbyNum.value.containsKey(1))
    }
}
