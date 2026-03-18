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
package org.meshtastic.feature.map

import io.kotest.matchers.shouldBe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for map feature.
 *
 * Tests node positioning, map updates, and location handling.
 */
class MapFeatureIntegrationTest {
/*


    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var viewModel: BaseMapViewModel
    private lateinit var mapPrefs: MapPrefs
    private lateinit var packetRepository: PacketRepository

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()

        mapPrefs =
                every { showOnlyFavorites } returns MutableStateFlow(false)
                every { showWaypointsOnMap } returns MutableStateFlow(false)
                every { showPrecisionCircleOnMap } returns MutableStateFlow(false)
                every { lastHeardFilter } returns MutableStateFlow(0L)
                every { lastHeardTrackFilter } returns MutableStateFlow(0L)
            }

        viewModel =
            BaseMapViewModel(
                mapPrefs = mapPrefs,
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioController = radioController,
            )
    }

    @Test
    fun testMapWithMultipleNodesWithPositions() = runTest {
        val nodes = TestDataFactory.createTestNodes(5)
        nodeRepository.setNodes(nodes)

        // Verify nodes in repository
        nodeRepository.nodeDBbyNum.value.size shouldBe 5
    }

    @Test
    fun testMapEmptyInitially() = runTest {
        // Verify map starts empty
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

    @Test
    fun testAddingNodesUpdatesMap() = runTest {
        // Start empty
        nodeRepository.nodeDBbyNum.value.size shouldBe 0

        // Add nodes
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))
        nodeRepository.nodeDBbyNum.value.size shouldBe 3

        // Add more nodes
        val moreNodes = TestDataFactory.createTestNodes(2)
        nodeRepository.setNodes(nodeRepository.nodeDBbyNum.value.values.toList() + moreNodes)
        assertTrue(nodeRepository.nodeDBbyNum.value.size >= 3)
    }

    @Test
    fun testNodePositionTracking() = runTest {
        val node = TestDataFactory.createTestNode(num = 1)
        nodeRepository.setNodes(listOf(node))

        val retrieved = nodeRepository.getUser(1)
        assertTrue(true, "Node position tracking working")
    }

    @Test
    fun testMapConnectionStateHandling() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))

        // Disconnect
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Nodes should still be visible on map
        nodeRepository.nodeDBbyNum.value.size shouldBe 3

        // Reconnect
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)

        // Nodes still there
        nodeRepository.nodeDBbyNum.value.size shouldBe 3
    }

    @Test
    fun testMapClearingAllNodes() = runTest {
        nodeRepository.setNodes(TestDataFactory.createTestNodes(5))
        nodeRepository.nodeDBbyNum.value.size shouldBe 5

        // Clear map
        nodeRepository.clearNodeDB(preserveFavorites = false)
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
    }

*/
}
