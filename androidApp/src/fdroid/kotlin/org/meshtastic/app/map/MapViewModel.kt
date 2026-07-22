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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MapCameraPosition
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.BaseMapViewModel
import java.io.InputStream

@Suppress("LongParameterList")
@KoinViewModel
class MapViewModel(
    mapPrefs: MapPrefs,
    packetRepository: PacketRepository,
    nodeRepository: NodeRepository,
    radioController: RadioController,
    radioConfigRepository: RadioConfigRepository,
    notificationPrefs: NotificationPrefs,
    buildConfigProvider: BuildConfigProvider,
    private val mapLayersManager: MapLayersManager,
    savedStateHandle: SavedStateHandle,
) : BaseMapViewModel(
    mapPrefs,
    nodeRepository,
    packetRepository,
    radioController,
    radioConfigRepository,
    notificationPrefs,
) {

    private val mutableInitialCameraState = MutableStateFlow<InitialCameraState>(InitialCameraState.Loading)
    internal val initialCameraState: StateFlow<InitialCameraState> = mutableInitialCameraState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableInitialCameraState.value = InitialCameraState.Ready(mapPrefs.awaitCameraPosition())
        }
    }

    internal fun saveCameraPosition(latitude: Double, longitude: Double, zoom: Double) {
        mapPrefs.setCameraPosition(MapCameraPosition(latitude, longitude, zoom))
    }

    private val _selectedWaypointId = MutableStateFlow(savedStateHandle.get<Int>("waypointId"))
    val selectedWaypointId: StateFlow<Int?> = _selectedWaypointId.asStateFlow()

    fun setWaypointId(id: Int?) {
        if (_selectedWaypointId.value != id) {
            _selectedWaypointId.value = id
        }
    }

    var mapStyleId: Int
        get() = mapPrefs.mapStyle.value
        set(value) {
            mapPrefs.setMapStyle(value)
        }

    val applicationId = buildConfigProvider.applicationId

    /** Imported overlay layers; owned by the flavor-neutral [MapLayersManager] and drawn on the OSMdroid map. */
    val mapLayers: StateFlow<List<MapLayerItem>> = mapLayersManager.mapLayers

    fun addMapLayer(uri: Uri, fileName: String?) = mapLayersManager.addMapLayer(uri, fileName)

    fun addGeoJsonLayer(name: String, geoJson: String) = mapLayersManager.addGeoJsonLayer(name, geoJson)

    fun addNetworkMapLayer(name: String, url: String) {
        mapLayersManager.addNetworkMapLayer(name, url)
    }

    fun toggleLayerVisibility(layerId: String) = mapLayersManager.toggleLayerVisibility(layerId)

    fun removeMapLayer(layerId: String) = mapLayersManager.removeMapLayer(layerId)

    fun refreshMapLayer(layerId: String) = mapLayersManager.refreshMapLayer(layerId)

    fun refreshAllVisibleNetworkLayers() = mapLayersManager.refreshAllVisibleNetworkLayers()

    suspend fun getInputStreamFromUri(layerItem: MapLayerItem): InputStream? =
        mapLayersManager.getInputStreamFromUri(layerItem)

    // Site Planner deep link from node detail: MapRoute.Map(sitePlannerNodeNum) → resolve to the node so the map can
    // open the estimate dialog pre-filled with its position. Cleared once consumed so it doesn't re-open.
    private val pendingSitePlannerNodeNum = MutableStateFlow(savedStateHandle.get<Int>("sitePlannerNodeNum"))
    val sitePlannerRequest: StateFlow<Node?> =
        combine(pendingSitePlannerNodeNum, nodeRepository.nodeDBbyNum) { num, db -> num?.let { db[it] } }
            .stateInWhileSubscribed(initialValue = null)

    fun consumeSitePlannerRequest() {
        pendingSitePlannerNodeNum.value = null
    }
}

internal sealed interface InitialCameraState {
    data object Loading : InitialCameraState

    data class Ready(val position: MapCameraPosition?) : InitialCameraState
}
