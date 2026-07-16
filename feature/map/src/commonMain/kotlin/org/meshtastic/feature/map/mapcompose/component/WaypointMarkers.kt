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
package org.meshtastic.feature.map.mapcompose.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.meshtastic.core.model.geofence.toGeofence
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.feature.map.component.emojiCodePointToString
import org.meshtastic.feature.map.mapcompose.geo.GeoPoint
import org.meshtastic.feature.map.mapcompose.geo.NormalizedPoint
import org.meshtastic.feature.map.mapcompose.geo.PolylineGeometry
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import org.meshtastic.proto.Waypoint
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.moveMarker
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.ui.state.MapState

internal const val WAYPOINT_MARKER_PREFIX = "wpt-"
internal const val PUSHPIN_CODEPOINT = 0x1F4CD // 📍 Round Pushpin

internal fun waypointMarkerId(id: Int): String = "$WAYPOINT_MARKER_PREFIX$id"

internal fun waypointIdFromMarkerId(id: String): Int? =
    if (id.startsWith(WAYPOINT_MARKER_PREFIX)) id.removePrefix(WAYPOINT_MARKER_PREFIX).toIntOrNull() else null

internal fun Waypoint.toNormalized(): NormalizedPoint =
    WebMercator.toNormalized((latitude_i ?: 0) * DEG_D, (longitude_i ?: 0) * DEG_D)

/**
 * Emoji waypoint markers plus their geofence region overlays — the shared twin of the google flavor's
 * `WaypointMarkers` + `WaypointGeofenceOverlay`. Tap handling (edit vs read-only geofence info vs locked) is routed by
 * the marker-click dispatcher in `MapComposeMapView`.
 */
@Composable
internal fun WaypointMarkers(mapState: MapState, waypoints: List<Waypoint>, showWaypoints: Boolean) {
    val waypointsById by rememberUpdatedState(waypoints.associateBy { waypointMarkerId(it.id) })

    val placed = remember(mapState) { mutableMapOf<String, NormalizedPoint>() }
    LaunchedEffect(waypoints, showWaypoints, mapState) {
        val targets =
            if (showWaypoints) {
                waypoints.associateBy({ waypointMarkerId(it.id) }, { it.toNormalized() })
            } else {
                emptyMap()
            }
        (placed.keys - targets.keys).toList().forEach { id ->
            mapState.removeMarker(id)
            placed.remove(id)
        }
        targets.forEach { (id, point) ->
            val previous = placed[id]
            when {
                previous == null ->
                    mapState.addMarker(id = id, x = point.x, y = point.y, zIndex = 3f) {
                        waypointsById[id]?.let { waypoint ->
                            val codePoint = if (waypoint.icon == 0) PUSHPIN_CODEPOINT else waypoint.icon
                            Text(
                                text = emojiCodePointToString(codePoint),
                                fontSize = 32.sp,
                                modifier = Modifier.padding(2.dp),
                            )
                        }
                    }

                previous != point -> mapState.moveMarker(id, point.x, point.y)
            }
            placed[id] = point
        }

        // Geofence regions: a filled circle and/or box polygon per waypoint that defines one.
        val circles =
            if (showWaypoints) {
                waypoints
                    .mapNotNull { wp -> wp.toGeofence()?.circle?.let { "geofence-circle-${wp.id}" to it } }
                    .toMap()
            } else {
                emptyMap()
            }
        mapState.reconcilePaths(
            group = "geofence-circle-",
            targets = circles,
            pathFor = { circle ->
                PolylineGeometry.circlePolygon(circle.centerLat, circle.centerLon, circle.radiusMeters.toDouble())
            },
            color = { GEOFENCE_OVERLAY_COLOR },
            fillAlpha = GEOFENCE_FILL_ALPHA,
        )

        val boxes =
            if (showWaypoints) {
                waypoints.mapNotNull { wp -> wp.toGeofence()?.box?.let { "geofence-box-${wp.id}" to it } }.toMap()
            } else {
                emptyMap()
            }
        mapState.reconcilePaths(
            group = "geofence-box-",
            targets = boxes,
            pathFor = { box ->
                listOf(
                    GeoPoint(box.south, box.west),
                    GeoPoint(box.north, box.west),
                    GeoPoint(box.north, box.east),
                    GeoPoint(box.south, box.east),
                    GeoPoint(box.south, box.west),
                )
            },
            color = { GEOFENCE_OVERLAY_COLOR },
            fillAlpha = GEOFENCE_FILL_ALPHA,
        )
    }
}

// Shared geofence overlay styling (orange, matching the google and fdroid flavors).
internal val GEOFENCE_OVERLAY_COLOR = Color(0xFFFF9800)
internal const val GEOFENCE_FILL_ALPHA = 0.12f
