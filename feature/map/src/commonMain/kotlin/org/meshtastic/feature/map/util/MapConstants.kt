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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position as GeoPosition

/** Meshtastic stores lat/lng as integer microdegrees; multiply by this to get decimal degrees. */
internal const val COORDINATE_SCALE = 1e-7

/** Standard radius for node and hop marker circles across all map composables. */
internal val NODE_MARKER_RADIUS: Dp = 8.dp

/** Standard stroke width for marker circle outlines across all map composables. */
internal val MARKER_STROKE_WIDTH: Dp = 2.dp

/** Opacity for precision circle strokes (shared between main map and inline map). */
internal const val PRECISION_CIRCLE_STROKE_ALPHA = 0.3f

/**
 * Convert Meshtastic integer microdegree coordinates to a [GeoPosition], returning `null` if both latitude and
 * longitude are zero (indicating no valid position).
 */
internal fun toGeoPositionOrNull(latI: Int?, lngI: Int?): GeoPosition? {
    val lat = (latI ?: 0) * COORDINATE_SCALE
    val lng = (lngI ?: 0) * COORDINATE_SCALE
    return if (lat == 0.0 && lng == 0.0) null else GeoPosition(longitude = lng, latitude = lat)
}

/**
 * Compute a [BoundingBox] that encloses all [positions], or `null` if fewer than 2 positions are provided. Used by
 * [NodeTrackMap][org.meshtastic.feature.map.component.NodeTrackMap] and
 * [TracerouteMap][org.meshtastic.feature.map.component.TracerouteMap] to fit the camera to track/route bounds.
 */
internal fun computeBoundingBox(positions: List<GeoPosition>): BoundingBox? {
    if (positions.size < 2) return null
    val lats = positions.map { it.latitude }
    val lngs = positions.map { it.longitude }
    return BoundingBox(
        southwest = GeoPosition(longitude = lngs.min(), latitude = lats.min()),
        northeast = GeoPosition(longitude = lngs.max(), latitude = lats.max()),
    )
}
