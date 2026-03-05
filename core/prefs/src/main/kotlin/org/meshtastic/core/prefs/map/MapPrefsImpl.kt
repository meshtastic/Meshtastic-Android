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
package org.meshtastic.core.prefs.map

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MapSharedPreferences
import org.meshtastic.core.prefs.preferenceFlow
import org.meshtastic.core.repository.MapPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapPrefsImpl
@Inject
constructor(
    @MapSharedPreferences private val prefs: SharedPreferences,
    dispatchers: CoroutineDispatchers,
) : MapPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val mapStyle: StateFlow<Int> =
        prefs
            .preferenceFlow("map_style_id") { p, k -> p.getInt(k, 0) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getInt("map_style_id", 0))

    override fun setMapStyle(value: Int) {
        prefs.edit { putInt("map_style_id", value) }
    }

    override val showOnlyFavorites: StateFlow<Boolean> =
        prefs
            .preferenceFlow("show_only_favorites") { p, k -> p.getBoolean(k, false) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean("show_only_favorites", false))

    override fun setShowOnlyFavorites(value: Boolean) {
        prefs.edit { putBoolean("show_only_favorites", value) }
    }

    override val showWaypointsOnMap: StateFlow<Boolean> =
        prefs
            .preferenceFlow("show_waypoints") { p, k -> p.getBoolean(k, true) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean("show_waypoints", true))

    override fun setShowWaypointsOnMap(value: Boolean) {
        prefs.edit { putBoolean("show_waypoints", value) }
    }

    override val showPrecisionCircleOnMap: StateFlow<Boolean> =
        prefs
            .preferenceFlow("show_precision_circle") { p, k -> p.getBoolean(k, true) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getBoolean("show_precision_circle", true))

    override fun setShowPrecisionCircleOnMap(value: Boolean) {
        prefs.edit { putBoolean("show_precision_circle", value) }
    }

    override val lastHeardFilter: StateFlow<Long> =
        prefs
            .preferenceFlow("last_heard_filter") { p, k -> p.getLong(k, 0L) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getLong("last_heard_filter", 0L))

    override fun setLastHeardFilter(value: Long) {
        prefs.edit { putLong("last_heard_filter", value) }
    }

    override val lastHeardTrackFilter: StateFlow<Long> =
        prefs
            .preferenceFlow("last_heard_track_filter") { p, k -> p.getLong(k, 0L) }
            .stateIn(scope, SharingStarted.Eagerly, prefs.getLong("last_heard_track_filter", 0L))

    override fun setLastHeardTrackFilter(value: Long) {
        prefs.edit { putLong("last_heard_track_filter", value) }
    }
}
