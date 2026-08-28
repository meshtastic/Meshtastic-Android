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

import android.graphics.Bitmap

/**
 * A converted import: the GeoJSON, plus any images the archive carried.
 *
 * [images] is keyed by each file's path inside the archive, which is what a KMZ placemark's `<href>` names and what the
 * Google renderer's image cache is keyed by. Empty for a bare `.kml`, whose icons are ordinary URLs.
 */
class ImportedKml(val geoJson: String, val images: Map<String, Bitmap>)
