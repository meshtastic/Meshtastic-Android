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
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.Waypoint
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class BaseMapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: BaseMapViewModel
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var radioController: FakeRadioController
    private lateinit var radioConfigRepository: FakeRadioConfigRepository
    private lateinit var waypointPacketsFlow: MutableStateFlow<List<DataPacket>>
    private val mapPrefs: MapPrefs = mock()
    private val packetRepository: PacketRepository = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nodeRepository = FakeNodeRepository()
        radioController = FakeRadioController()
        radioConfigRepository = FakeRadioConfigRepository()
        radioController.setConnectionState(ConnectionState.Disconnected)

        every { mapPrefs.showOnlyFavorites } returns MutableStateFlow(false)
        every { mapPrefs.showWaypointsOnMap } returns MutableStateFlow(false)
        every { mapPrefs.showPrecisionCircleOnMap } returns MutableStateFlow(false)
        every { mapPrefs.lastHeardFilter } returns MutableStateFlow(0L)
        every { mapPrefs.lastHeardTrackFilter } returns MutableStateFlow(0L)

        waypointPacketsFlow = MutableStateFlow(emptyList())
        every { packetRepository.getWaypoints() } returns waypointPacketsFlow

        viewModel =
            BaseMapViewModel(
                mapPrefs = mapPrefs,
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioController = radioController,
                radioConfigRepository = radioConfigRepository,
                notificationPrefs = FakeNotificationPrefs(),
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
    fun testNodeRepositoryIntegration() = runTest(testDispatcher) {
        val testNodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(testNodes)

        assertEquals(3, nodeRepository.nodeDBbyNum.value.size)
    }

    @Test
    fun testWaypointsIncludeFutureExpirations() = runTest(testDispatcher) {
        val now = nowSeconds.toInt()
        val futureWaypoint = waypointPacket(id = 1, expire = now + 60)

        viewModel.waypoints.test {
            assertEquals(emptyMap(), awaitItem())

            waypointPacketsFlow.value = listOf(futureWaypoint)

            assertEquals(mapOf(1 to futureWaypoint), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testWaypointsExcludeBoundaryExpirations() = runTest(testDispatcher) {
        val now = nowSeconds.toInt()
        val expiredAtNowWaypoint = waypointPacket(id = 2, expire = now)

        viewModel.waypoints.test {
            assertEquals(emptyMap(), awaitItem())

            waypointPacketsFlow.value = listOf(expiredAtNowWaypoint)

            expectNoEvents()
            assertEquals(emptyMap(), viewModel.waypoints.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testWaypointsIncludeNeverExpiringWaypoints() = runTest(testDispatcher) {
        val neverExpiresWaypoint = waypointPacket(id = 3, expire = 0)

        viewModel.waypoints.test {
            assertEquals(emptyMap(), awaitItem())

            waypointPacketsFlow.value = listOf(neverExpiresWaypoint)

            assertEquals(mapOf(3 to neverExpiresWaypoint), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testWaypointsFilterMixedExpiredAndActiveWaypoints() = runTest(testDispatcher) {
        val now = nowSeconds.toInt()
        val expiredWaypoint = waypointPacket(id = 4, expire = now - 1)
        val activeWaypoint = waypointPacket(id = 5, expire = now + 60)
        val neverExpiresWaypoint = waypointPacket(id = 6, expire = 0)

        viewModel.waypoints.test {
            assertEquals(emptyMap(), awaitItem())

            waypointPacketsFlow.value = listOf(expiredWaypoint, activeWaypoint, neverExpiresWaypoint)

            assertEquals(
                mapOf(
                    activeWaypoint.waypoint!!.id to activeWaypoint,
                    neverExpiresWaypoint.waypoint!!.id to neverExpiresWaypoint,
                ),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun waypointPacket(id: Int, expire: Int): DataPacket = DataPacket(
        to = NodeAddress.ID_BROADCAST,
        channel = 0,
        waypoint = Waypoint(id = id, name = "Waypoint $id", expire = expire),
    )
}
