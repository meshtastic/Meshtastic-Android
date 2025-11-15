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
import com.google.maps.android.compose.MapType
import org.meshtastic.core.prefs.DoublePrefDelegate
import org.meshtastic.core.prefs.FloatPrefDelegate
import org.meshtastic.core.prefs.NullableStringPrefDelegate
import org.meshtastic.core.prefs.StringSetPrefDelegate
import org.meshtastic.core.prefs.di.GoogleMapsSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Interface for prefs specific to Google Maps. For general map prefs, see MapPrefs. */
interface GoogleMapsPrefs {
    var selectedGoogleMapType: String?
    var selectedCustomTileUrl: String?
    var hiddenLayerUrls: Set<String>
    var cameraTargetLat: Double
    var cameraTargetLng: Double
    var cameraZoom: Float
    var cameraTilt: Float
    var cameraBearing: Float
}

@Singleton
class GoogleMapsPrefsImpl @Inject constructor(@GoogleMapsSharedPreferences prefs: SharedPreferences) : GoogleMapsPrefs {
    override var selectedGoogleMapType: String? by
        NullableStringPrefDelegate(prefs, "selected_google_map_type", MapType.NORMAL.name)
    override var selectedCustomTileUrl: String? by NullableStringPrefDelegate(prefs, "selected_custom_tile_url", null)
    override var hiddenLayerUrls: Set<String> by StringSetPrefDelegate(prefs, "hidden_layer_urls", emptySet())
    override var cameraTargetLat: Double by DoublePrefDelegate(prefs, "camera_target_lat", 0.0)
    override var cameraTargetLng: Double by DoublePrefDelegate(prefs, "camera_target_lng", 0.0)
    override var cameraZoom: Float by FloatPrefDelegate(prefs, "camera_zoom", 7f)
    override var cameraTilt: Float by FloatPrefDelegate(prefs, "camera_tilt", 0f)
    override var cameraBearing: Float by FloatPrefDelegate(prefs, "camera_bearing", 0f)
}
