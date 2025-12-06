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

package org.meshtastic.core.prefs.map

import android.content.SharedPreferences
import org.meshtastic.core.prefs.DoublePrefDelegate
import org.meshtastic.core.prefs.NullableStringPrefDelegate
import org.meshtastic.core.prefs.PrefDelegate
import org.meshtastic.core.prefs.di.MapLibreSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Interface for prefs specific to MapLibre. For general map prefs, see MapPrefs. */
interface MapLibrePrefs {
    var cameraTargetLat: Double
    var cameraTargetLng: Double
    var cameraZoom: Double
    var cameraBearing: Double
    var cameraTilt: Double
    var shouldRestoreCameraPosition: Boolean
    var markerColorMode: String
    var clusteringEnabled: Boolean
    var heatmapEnabled: Boolean
    var baseStyleIndex: Int
    var customTileUrl: String?
    var usingCustomTiles: Boolean
}

@Singleton
class MapLibrePrefsImpl @Inject constructor(@MapLibreSharedPreferences prefs: SharedPreferences) : MapLibrePrefs {
    override var cameraTargetLat: Double by DoublePrefDelegate(prefs, "camera_target_lat", 0.0)
    override var cameraTargetLng: Double by DoublePrefDelegate(prefs, "camera_target_lng", 0.0)
    override var cameraZoom: Double by DoublePrefDelegate(prefs, "camera_zoom", 15.0)
    override var cameraBearing: Double by DoublePrefDelegate(prefs, "camera_bearing", 0.0)
    override var cameraTilt: Double by DoublePrefDelegate(prefs, "camera_tilt", 0.0)
    override var shouldRestoreCameraPosition: Boolean by PrefDelegate(prefs, "should_restore_camera_position", false)
    override var markerColorMode: String by PrefDelegate(prefs, "marker_color_mode", "NODE")
    override var clusteringEnabled: Boolean by PrefDelegate(prefs, "clustering_enabled", true)
    override var heatmapEnabled: Boolean by PrefDelegate(prefs, "heatmap_enabled", false)
    override var baseStyleIndex: Int by PrefDelegate(prefs, "base_style_index", 0)
    override var customTileUrl: String? by NullableStringPrefDelegate(prefs, "custom_tile_url", null)
    override var usingCustomTiles: Boolean by PrefDelegate(prefs, "using_custom_tiles", false)
}
