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

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MapDataStore
import org.meshtastic.core.repository.MapCameraPosition
import org.meshtastic.core.repository.MapPrefs

@Single
@Suppress("TooManyFunctions")
class MapPrefsImpl(private val dataStore: MapDataStore, dispatchers: CoroutineDispatchers) : MapPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val mapStyle: StateFlow<Int> =
        dataStore.data.map { it[KEY_MAP_STYLE_PREF] ?: 0 }.stateIn(scope, SharingStarted.Eagerly, 0)

    override fun setMapStyle(style: Int) {
        scope.launch { dataStore.edit { it[KEY_MAP_STYLE_PREF] = style } }
    }

    override suspend fun awaitMapStyle(): Int = dataStore.data.map { it[KEY_MAP_STYLE_PREF] ?: 0 }.first()

    override val showOnlyFavorites: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_ONLY_FAVORITES_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setShowOnlyFavorites(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_ONLY_FAVORITES_PREF] = show } }
    }

    override val showWaypointsOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_WAYPOINTS_PREF] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShowWaypointsOnMap(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_WAYPOINTS_PREF] = show } }
    }

    override val showPrecisionCircleOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_PRECISION_CIRCLE_PREF] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setShowPrecisionCircleOnMap(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_PRECISION_CIRCLE_PREF] = show } }
    }

    override val lastHeardFilter: StateFlow<Long> =
        dataStore.data.map { it[KEY_LAST_HEARD_FILTER_PREF] ?: 0L }.stateIn(scope, SharingStarted.Eagerly, 0L)

    override fun setLastHeardFilter(seconds: Long) {
        scope.launch { dataStore.edit { it[KEY_LAST_HEARD_FILTER_PREF] = seconds } }
    }

    override val lastHeardTrackFilter: StateFlow<Long> =
        dataStore.data.map { it[KEY_LAST_HEARD_TRACK_FILTER_PREF] ?: 0L }.stateIn(scope, SharingStarted.Eagerly, 0L)

    override fun setLastHeardTrackFilter(seconds: Long) {
        scope.launch { dataStore.edit { it[KEY_LAST_HEARD_TRACK_FILTER_PREF] = seconds } }
    }

    override val onlyOnlineOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_ONLINE_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setOnlyOnlineOnMap(only: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_ONLINE_PREF] = only } }
    }

    override val onlyDirectOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_ONLY_DIRECT_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setOnlyDirectOnMap(only: Boolean) {
        scope.launch { dataStore.edit { it[KEY_ONLY_DIRECT_PREF] = only } }
    }

    override val excludeMqttOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_EXCLUDE_MQTT_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setExcludeMqttOnMap(exclude: Boolean) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDE_MQTT_PREF] = exclude } }
    }

    override val showIgnoredOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_SHOW_IGNORED_PREF] ?: false }.stateIn(scope, SharingStarted.Eagerly, false)

    override fun setShowIgnoredOnMap(show: Boolean) {
        scope.launch { dataStore.edit { it[KEY_SHOW_IGNORED_PREF] = show } }
    }

    override val includeUnknownOnMap: StateFlow<Boolean> =
        dataStore.data.map { it[KEY_INCLUDE_UNKNOWN_PREF] ?: true }.stateIn(scope, SharingStarted.Eagerly, true)

    override fun setIncludeUnknownOnMap(include: Boolean) {
        scope.launch { dataStore.edit { it[KEY_INCLUDE_UNKNOWN_PREF] = include } }
    }

    override val excludedMapRoles: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_EXCLUDED_ROLES_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun setExcludedMapRoles(roles: Set<String>) {
        scope.launch { dataStore.edit { it[KEY_EXCLUDED_ROLES_PREF] = roles } }
    }

    override val hiddenLayerUrls: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_HIDDEN_LAYER_URLS_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun updateHiddenLayerUrls(transform: (Set<String>) -> Set<String>) {
        // Compute the new set inside the edit transaction (DataStore serializes edits) to avoid lost updates.
        scope.launch {
            dataStore.edit { it[KEY_HIDDEN_LAYER_URLS_PREF] = transform(it[KEY_HIDDEN_LAYER_URLS_PREF] ?: emptySet()) }
        }
    }

    // dataStore.data's first emission is the persisted value (unlike the eager StateFlow, which starts at emptySet()).
    override suspend fun awaitHiddenLayerUrls(): Set<String> =
        dataStore.data.map { it[KEY_HIDDEN_LAYER_URLS_PREF] ?: emptySet() }.first()

    override val layerOpacity: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_LAYER_OPACITY_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun updateLayerOpacity(transform: (Set<String>) -> Set<String>) {
        scope.launch {
            dataStore.edit { it[KEY_LAYER_OPACITY_PREF] = transform(it[KEY_LAYER_OPACITY_PREF] ?: emptySet()) }
        }
    }

    override val networkMapLayers: StateFlow<Set<String>> =
        dataStore.data
            .map { it[KEY_NETWORK_MAP_LAYERS_PREF] ?: emptySet() }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    override fun updateNetworkMapLayers(transform: (Set<String>) -> Set<String>) {
        scope.launch {
            dataStore.edit {
                it[KEY_NETWORK_MAP_LAYERS_PREF] = transform(it[KEY_NETWORK_MAP_LAYERS_PREF] ?: emptySet())
            }
        }
    }

    override suspend fun awaitNetworkMapLayers(): Set<String> =
        dataStore.data.map { it[KEY_NETWORK_MAP_LAYERS_PREF] ?: emptySet() }.first()

    override fun setCameraPosition(position: MapCameraPosition) {
        scope.launch {
            dataStore.edit {
                it[KEY_CAMERA_LATITUDE] = position.latitude
                it[KEY_CAMERA_LONGITUDE] = position.longitude
                it[KEY_CAMERA_ZOOM] = position.zoom
            }
        }
    }

    override suspend fun awaitCameraPosition(): MapCameraPosition? = dataStore.data
        .map { preferences ->
            val latitude = preferences[KEY_CAMERA_LATITUDE] ?: return@map null
            val longitude = preferences[KEY_CAMERA_LONGITUDE] ?: return@map null
            val zoom = preferences[KEY_CAMERA_ZOOM] ?: return@map null
            MapCameraPosition(latitude, longitude, zoom)
        }
        .first()

    companion object {
        val KEY_MAP_STYLE_PREF = intPreferencesKey("map_style_id")
        val KEY_SHOW_ONLY_FAVORITES_PREF = booleanPreferencesKey("show_only_favorites")
        val KEY_SHOW_WAYPOINTS_PREF = booleanPreferencesKey("show_waypoints")
        val KEY_SHOW_PRECISION_CIRCLE_PREF = booleanPreferencesKey("show_precision_circle")
        val KEY_LAST_HEARD_FILTER_PREF = longPreferencesKey("last_heard_filter")
        val KEY_LAST_HEARD_TRACK_FILTER_PREF = longPreferencesKey("last_heard_track_filter")
        val KEY_HIDDEN_LAYER_URLS_PREF = stringSetPreferencesKey("hidden_layer_urls")
        val KEY_NETWORK_MAP_LAYERS_PREF = stringSetPreferencesKey("network_map_layers")
        val KEY_LAYER_OPACITY_PREF = stringSetPreferencesKey("layer_opacity")
        val KEY_ONLY_ONLINE_PREF = booleanPreferencesKey("map_only_online")
        val KEY_ONLY_DIRECT_PREF = booleanPreferencesKey("map_only_direct")
        val KEY_EXCLUDE_MQTT_PREF = booleanPreferencesKey("map_exclude_mqtt")
        val KEY_SHOW_IGNORED_PREF = booleanPreferencesKey("map_show_ignored")
        val KEY_INCLUDE_UNKNOWN_PREF = booleanPreferencesKey("map_include_unknown")
        val KEY_EXCLUDED_ROLES_PREF = stringSetPreferencesKey("map_excluded_roles")
        val KEY_CAMERA_LATITUDE = doublePreferencesKey("camera_latitude")
        val KEY_CAMERA_LONGITUDE = doublePreferencesKey("camera_longitude")
        val KEY_CAMERA_ZOOM = doublePreferencesKey("camera_zoom")
    }
}
