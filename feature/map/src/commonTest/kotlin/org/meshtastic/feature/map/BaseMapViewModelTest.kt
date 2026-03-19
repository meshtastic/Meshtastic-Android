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
package org.meshtastic.feature.map

import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMapViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: BaseMapViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private val mapPrefs: MapPrefs = mock()
    private val packetRepository: PacketRepository = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()

        every { mapPrefs.showOnlyFavorites } returns MutableStateFlow(false)
        every { mapPrefs.showWaypointsOnMap } returns MutableStateFlow(false)
        every { mapPrefs.showPrecisionCircleOnMap } returns MutableStateFlow(false)
        every { mapPrefs.lastHeardFilter } returns MutableStateFlow(0L)
        every { mapPrefs.lastHeardTrackFilter } returns MutableStateFlow(0L)

        every { packetRepository.getWaypoints() } returns MutableStateFlow(emptyList())

        viewModel =
            BaseMapViewModel(
                mapPrefs = mapPrefs,
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioController = radioController,
            )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun testMyNodeInfoFlow() = runTest {
        viewModel.myNodeInfo.test {
            assertEquals(null, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodesWithPositionStartsEmpty() = runTest {
        viewModel.nodesWithPosition.test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConnectionStateFlow() = runTest {
        viewModel.isConnected.test {
            // Initially reflects radioController state (which is Disconnected in FakeRadioController default)
            assertEquals(false, awaitItem())

            radioController.setConnectionState(ConnectionState.Connected)
            assertEquals(true, awaitItem())

            radioController.setConnectionState(ConnectionState.Disconnected)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodeRepositoryIntegration() = runTest {
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)

        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
    }
}
