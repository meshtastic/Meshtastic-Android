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

package com.geeksville.mesh.android.prefs

import android.content.SharedPreferences

/** Interface for general map prefs. For Google-specific prefs, see GoogleMapsPrefs. */
interface MapPrefs {
    var mapStyle: Int
    var showOnlyFavorites: Boolean
    var showWaypointsOnMap: Boolean
    var showPrecisionCircleOnMap: Boolean
}

class MapPrefsImpl(prefs: SharedPreferences) : MapPrefs {
    override var mapStyle: Int by PrefDelegate(prefs, "map_style_id", 0)
    override var showOnlyFavorites: Boolean by PrefDelegate(prefs, "show_only_favorites", false)
    override var showWaypointsOnMap: Boolean by PrefDelegate(prefs, "show_waypoints", true)
    override var showPrecisionCircleOnMap: Boolean by PrefDelegate(prefs, "show_precision_circle", true)
}
