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
package org.meshtastic.core.prefs.map

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapCameraPrefs

@Single
class MapCameraPrefsImpl(
    @Named("MapDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MapCameraPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val cameraLat: StateFlow<Double> =
        dataStore.data.map { it[KEY_CAMERA_LAT] ?: DEFAULT_LAT }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_LAT)

    override fun setCameraLat(value: Double) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_LAT] = value } }
    }

    override val cameraLng: StateFlow<Double> =
        dataStore.data.map { it[KEY_CAMERA_LNG] ?: DEFAULT_LNG }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_LNG)

    override fun setCameraLng(value: Double) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_LNG] = value } }
    }

    override val cameraZoom: StateFlow<Float> =
        dataStore.data.map { it[KEY_CAMERA_ZOOM] ?: DEFAULT_ZOOM }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_ZOOM)

    override fun setCameraZoom(value: Float) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_ZOOM] = value } }
    }

    override val cameraTilt: StateFlow<Float> =
        dataStore.data.map { it[KEY_CAMERA_TILT] ?: DEFAULT_TILT }.stateIn(scope, SharingStarted.Eagerly, DEFAULT_TILT)

    override fun setCameraTilt(value: Float) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_TILT] = value } }
    }

    override val cameraBearing: StateFlow<Float> =
        dataStore.data
            .map { it[KEY_CAMERA_BEARING] ?: DEFAULT_BEARING }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_BEARING)

    override fun setCameraBearing(value: Float) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_BEARING] = value } }
    }

    override val selectedStyleUri: StateFlow<String> =
        dataStore.data
            .map { it[KEY_SELECTED_STYLE_URI] ?: DEFAULT_STYLE_URI }
            .stateIn(scope, SharingStarted.Eagerly, DEFAULT_STYLE_URI)

    override fun setSelectedStyleUri(value: String) {
        scope.launch { dataStore.edit { it[KEY_SELECTED_STYLE_URI] = value } }
    }

    override val hiddenLayerUrls: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_HIDDEN_LAYER_URLS] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setHiddenLayerUrls(value: Set<String>) {
        scope.launch { dataStore.edit { it[KEY_HIDDEN_LAYER_URLS] = value } }
    }

    override val networkMapLayers: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_NETWORK_MAP_LAYERS] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setNetworkMapLayers(value: Set<String>) {
        scope.launch { dataStore.edit { it[KEY_NETWORK_MAP_LAYERS] = value } }
    }

    companion object {
        private const val DEFAULT_LAT = 0.0
        private const val DEFAULT_LNG = 0.0
        private const val DEFAULT_ZOOM = 7f
        private const val DEFAULT_TILT = 0f
        private const val DEFAULT_BEARING = 0f
        private const val DEFAULT_STYLE_URI = ""

        val KEY_CAMERA_LAT = doublePreferencesKey("map_camera_lat")
        val KEY_CAMERA_LNG = doublePreferencesKey("map_camera_lng")
        val KEY_CAMERA_ZOOM = floatPreferencesKey("map_camera_zoom")
        val KEY_CAMERA_TILT = floatPreferencesKey("map_camera_tilt")
        val KEY_CAMERA_BEARING = floatPreferencesKey("map_camera_bearing")
        val KEY_SELECTED_STYLE_URI = stringPreferencesKey("map_selected_style_uri")
        val KEY_HIDDEN_LAYER_URLS = stringSetPreferencesKey("map_hidden_layer_urls")
        val KEY_NETWORK_MAP_LAYERS = stringSetPreferencesKey("map_network_layers")
    }
}
