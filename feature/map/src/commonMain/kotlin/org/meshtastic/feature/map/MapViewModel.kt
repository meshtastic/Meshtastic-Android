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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.style.BaseStyle
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MapCameraPrefs
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.model.MapStyle
import org.meshtastic.feature.map.util.COORDINATE_SCALE
import org.meshtastic.proto.Waypoint
import org.maplibre.spatialk.geojson.Position as GeoPosition

/**
 * Unified map ViewModel replacing the previous Google and F-Droid flavor-specific ViewModels.
 *
 * Manages camera state persistence, map style selection, and waypoint selection using MapLibre Compose Multiplatform
 * types. All map-related state is shared across platforms.
 */
@KoinViewModel
class MapViewModel(
    mapPrefs: MapPrefs,
    private val mapCameraPrefs: MapCameraPrefs,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    radioController: RadioController,
    savedStateHandle: SavedStateHandle,
) : BaseMapViewModel(mapPrefs, nodeRepository, packetRepository, radioController) {

    /** Currently selected waypoint to focus on map. */
    private val selectedWaypointIdInternal = MutableStateFlow<Int?>(savedStateHandle.get<Int?>("waypointId"))
    val selectedWaypointId: StateFlow<Int?> = selectedWaypointIdInternal.asStateFlow()

    fun setWaypointId(id: Int?) {
        selectedWaypointIdInternal.value = id
    }

    /** Initial camera position restored from persistent preferences. */
    val initialCameraPosition: CameraPosition
        get() =
            CameraPosition(
                target =
                GeoPosition(longitude = mapCameraPrefs.cameraLng.value, latitude = mapCameraPrefs.cameraLat.value),
                zoom = mapCameraPrefs.cameraZoom.value.toDouble(),
                tilt = mapCameraPrefs.cameraTilt.value.toDouble(),
                bearing = mapCameraPrefs.cameraBearing.value.toDouble(),
            )

    /** Active map base style. */
    val baseStyle: StateFlow<BaseStyle> =
        mapCameraPrefs.selectedStyleUri
            .map { uri -> if (uri.isBlank()) MapStyle.OpenStreetMap.toBaseStyle() else BaseStyle.Uri(uri) }
            .stateInWhileSubscribed(MapStyle.OpenStreetMap.toBaseStyle())

    /** Currently selected map style enum index. */
    val selectedMapStyle: StateFlow<MapStyle> =
        mapCameraPrefs.selectedStyleUri
            .map { uri -> MapStyle.entries.find { it.styleUri == uri } ?: MapStyle.OpenStreetMap }
            .stateInWhileSubscribed(MapStyle.OpenStreetMap)

    /** Persist camera position to DataStore. */
    fun saveCameraPosition(position: CameraPosition) {
        mapCameraPrefs.setCameraLat(position.target.latitude)
        mapCameraPrefs.setCameraLng(position.target.longitude)
        mapCameraPrefs.setCameraZoom(position.zoom.toFloat())
        mapCameraPrefs.setCameraTilt(position.tilt.toFloat())
        mapCameraPrefs.setCameraBearing(position.bearing.toFloat())
    }

    /** Select a predefined map style. */
    fun selectMapStyle(style: MapStyle) {
        mapCameraPrefs.setSelectedStyleUri(style.styleUri)
    }

    /**
     * Create a [Waypoint] proto from user-provided fields, handling coordinate conversion and ID generation.
     *
     * @param existingWaypoint If non-null, the waypoint being edited (retains its id and coordinates).
     * @param position If non-null, the long-press position for a new waypoint.
     */
    fun createAndSendWaypoint(
        name: String,
        description: String,
        icon: Int,
        locked: Boolean,
        expire: Int,
        existingWaypoint: Waypoint?,
        position: GeoPosition?,
    ) {
        val wpt =
            Waypoint(
                id = existingWaypoint?.id ?: generatePacketId(),
                name = name,
                description = description,
                icon = icon,
                locked_to = if (locked) (myNodeNum ?: 0) else 0,
                latitude_i =
                existingWaypoint?.latitude_i ?: position?.let { (it.latitude / COORDINATE_SCALE).toInt() } ?: 0,
                longitude_i =
                existingWaypoint?.longitude_i ?: position?.let { (it.longitude / COORDINATE_SCALE).toInt() } ?: 0,
                expire = expire,
            )
        sendWaypoint(wpt)
    }
}
