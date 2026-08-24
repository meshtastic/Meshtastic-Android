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
package org.meshtastic.app.map

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MapViewModelSitePlannerRequestTest {

    private val testDispatcher = StandardTestDispatcher()
    private val nodeRepository = FakeNodeRepository()
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val mapPrefs = FakeMapPrefs()
    private val firstNode = Node(num = 11)
    private val secondNode = Node(num = 22)
    private lateinit var httpClient: HttpClient
    private lateinit var mapLayersManager: MapLayersManager
    private lateinit var viewModel: MapViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { packetRepository.getWaypoints() } returns flowOf(emptyList())
        httpClient = HttpClient()
        mapLayersManager =
            MapLayersManager(
                application = ApplicationProvider.getApplicationContext(),
                dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher),
                httpClient = httpClient,
                mapPrefs = mapPrefs,
            )

        nodeRepository.setNodes(listOf(firstNode, secondNode))
        viewModel =
            MapViewModel(
                mapPrefs = mapPrefs,
                packetRepository = packetRepository,
                nodeRepository = nodeRepository,
                radioController = FakeRadioController(),
                radioConfigRepository = FakeRadioConfigRepository(),
                notificationPrefs = FakeNotificationPrefs(),
                mapLayersManager = mapLayersManager,
                savedStateHandle = SavedStateHandle(),
            )
    }

    @After
    fun tearDown() {
        httpClient.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `consumed site planner request is not rearmed by entry recomposition`() = runTest(testDispatcher) {
        viewModel.sitePlannerRequest.test {
            assertNull(awaitItem())

            viewModel.setSitePlannerNodeNum(firstNode.num)
            assertEquals(firstNode, awaitItem())

            viewModel.consumeSitePlannerRequest(firstNode.num)
            assertNull(awaitItem())

            viewModel.setSitePlannerNodeNum(firstNode.num)
            runCurrent()
            expectNoEvents()
            assertNull(viewModel.sitePlannerRequest.value)
        }
    }

    @Test
    fun `new route request replaces pending request and stale consumption cannot clear it`() = runTest(testDispatcher) {
        viewModel.sitePlannerRequest.test {
            assertNull(awaitItem())

            viewModel.setSitePlannerNodeNum(firstNode.num)
            assertEquals(firstNode, awaitItem())
            viewModel.setSitePlannerNodeNum(secondNode.num)
            assertEquals(secondNode, awaitItem())

            viewModel.consumeSitePlannerRequest(firstNode.num)
            expectNoEvents()
            assertEquals(secondNode, viewModel.sitePlannerRequest.value)
        }
    }

    @Test
    fun `pending request follows the live node and stops after consumption`() = runTest(testDispatcher) {
        viewModel.sitePlannerRequest.test {
            assertNull(awaitItem())

            viewModel.setSitePlannerNodeNum(firstNode.num)
            assertEquals(firstNode, awaitItem())

            val updatedNode = firstNode.copy(notes = "updated while pending")
            nodeRepository.setNodes(listOf(updatedNode, secondNode))
            assertEquals(updatedNode, awaitItem())

            viewModel.consumeSitePlannerRequest(updatedNode.num)
            assertNull(awaitItem())

            nodeRepository.setNodes(listOf(firstNode, secondNode))
            expectNoEvents()
        }
    }
}
