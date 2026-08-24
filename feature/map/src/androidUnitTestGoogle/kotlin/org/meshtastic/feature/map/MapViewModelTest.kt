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

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.google.android.gms.maps.model.UrlTileProvider
import dev.mokkery.MockMode
import dev.mokkery.every
import dev.mokkery.mock
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.map.model.CustomTileProviderConfig
import org.meshtastic.feature.map.prefs.map.GoogleMapsPrefs
import org.meshtastic.feature.map.repository.CustomTileProviderRepository
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MapViewModelTest {

    private val application = mock<Application>(MockMode.autofill)
    private val mapPrefs = mock<MapPrefs>(MockMode.autofill)
    private val googleMapsPrefs = mock<GoogleMapsPrefs>(MockMode.autofill)
    private val nodeRepository = FakeNodeRepository()
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)
    private val radioController = FakeRadioController()
    private val customTileProviderRepository = mock<CustomTileProviderRepository>(MockMode.autofill)
    private val uiPrefs = mock<UiPrefs>(MockMode.autofill)
    private val savedStateHandle = SavedStateHandle(mapOf("waypointId" to null))

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: MapViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { mapPrefs.mapStyle } returns MutableStateFlow(0)
        every { mapPrefs.showOnlyFavorites } returns MutableStateFlow(false)
        every { mapPrefs.showWaypointsOnMap } returns MutableStateFlow(true)
        every { mapPrefs.showPrecisionCircleOnMap } returns MutableStateFlow(true)
        every { mapPrefs.lastHeardFilter } returns MutableStateFlow(0L)
        every { mapPrefs.lastHeardTrackFilter } returns MutableStateFlow(0L)

        every { googleMapsPrefs.cameraTargetLat } returns MutableStateFlow(0.0)
        every { googleMapsPrefs.cameraTargetLng } returns MutableStateFlow(0.0)
        every { googleMapsPrefs.cameraZoom } returns MutableStateFlow(0f)
        every { googleMapsPrefs.cameraTilt } returns MutableStateFlow(0f)
        every { googleMapsPrefs.cameraBearing } returns MutableStateFlow(0f)
        every { googleMapsPrefs.selectedCustomTileUrl } returns MutableStateFlow(null)
        every { googleMapsPrefs.selectedGoogleMapType } returns MutableStateFlow(null)
        every { googleMapsPrefs.hiddenLayerUrls } returns MutableStateFlow(emptySet())

        every { customTileProviderRepository.getCustomTileProviders() } returns flowOf(emptyList())
        every { radioConfigRepository.deviceProfileFlow } returns flowOf(mock(MockMode.autofill))
        every { uiPrefs.theme } returns MutableStateFlow(1)
        every { packetRepository.getWaypoints() } returns flowOf(emptyList())

        viewModel =
            MapViewModel(
                application,
                mapPrefs,
                googleMapsPrefs,
                nodeRepository,
                packetRepository,
                radioConfigRepository,
                radioController,
                customTileProviderRepository,
                uiPrefs,
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
        viewModel.addNetworkMapLayer("Test Layer", "https://example.com/data.geojson")
        advanceUntilIdle()

        val layer = viewModel.mapLayers.value.find { it.name == "Test Layer" }
        assertEquals(LayerType.GEOJSON, layer?.layerType)
    }

    @Test
    fun `addNetworkMapLayer defaults to KML for other extensions`() = runTest(testDispatcher) {
        viewModel.addNetworkMapLayer("Test KML", "https://example.com/map.kml")
        advanceUntilIdle()

        val layer = viewModel.mapLayers.value.find { it.name == "Test KML" }
        assertEquals(LayerType.KML, layer?.layerType)
    }

    @Test
    fun `setWaypointId updates value correctly including null`() = runTest(testDispatcher) {
        // Set to a valid ID
        viewModel.setWaypointId(123)
        assertEquals(123, viewModel.selectedWaypointId.value)

        // Set to null should clear the selection
        viewModel.setWaypointId(null)
        assertEquals(null, viewModel.selectedWaypointId.value)
    }
}
