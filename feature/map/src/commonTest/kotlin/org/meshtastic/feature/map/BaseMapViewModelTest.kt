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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.Position
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MagicNumber")
class BaseMapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: BaseMapViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var mapPrefs: FakeMapPrefs
    private val packetRepository: PacketRepository = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        radioController.setConnectionState(ConnectionState.Disconnected)
        mapPrefs = FakeMapPrefs()
        every { packetRepository.getWaypoints() } returns MutableStateFlow(emptyList())
        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BaseMapViewModel = BaseMapViewModel(
        mapPrefs = mapPrefs,
        nodeRepository = nodeRepository,
        packetRepository = packetRepository,
        radioController = radioController,
    )

    private fun nodeWithPosition(
        num: Int,
        latI: Int = 400000000,
        lngI: Int = -740000000,
        isFavorite: Boolean = false,
        lastHeard: Int = nowSeconds.toInt(),
    ): Node = Node(
        num = num,
        position = Position(latitude_i = latI, longitude_i = lngI),
        isFavorite = isFavorite,
        lastHeard = lastHeard,
    )

    // ---- Initialization ----

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun testMyNodeInfoFlow() = runTest(testDispatcher) {
        viewModel.myNodeInfo.test {
            assertEquals(null, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodesWithPositionStartsEmpty() = runTest(testDispatcher) {
        viewModel.nodesWithPosition.test {
            assertEquals(emptyList(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testConnectionStateFlow() = runTest(testDispatcher) {
        viewModel.isConnected.test {
            assertEquals(false, awaitItem())
            radioController.setConnectionState(ConnectionState.Connected)
            assertEquals(true, awaitItem())
            radioController.setConnectionState(ConnectionState.Disconnected)
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testNodeRepositoryIntegration() = runTest(testDispatcher) {
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)
        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
    }

    // ---- Filter toggle tests ----

    @Test
    fun toggleOnlyFavorites_togglesState() {
        assertFalse(viewModel.showOnlyFavoritesOnMap.value)
        viewModel.toggleOnlyFavorites()
        assertTrue(viewModel.showOnlyFavoritesOnMap.value)
        viewModel.toggleOnlyFavorites()
        assertFalse(viewModel.showOnlyFavoritesOnMap.value)
    }

    @Test
    fun toggleOnlyFavorites_persistsToPrefs() {
        viewModel.toggleOnlyFavorites()
        assertTrue(mapPrefs.showOnlyFavorites.value)
    }

    @Test
    fun toggleShowWaypointsOnMap_togglesState() {
        // FakeMapPrefs defaults to true
        assertTrue(viewModel.showWaypointsOnMap.value)
        viewModel.toggleShowWaypointsOnMap()
        assertFalse(viewModel.showWaypointsOnMap.value)
    }

    @Test
    fun toggleShowPrecisionCircleOnMap_togglesState() {
        assertTrue(viewModel.showPrecisionCircleOnMap.value)
        viewModel.toggleShowPrecisionCircleOnMap()
        assertFalse(viewModel.showPrecisionCircleOnMap.value)
    }

    @Test
    fun setLastHeardFilter_updatesStateAndPrefs() {
        viewModel.setLastHeardFilter(LastHeardFilter.OneHour)
        assertEquals(LastHeardFilter.OneHour, viewModel.lastHeardFilter.value)
        assertEquals(3600L, mapPrefs.lastHeardFilter.value)
    }

    @Test
    fun setLastHeardTrackFilter_updatesStateAndPrefs() {
        viewModel.setLastHeardTrackFilter(LastHeardFilter.OneDay)
        assertEquals(LastHeardFilter.OneDay, viewModel.lastHeardTrackFilter.value)
        assertEquals(86400L, mapPrefs.lastHeardTrackFilter.value)
    }

    // ---- MapFilterState composition ----

    @Test
    fun mapFilterState_reflectsAllFilterValues() = runTest(testDispatcher) {
        viewModel.mapFilterStateFlow.test {
            val initial = awaitItem()
            assertFalse(initial.onlyFavorites)
            assertTrue(initial.showWaypoints)
            assertTrue(initial.showPrecisionCircle)
            assertEquals(LastHeardFilter.Any, initial.lastHeardFilter)

            viewModel.toggleOnlyFavorites()
            val updated = awaitItem()
            assertTrue(updated.onlyFavorites)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- filteredNodes tests ----

    @Test
    fun filteredNodes_noFilters_returnsAllNodesWithPosition() = runTest(testDispatcher) {
        val nodes = listOf(nodeWithPosition(1), nodeWithPosition(2), nodeWithPosition(3))
        nodeRepository.setNodes(nodes)

        viewModel.filteredNodes.test {
            val result = expectMostRecentItem()
            assertEquals(3, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filteredNodes_favoritesFilter_showsOnlyFavoritesAndMyNode() = runTest(testDispatcher) {
        val myNodeNum = 1
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = myNodeNum))
        val nodes =
            listOf(
                nodeWithPosition(myNodeNum),
                nodeWithPosition(2, isFavorite = true),
                nodeWithPosition(3, isFavorite = false),
            )
        nodeRepository.setNodes(nodes)

        viewModel.toggleOnlyFavorites()

        viewModel.filteredNodes.test {
            val result = expectMostRecentItem()
            val nodeNums = result.map { it.num }.toSet()
            // My node (1) + favorite node (2) should be present; non-favorite (3) filtered out
            assertTrue(myNodeNum in nodeNums, "My node should always be visible")
            assertTrue(2 in nodeNums, "Favorite node should be visible")
            assertFalse(3 in nodeNums, "Non-favorite node should be filtered out")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filteredNodes_lastHeardFilter_excludesStaleNodes() = runTest(testDispatcher) {
        val now = nowSeconds.toInt()
        val nodes =
            listOf(
                nodeWithPosition(1, lastHeard = now), // heard just now
                nodeWithPosition(2, lastHeard = now - 7200), // heard 2 hours ago
            )
        nodeRepository.setNodes(nodes)

        viewModel.setLastHeardFilter(LastHeardFilter.OneHour)

        viewModel.filteredNodes.test {
            val result = expectMostRecentItem()
            val nodeNums = result.map { it.num }.toSet()
            assertTrue(1 in nodeNums, "Recently heard node should be visible")
            assertFalse(2 in nodeNums, "Stale node should be filtered out with 1-hour filter")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filteredNodes_anyFilter_showsAllNodes() = runTest(testDispatcher) {
        val now = nowSeconds.toInt()
        val nodes =
            listOf(
                nodeWithPosition(1, lastHeard = now),
                nodeWithPosition(2, lastHeard = now - 200000), // very old
            )
        nodeRepository.setNodes(nodes)

        viewModel.setLastHeardFilter(LastHeardFilter.Any)

        viewModel.filteredNodes.test {
            val result = expectMostRecentItem()
            assertEquals(2, result.size, "Any filter should show all nodes")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun filteredNodes_combinedFavoritesAndLastHeard_filtersCorrectly() = runTest(testDispatcher) {
        val now = nowSeconds.toInt()
        val myNodeNum = 1
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = myNodeNum))
        val nodes =
            listOf(
                nodeWithPosition(myNodeNum, lastHeard = now), // my node — always visible
                nodeWithPosition(2, isFavorite = true, lastHeard = now), // favorite + recent
                nodeWithPosition(3, isFavorite = true, lastHeard = now - 7200), // favorite + stale
                nodeWithPosition(4, isFavorite = false, lastHeard = now), // non-favorite + recent
            )
        nodeRepository.setNodes(nodes)

        // Enable both filters
        viewModel.toggleOnlyFavorites()
        viewModel.setLastHeardFilter(LastHeardFilter.OneHour)

        viewModel.filteredNodes.test {
            val result = expectMostRecentItem()
            val nodeNums = result.map { it.num }.toSet()
            // My node always visible, favorite+recent visible, favorite+stale filtered, non-favorite filtered
            assertTrue(myNodeNum in nodeNums, "My node should always be visible")
            assertTrue(2 in nodeNums, "Favorite + recent node should be visible")
            assertFalse(3 in nodeNums, "Favorite + stale node should be filtered out by lastHeard")
            assertFalse(4 in nodeNums, "Non-favorite node should be filtered out by favorites filter")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- getNodeOrFallback ----

    @Test
    fun getNodeOrFallback_existingNode_returnsNode() {
        val testNode = TestDataFactory.createTestNode(num = 42, longName = "Found")
        nodeRepository.setNodes(listOf(testNode))
        val result = viewModel.getNodeOrFallback(42)
        assertEquals(42, result.num)
        assertEquals("Found", result.user.long_name)
    }

    @Test
    fun getNodeOrFallback_missingNode_returnsFallback() {
        val result = viewModel.getNodeOrFallback(9999)
        assertEquals(9999, result.num)
    }
}
