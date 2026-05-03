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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
import org.meshtastic.core.repository.MapPrefs

@Single
class MapPrefsImpl(
    @Named("MapDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : MapPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val mapStyle: StateFlow<Int> =
        dataStore.data.map { it[KEY_MAP_STYLE_PREF] ?: 0 }.stateIn(scope, SharingStarted.Eagerly, 0)

    override fun setMapStyle(style: Int) {
        scope.launch { dataStore.edit { it[KEY_MAP_STYLE_PREF] = style } }
    }

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

    companion object {
        val KEY_MAP_STYLE_PREF = intPreferencesKey("map_style_id")
        val KEY_SHOW_ONLY_FAVORITES_PREF = booleanPreferencesKey("show_only_favorites")
        val KEY_SHOW_WAYPOINTS_PREF = booleanPreferencesKey("show_waypoints")
        val KEY_SHOW_PRECISION_CIRCLE_PREF = booleanPreferencesKey("show_precision_circle")
        val KEY_LAST_HEARD_FILTER_PREF = longPreferencesKey("last_heard_filter")
        val KEY_LAST_HEARD_TRACK_FILTER_PREF = longPreferencesKey("last_heard_track_filter")
    }
}
