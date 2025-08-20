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
import com.google.maps.android.compose.MapType

/** Interface for prefs specific to Google Maps. For general map prefs, see MapPrefs. */
interface GoogleMapsPrefs {
    var selectedGoogleMapType: String?
    var selectedCustomTileUrl: String?
}

class GoogleMapsPrefsImpl(prefs: SharedPreferences) : GoogleMapsPrefs {
    override var selectedGoogleMapType: String? by
        NullableStringPrefDelegate(prefs, "selected_google_map_type", MapType.NORMAL.name)
    override var selectedCustomTileUrl: String? by NullableStringPrefDelegate(prefs, "selected_custom_tile_url", null)
}
