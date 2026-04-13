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
package org.meshtastic.feature.map.util

import org.maplibre.spatialk.geojson.Position as GeoPosition

/** Meshtastic stores lat/lng as integer microdegrees; multiply by this to get decimal degrees. */
const val COORDINATE_SCALE = 1e-7

/**
 * Convert Meshtastic integer microdegree coordinates to a [GeoPosition], returning `null` if both latitude and
 * longitude are zero (indicating no valid position).
 */
fun toGeoPositionOrNull(latI: Int?, lngI: Int?): GeoPosition? {
    val lat = (latI ?: 0) * COORDINATE_SCALE
    val lng = (lngI ?: 0) * COORDINATE_SCALE
    return if (lat == 0.0 && lng == 0.0) null else GeoPosition(longitude = lng, latitude = lat)
}
