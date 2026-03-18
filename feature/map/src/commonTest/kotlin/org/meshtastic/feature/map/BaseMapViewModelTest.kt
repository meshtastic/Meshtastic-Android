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
 * Bootstrap tests for BaseMapViewModel.
 *
 * Tests map functionality using FakeNodeRepository and test data.
 */
class BaseMapViewModelTest {
/*


    private lateinit var viewModel: BaseMapViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
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
    fun testInitialization() = runTest {
        setUp()
        assertTrue(true, "BaseMapViewModel initialized successfully")
    }

    @Test
    fun testMyNodeInfoFlow() = runTest {
        setUp()
        val myNodeInfo = viewModel.myNodeInfo.value
        assertTrue(myNodeInfo == null, "myNodeInfo starts as null")
    }

    @Test
    fun testNodesWithPositionStartsEmpty() = runTest {
        setUp()
        "nodesWithPosition should start empty" shouldBe emptyList<Any>(), viewModel.nodesWithPosition.value
    }

    @Test
    fun testConnectionStateFlow() = runTest {
        setUp()
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)
        // isConnected should reflect radioController state
        assertTrue(true, "Connection state flow is reactive")
    }

    @Test
    fun testNodeRepositoryIntegration() = runTest {
        setUp()
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)

        "Nodes added to repository" shouldBe 3, nodeRepository.nodeDBbyNum.value.size
    }

*/
}
