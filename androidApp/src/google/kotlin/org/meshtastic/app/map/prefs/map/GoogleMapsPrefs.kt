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
package org.meshtastic.app.map.prefs.map

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.maps.android.compose.MapType
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

/** Interface for prefs specific to Google Maps. For general map prefs, see MapPrefs. */
interface GoogleMapsPrefs {
    val selectedGoogleMapType: StateFlow<String?>

    fun setSelectedGoogleMapType(value: String?)

    val selectedCustomTileUrl: StateFlow<String?>

    fun setSelectedCustomTileUrl(value: String?)

    val hiddenLayerUrls: StateFlow<Set<String>>

    fun setHiddenLayerUrls(value: Set<String>)

    val cameraTargetLat: StateFlow<Double>

    fun setCameraTargetLat(value: Double)

    val cameraTargetLng: StateFlow<Double>

    fun setCameraTargetLng(value: Double)

    val cameraZoom: StateFlow<Float>

    fun setCameraZoom(value: Float)

    val cameraTilt: StateFlow<Float>

    fun setCameraTilt(value: Float)

    val cameraBearing: StateFlow<Float>

    fun setCameraBearing(value: Float)

    val networkMapLayers: StateFlow<Set<String>>

    fun setNetworkMapLayers(value: Set<String>)
}

@Single
class GoogleMapsPrefsImpl(
    @Named("GoogleMapsDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : GoogleMapsPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val selectedGoogleMapType: StateFlow<String?> =
        dataStore.data
            .map { it[KEY_SELECTED_GOOGLE_MAP_TYPE_PREF] ?: MapType.NORMAL.name }
            .stateIn(scope, SharingStarted.Eagerly, MapType.NORMAL.name)

    override fun setSelectedGoogleMapType(value: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (value == null) {
                    prefs.remove(KEY_SELECTED_GOOGLE_MAP_TYPE_PREF)
                } else {
                    prefs[KEY_SELECTED_GOOGLE_MAP_TYPE_PREF] = value
                }
            }
        }
    }

    override val selectedCustomTileUrl: StateFlow<String?> =
        dataStore.data.map { it[KEY_SELECTED_CUSTOM_TILE_URL_PREF] }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun setSelectedCustomTileUrl(value: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (value == null) {
                    prefs.remove(KEY_SELECTED_CUSTOM_TILE_URL_PREF)
                } else {
                    prefs[KEY_SELECTED_CUSTOM_TILE_URL_PREF] = value
                }
            }
        }
    }

    override val hiddenLayerUrls: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_HIDDEN_LAYER_URLS_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setHiddenLayerUrls(value: Set<String>) {
        scope.launch { dataStore.edit { it[KEY_HIDDEN_LAYER_URLS_PREF] = value } }
    }

    override val cameraTargetLat: StateFlow<Double> =
        dataStore.data
            .map {
                try {
                    it[KEY_CAMERA_TARGET_LAT_PREF] ?: 0.0
                } catch (_: ClassCastException) {
                    it[floatPreferencesKey(KEY_CAMERA_TARGET_LAT_PREF.name)]?.toDouble() ?: 0.0
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, 0.0)

    override fun setCameraTargetLat(value: Double) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_TARGET_LAT_PREF] = value } }
    }

    override val cameraTargetLng: StateFlow<Double> =
        dataStore.data
            .map {
                try {
                    it[KEY_CAMERA_TARGET_LNG_PREF] ?: 0.0
                } catch (_: ClassCastException) {
                    it[floatPreferencesKey(KEY_CAMERA_TARGET_LNG_PREF.name)]?.toDouble() ?: 0.0
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, 0.0)

    override fun setCameraTargetLng(value: Double) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_TARGET_LNG_PREF] = value } }
    }

    override val cameraZoom: StateFlow<Float> =
        dataStore.data.map { it[KEY_CAMERA_ZOOM_PREF] ?: 7f }.stateIn(scope, SharingStarted.Eagerly, 7f)

    override fun setCameraZoom(value: Float) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_ZOOM_PREF] = value } }
    }

    override val cameraTilt: StateFlow<Float> =
        dataStore.data.map { it[KEY_CAMERA_TILT_PREF] ?: 0f }.stateIn(scope, SharingStarted.Eagerly, 0f)

    override fun setCameraTilt(value: Float) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_TILT_PREF] = value } }
    }

    override val cameraBearing: StateFlow<Float> =
        dataStore.data.map { it[KEY_CAMERA_BEARING_PREF] ?: 0f }.stateIn(scope, SharingStarted.Eagerly, 0f)

    override fun setCameraBearing(value: Float) {
        scope.launch { dataStore.edit { it[KEY_CAMERA_BEARING_PREF] = value } }
    }

    override val networkMapLayers: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_NETWORK_MAP_LAYERS_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setNetworkMapLayers(value: Set<String>) {
        scope.launch { dataStore.edit { it[KEY_NETWORK_MAP_LAYERS_PREF] = value } }
    }

    companion object {
        val KEY_SELECTED_GOOGLE_MAP_TYPE_PREF = stringPreferencesKey("selected_google_map_type")
        val KEY_SELECTED_CUSTOM_TILE_URL_PREF = stringPreferencesKey("selected_custom_tile_url")
        val KEY_HIDDEN_LAYER_URLS_PREF = stringSetPreferencesKey("hidden_layer_urls")
        val KEY_CAMERA_TARGET_LAT_PREF = doublePreferencesKey("camera_target_lat")
        val KEY_CAMERA_TARGET_LNG_PREF = doublePreferencesKey("camera_target_lng")
        val KEY_CAMERA_ZOOM_PREF = floatPreferencesKey("camera_zoom")
        val KEY_CAMERA_TILT_PREF = floatPreferencesKey("camera_tilt")
        val KEY_CAMERA_BEARING_PREF = floatPreferencesKey("camera_bearing")
        val KEY_NETWORK_MAP_LAYERS_PREF = stringSetPreferencesKey("network_map_layers")
    }
}
