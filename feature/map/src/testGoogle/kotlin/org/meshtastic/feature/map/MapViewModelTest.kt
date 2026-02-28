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

import android.app.Application
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.google.android.gms.maps.model.UrlTileProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.data.model.CustomTileProviderConfig
import org.meshtastic.core.data.repository.CustomTileProviderRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.prefs.map.GoogleMapsPrefs
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.service.ConnectionState
import org.meshtastic.core.service.ServiceRepository
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {

    private val application = mockk<Application>(relaxed = true)
    private val mapPrefs = mockk<MapPrefs>(relaxed = true)
    private val googleMapsPrefs = mockk<GoogleMapsPrefs>(relaxed = true)
    private val nodeRepository = mockk<NodeRepository>(relaxed = true)
    private val packetRepository = mockk<PacketRepository>(relaxed = true)
    private val radioConfigRepository = mockk<RadioConfigRepository>(relaxed = true)
    private val serviceRepository = mockk<ServiceRepository>(relaxed = true)
    private val customTileProviderRepository = mockk<CustomTileProviderRepository>(relaxed = true)
    private val uiPreferencesDataSource = mockk<UiPreferencesDataSource>(relaxed = true)
    private val savedStateHandle = SavedStateHandle(mapOf("waypointId" to null))

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { customTileProviderRepository.getCustomTileProviders() } returns flowOf(emptyList())
        every { radioConfigRepository.deviceProfileFlow } returns flowOf(mockk(relaxed = true))
        every { uiPreferencesDataSource.theme } returns MutableStateFlow(1)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(null)
        every { nodeRepository.myId } returns MutableStateFlow(null)
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(emptyMap())
        every { nodeRepository.getNodes() } returns flowOf(emptyList())
        every { packetRepository.getWaypoints() } returns flowOf(emptyList())
        every { serviceRepository.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)

        viewModel =
            MapViewModel(
                application,
                mapPrefs,
                googleMapsPrefs,
                nodeRepository,
                packetRepository,
                radioConfigRepository,
                serviceRepository,
                customTileProviderRepository,
                uiPreferencesDataSource,
                savedStateHandle,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `getTileProvider returns UrlTileProvider for remote config`() = runTest {
        val config =
            CustomTileProviderConfig(
                name = "OpenStreetMap",
                urlTemplate = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
            )

        val provider = viewModel.getTileProvider(config)
        assertTrue(provider is UrlTileProvider)
    }

    @Test
    fun `addNetworkMapLayer detects GeoJSON based on extension`() = runTest(testDispatcher) {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { Uri.parse("https://example.com/data.geojson") } returns mockUri
        every { mockUri.scheme } returns "https"
        every { mockUri.path } returns "/data.geojson"
        every { mockUri.toString() } returns "https://example.com/data.geojson"

        viewModel.addNetworkMapLayer("Test Layer", "https://example.com/data.geojson")
        advanceUntilIdle()

        val layer = viewModel.mapLayers.value.find { it.name == "Test Layer" }
        assertEquals(LayerType.GEOJSON, layer?.layerType)
    }

    @Test
    fun `addNetworkMapLayer defaults to KML for other extensions`() = runTest(testDispatcher) {
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { Uri.parse("https://example.com/map.kml") } returns mockUri
        every { mockUri.scheme } returns "https"
        every { mockUri.path } returns "/map.kml"
        every { mockUri.toString() } returns "https://example.com/map.kml"

        viewModel.addNetworkMapLayer("Test KML", "https://example.com/map.kml")
        advanceUntilIdle()

        val layer = viewModel.mapLayers.value.find { it.name == "Test KML" }
        assertEquals(LayerType.KML, layer?.layerType)
    }
}
