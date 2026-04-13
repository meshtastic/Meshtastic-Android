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

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.maplibre.compose.style.BaseStyle
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeMapCameraPrefs
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.proto.Waypoint
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MapViewModel
    private lateinit var mapCameraPrefs: FakeMapCameraPrefs
    private lateinit var mapPrefs: FakeMapPrefs
    private lateinit var radioController: FakeRadioController
    private val packetRepository: PacketRepository = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mapCameraPrefs = FakeMapCameraPrefs()
        mapPrefs = FakeMapPrefs()
        radioController = FakeRadioController()
        every { packetRepository.getWaypoints() } returns MutableStateFlow(emptyList())
        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        nodeRepository: FakeNodeRepository = FakeNodeRepository(),
    ): MapViewModel = MapViewModel(
        mapPrefs = mapPrefs,
        mapCameraPrefs = mapCameraPrefs,
        nodeRepository = nodeRepository,
        packetRepository = packetRepository,
        radioController = radioController,
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun selectedWaypointIdDefaultsToNull() {
        assertNull(viewModel.selectedWaypointId.value)
    }

    @Test
    fun selectedWaypointIdRestoredFromSavedState() {
        val vm = createViewModel(SavedStateHandle(mapOf("waypointId" to 42)))
        assertEquals(42, vm.selectedWaypointId.value)
    }

    @Test
    fun setWaypointIdUpdatesState() {
        viewModel.setWaypointId(7)
        assertEquals(7, viewModel.selectedWaypointId.value)

        viewModel.setWaypointId(null)
        assertNull(viewModel.selectedWaypointId.value)
    }

    @Test
    fun initialCameraPositionReflectsPrefs() {
        mapCameraPrefs.setCameraLat(51.5)
        mapCameraPrefs.setCameraLng(-0.1)
        mapCameraPrefs.setCameraZoom(12f)
        mapCameraPrefs.setCameraTilt(30f)
        mapCameraPrefs.setCameraBearing(45f)

        val vm = createViewModel()
        val pos = vm.initialCameraPosition

        assertEquals(51.5, pos.target.latitude)
        assertEquals(-0.1, pos.target.longitude)
        assertEquals(12.0, pos.zoom)
        assertEquals(30.0, pos.tilt)
        assertEquals(45.0, pos.bearing)
    }

    @Test
    fun saveCameraPositionPersistsToPrefs() {
        val cameraPosition =
            org.maplibre.compose.camera.CameraPosition(
                target = org.maplibre.spatialk.geojson.Position(longitude = -122.4, latitude = 37.8),
                zoom = 15.0,
                tilt = 20.0,
                bearing = 90.0,
            )

        viewModel.saveCameraPosition(cameraPosition)

        assertEquals(37.8, mapCameraPrefs.cameraLat.value)
        assertEquals(-122.4, mapCameraPrefs.cameraLng.value)
        assertEquals(15f, mapCameraPrefs.cameraZoom.value)
        assertEquals(20f, mapCameraPrefs.cameraTilt.value)
        assertEquals(90f, mapCameraPrefs.cameraBearing.value)
    }

    @Test
    fun baseStyleDefaultsToOpenStreetMap() = runTest(testDispatcher) {
        viewModel.baseStyle.test {
            val style = awaitItem()
            assertEquals(BaseStyle.Uri(MapStyle.OpenStreetMap.styleUri), style)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectMapStyleUpdatesBaseStyleAndSelectedMapStyle() = runTest(testDispatcher) {
        viewModel.selectedMapStyle.test {
            assertEquals(MapStyle.OpenStreetMap, awaitItem())

            viewModel.selectMapStyle(MapStyle.Dark)
            assertEquals(MapStyle.Dark, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun baseStyleEmitsUriOnStyleChange() = runTest(testDispatcher) {
        viewModel.baseStyle.test {
            // Initial style
            awaitItem()

            viewModel.selectMapStyle(MapStyle.Dark)
            val darkStyle = awaitItem()
            assertEquals(BaseStyle.Uri(MapStyle.Dark.styleUri), darkStyle)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun blankStyleUriFallsBackToOpenStreetMap() = runTest(testDispatcher) {
        // selectedStyleUri defaults to "" in FakeMapCameraPrefs
        viewModel.baseStyle.test {
            val style = awaitItem()
            assertEquals(BaseStyle.Uri(MapStyle.OpenStreetMap.styleUri), style)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- createAndSendWaypoint tests ----

    @Test
    fun createAndSendWaypoint_newWaypoint_convertsPositionToIntCoordinates() = runTest(testDispatcher) {
        val position = org.maplibre.spatialk.geojson.Position(longitude = -74.0, latitude = 40.0)

        viewModel.createAndSendWaypoint(
            name = "Test WP",
            description = "A waypoint",
            icon = 0x1F4CD,
            locked = false,
            expire = 0,
            existingWaypoint = null,
            position = position,
        )

        // sendWaypoint dispatches to ioDispatcher; give it time to execute
        delay(100)

        // FakeRadioController.getPacketId() returns 1, and sendMessage appends to sentPackets
        assertEquals(1, radioController.sentPackets.size)
        val sent = radioController.sentPackets.first()
        val wpt = sent.waypoint!!
        assertEquals("Test WP", wpt.name)
        assertEquals("A waypoint", wpt.description)
        assertEquals(0x1F4CD, wpt.icon)
        assertEquals(0, wpt.locked_to)
        // 40.0 / 1e-7 = 400000000
        assertEquals(400000000, wpt.latitude_i)
        // -74.0 / 1e-7 = -740000000
        assertEquals(-740000000, wpt.longitude_i)
    }

    @Test
    fun createAndSendWaypoint_editExisting_retainsOriginalCoordinates() = runTest(testDispatcher) {
        val existing = Waypoint(id = 42, name = "Old Name", latitude_i = 515000000, longitude_i = -1000000)

        viewModel.createAndSendWaypoint(
            name = "New Name",
            description = "Updated",
            icon = 0x1F3E0,
            locked = false,
            expire = 0,
            existingWaypoint = existing,
            position = null,
        )

        delay(100)

        assertEquals(1, radioController.sentPackets.size)
        val wpt = radioController.sentPackets.first().waypoint!!
        assertEquals(42, wpt.id) // Retains existing ID
        assertEquals("New Name", wpt.name)
        assertEquals(515000000, wpt.latitude_i) // Retains existing coords
        assertEquals(-1000000, wpt.longitude_i)
    }

    @Test
    fun createAndSendWaypoint_locked_setsLockedToMyNodeNum() = runTest(testDispatcher) {
        val nodeRepository = FakeNodeRepository()
        nodeRepository.setMyNodeInfo(org.meshtastic.core.testing.TestDataFactory.createMyNodeInfo(myNodeNum = 99))
        val vm = createViewModel(nodeRepository = nodeRepository)
        val position = org.maplibre.spatialk.geojson.Position(longitude = 0.1, latitude = 0.1)

        vm.createAndSendWaypoint(
            name = "Locked WP",
            description = "",
            icon = 0,
            locked = true,
            expire = 0,
            existingWaypoint = null,
            position = position,
        )

        delay(100)

        assertEquals(1, radioController.sentPackets.size)
        assertEquals(99, radioController.sentPackets.first().waypoint!!.locked_to)
    }

    @Test
    fun createAndSendWaypoint_noPositionNoExisting_usesZeroCoordinates() = runTest(testDispatcher) {
        viewModel.createAndSendWaypoint(
            name = "Nowhere",
            description = "",
            icon = 0,
            locked = false,
            expire = 0,
            existingWaypoint = null,
            position = null,
        )

        delay(100)

        assertEquals(1, radioController.sentPackets.size)
        val wpt = radioController.sentPackets.first().waypoint!!
        assertEquals(0, wpt.latitude_i)
        assertEquals(0, wpt.longitude_i)
    }
}
