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

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.maps.android.compose.MapType
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.prefs.map.GoogleCameraPosition
import org.meshtastic.app.map.prefs.map.GoogleMapSelectionPrefs
import org.meshtastic.app.map.prefs.map.GoogleMapsPrefs
import org.meshtastic.app.map.repository.CustomTileProviderLoadResult
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.core.testing.FakeMapTileProviderPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeUiPrefs
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MapViewModelSitePlannerRequestTest {

    private val testDispatcher = StandardTestDispatcher()
    private val nodeRepository = FakeNodeRepository()
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val mapPrefs = FakeMapPrefs()
    private val googleMapsPrefs = mock<GoogleMapsPrefs>(MockMode.autofill)
    private val customTileProviderRepository = mock<CustomTileProviderRepository>(MockMode.autofill)
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
        every { googleMapsPrefs.cameraPosition } returns flowOf<GoogleCameraPosition?>(null)
        every { googleMapsPrefs.selectedCustomTileUrl } returns MutableStateFlow(null)
        every { googleMapsPrefs.selectedGoogleMapType } returns MutableStateFlow(null)
        everySuspend { googleMapsPrefs.awaitMapSelection() } returns
            GoogleMapSelectionPrefs(mapType = MapType.NORMAL.name, customTileUrl = null)
        every { customTileProviderRepository.getCustomTileProviders() } returns
            flowOf<List<CustomTileProviderConfig>>(emptyList())
        // autofill cannot synthesize the load result, and the ViewModel init dereferences it.
        everySuspend { customTileProviderRepository.awaitCustomTileProviders() } returns
            CustomTileProviderLoadResult(providers = emptyList(), isSuccessful = true)

        nodeRepository.setNodes(listOf(firstNode, secondNode))
        viewModel =
            MapViewModel(
                application = ApplicationProvider.getApplicationContext(),
                dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher),
                mapLayersManager = mapLayersManager,
                mapPrefs = mapPrefs,
                googleMapsPrefs = googleMapsPrefs,
                nodeRepository = nodeRepository,
                packetRepository = packetRepository,
                radioConfigRepository = FakeRadioConfigRepository(),
                radioController = FakeRadioController(),
                customTileProviderRepository = customTileProviderRepository,
                mapTileProviderPrefs = FakeMapTileProviderPrefs(),
                uiPrefs = FakeUiPrefs(),
                notificationPrefs = FakeNotificationPrefs(),
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
