/*
 * Copyright (c) 2025 Meshtastic LLC
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

import dagger.hilt.android.lifecycle.HiltViewModel
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.LocalOnlyProtos.LocalConfig
import javax.inject.Inject

@HiltViewModel
class MapViewModel
@Inject
constructor(
    mapPrefs: MapPrefs,
    packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
    serviceRepository: ServiceRepository,
    radioConfigRepository: RadioConfigRepository,
    buildConfigProvider: BuildConfigProvider,
    private val mapLibrePrefs: org.meshtastic.core.prefs.map.MapLibrePrefs,
) : BaseMapViewModel(mapPrefs, nodeRepository, packetRepository, serviceRepository) {

    var mapStyleId: Int
        get() = mapPrefs.mapStyle
        set(value) {
            mapPrefs.mapStyle = value
        }

    val localConfig =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig.getDefaultInstance())

    val config
        get() = localConfig.value

    val applicationId = buildConfigProvider.applicationId

    fun getUser(userId: String?) = nodeRepository.getUser(userId ?: DataPacket.ID_BROADCAST)

    // MapLibre camera position
    fun saveCameraPosition(latitude: Double, longitude: Double, zoom: Double, bearing: Double, tilt: Double) {
        mapLibrePrefs.cameraTargetLat = latitude
        mapLibrePrefs.cameraTargetLng = longitude
        mapLibrePrefs.cameraZoom = zoom
        mapLibrePrefs.cameraBearing = bearing
        mapLibrePrefs.cameraTilt = tilt
        // Set flag to restore position on next map open (e.g., returning from node details)
        mapLibrePrefs.shouldRestoreCameraPosition = true
    }

    fun getCameraPosition(): CameraState? {
        // Only restore if flag is set (user is returning from node details)
        if (!mapLibrePrefs.shouldRestoreCameraPosition) {
            return null
        }

        val lat = mapLibrePrefs.cameraTargetLat
        val lng = mapLibrePrefs.cameraTargetLng

        // Clear the flag so position is only restored once (on return from node details)
        mapLibrePrefs.shouldRestoreCameraPosition = false

        return if (lat != 0.0 || lng != 0.0) {
            CameraState(
                latitude = lat,
                longitude = lng,
                zoom = mapLibrePrefs.cameraZoom,
                bearing = mapLibrePrefs.cameraBearing,
                tilt = mapLibrePrefs.cameraTilt,
            )
        } else {
            null
        }
    }

    // Map settings
    var markerColorMode: String
        get() = mapLibrePrefs.markerColorMode
        set(value) {
            mapLibrePrefs.markerColorMode = value
        }

    var clusteringEnabled: Boolean
        get() = mapLibrePrefs.clusteringEnabled
        set(value) {
            mapLibrePrefs.clusteringEnabled = value
        }

    var heatmapEnabled: Boolean
        get() = mapLibrePrefs.heatmapEnabled
        set(value) {
            mapLibrePrefs.heatmapEnabled = value
        }

    var baseStyleIndex: Int
        get() = mapLibrePrefs.baseStyleIndex
        set(value) {
            mapLibrePrefs.baseStyleIndex = value
        }

    var customTileUrl: String?
        get() = mapLibrePrefs.customTileUrl
        set(value) {
            mapLibrePrefs.customTileUrl = value
        }

    var usingCustomTiles: Boolean
        get() = mapLibrePrefs.usingCustomTiles
        set(value) {
            mapLibrePrefs.usingCustomTiles = value
        }
}

data class CameraState(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
)
