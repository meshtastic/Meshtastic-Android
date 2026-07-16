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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.GeoConstants.DEG_D
import org.meshtastic.core.model.util.metersIn
import org.meshtastic.core.model.util.toString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.alt
import org.meshtastic.core.resources.latitude
import org.meshtastic.core.resources.longitude
import org.meshtastic.core.resources.sats
import org.meshtastic.core.resources.speed
import org.meshtastic.core.resources.timestamp
import org.meshtastic.core.resources.track_point
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.TripOrigin
import org.meshtastic.core.ui.util.formatPositionTime
import org.meshtastic.core.ui.theme.TracerouteColors
import org.meshtastic.feature.map.mapcompose.geo.GeoPoint
import org.meshtastic.feature.map.mapcompose.geo.NormalizedPoint
import org.meshtastic.feature.map.mapcompose.geo.PolylineGeometry
import org.meshtastic.feature.map.mapcompose.geo.toNormalized
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Position
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.removeAllPaths
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.removePaths
import ovh.plrapps.mapcompose.ui.state.MapState

internal const val TRACK_MARKER_PREFIX = "track-"
internal const val HOP_MARKER_PREFIX = "hop-"
private const val TRACEROUTE_OFFSET_METERS = 100.0
private const val MAX_TRACK_SEGMENTS = 100

internal fun trackMarkerId(time: Int): String = "$TRACK_MARKER_PREFIX$time"

internal fun trackTimeFromMarkerId(id: String): Int? =
    if (id.startsWith(TRACK_MARKER_PREFIX)) id.removePrefix(TRACK_MARKER_PREFIX).toIntOrNull() else null

internal fun Position.toGeoPoint(): GeoPoint = GeoPoint((latitude_i ?: 0) * DEG_D, (longitude_i ?: 0) * DEG_D)

/**
 * A focused node's historical position track: per-segment polylines whose alpha fades from oldest to newest
 * (approximating the google renderer's gradient), a [NodeChip][PulsingNodeChip] on the newest position, and dots for
 * older positions. Tapping any point invokes [onPositionSelected] for list synchronization.
 */
@Composable
internal fun NodeTrackLayer(
    mapState: MapState,
    focusedNode: Node?,
    sortedPositions: List<Position>,
    selectedPositionTime: Int?,
) {
    val node = focusedNode ?: return
    val selectedColor = MaterialTheme.colorScheme.primary
    val nodeState by rememberUpdatedState(node)

    val placed = remember(mapState) { mutableSetOf<String>() }
    LaunchedEffect(sortedPositions, selectedPositionTime, mapState) {
        // Track contents change wholesale (filter changes, new fixes); markers are cheap enough to rebuild.
        placed.forEach { mapState.removeMarker(it) }
        placed.clear()
        mapState.removePaths { it.startsWith("track-seg-") }

        sortedPositions.forEachIndexed { index, position ->
            val id = trackMarkerId(position.time)
            val point = position.toNormalized()
            val alpha = if (sortedPositions.size > 1) index.toFloat() / (sortedPositions.size - 1) else 1f
            val isSelected = position.time == selectedPositionTime
            val isNewest = index == sortedPositions.lastIndex
            mapState.addMarker(id = id, x = point.x, y = point.y, zIndex = if (isNewest || isSelected) 5f else 1f + alpha) {
                if (isNewest) {
                    PulsingNodeChip(nodeState)
                } else {
                    Icon(
                        imageVector = MeshtasticIcons.TripOrigin,
                        contentDescription = stringResource(Res.string.track_point),
                        tint = if (isSelected) selectedColor else Color(nodeState.colors.second).copy(alpha = alpha),
                        modifier = if (isSelected) Modifier.size(32.dp) else Modifier,
                    )
                }
            }
            placed.add(id)
        }

        // Alpha-graded segments, downsampled so very long logs don't create thousands of paths.
        if (sortedPositions.size > 1) {
            val step = ((sortedPositions.size - 1) + MAX_TRACK_SEGMENTS - 1) / MAX_TRACK_SEGMENTS
            val sampled = sortedPositions.filterIndexed { i, _ -> i % step == 0 || i == sortedPositions.lastIndex }
            sampled.windowed(size = 2, step = 1).forEachIndexed { index, segment ->
                val alpha = if (sampled.size > 2) index.toFloat() / (sampled.size - 2) else 1f
                mapState.setLatLonPath(
                    id = "track-seg-$index",
                    points = segment.map { it.toGeoPoint() },
                    color = Color(nodeState.colors.second).copy(alpha = alpha),
                    width = 4.dp,
                    zIndex = 0.6f,
                )
            }
        }
    }
}

/** State computed once per traceroute overlay: the offset forward/return polylines in lat/lon space. */
internal data class TracerouteLines(val forward: List<GeoPoint>, val back: List<GeoPoint>)

internal fun tracerouteLines(forwardPoints: List<GeoPoint>, returnPoints: List<GeoPoint>): TracerouteLines {
    val headingReference =
        when {
            forwardPoints.size >= 2 -> forwardPoints
            returnPoints.size >= 2 -> returnPoints
            else -> emptyList()
        }
    return TracerouteLines(
        forward = PolylineGeometry.offsetPolyline(forwardPoints, TRACEROUTE_OFFSET_METERS, headingReference, 1.0),
        back = PolylineGeometry.offsetPolyline(returnPoints, TRACEROUTE_OFFSET_METERS, headingReference, -1.0),
    )
}

/**
 * Traceroute visualization: forward and return routes drawn side-by-side (offset perpendicular to the route so they
 * don't overlap) plus a chip marker per resolvable hop, keyed by node number (#6197/#6270).
 */
@Composable
internal fun TracerouteLayer(
    mapState: MapState,
    displayNodes: List<Node>,
    forwardPoints: List<GeoPoint>,
    returnPoints: List<GeoPoint>,
) {
    val nodesById by rememberUpdatedState(displayNodes.associateBy { "$HOP_MARKER_PREFIX${it.num}" })

    val placed = remember(mapState) { mutableMapOf<String, NormalizedPoint>() }
    LaunchedEffect(displayNodes, forwardPoints, returnPoints, mapState) {
        val lines = tracerouteLines(forwardPoints, returnPoints)
        mapState.removeAllPaths()
        if (forwardPoints.size >= 2) {
            mapState.setLatLonPath(
                id = "traceroute-forward",
                points = lines.forward,
                color = TracerouteColors.OutgoingRoute,
                width = 5.dp,
                zIndex = 3.0f,
            )
        }
        if (returnPoints.size >= 2) {
            mapState.setLatLonPath(
                id = "traceroute-return",
                points = lines.back,
                color = TracerouteColors.ReturnRoute,
                width = 4.dp,
                zIndex = 2.5f,
            )
        }

        val targets =
            displayNodes
                .filter { it.validPosition != null }
                .associateBy({ "$HOP_MARKER_PREFIX${it.num}" }, { it.toNormalized() })
        (placed.keys - targets.keys).toList().forEach { id ->
            mapState.removeMarker(id)
            placed.remove(id)
        }
        targets.forEach { (id, point) ->
            if (placed[id] == null) {
                mapState.addMarker(id = id, x = point.x, y = point.y, zIndex = 4f) {
                    nodesById[id]?.let { node -> PulsingNodeChip(node) }
                }
            }
            placed[id] = point
        }
    }
}

/** Position details shown in a callout when a track point is tapped — port of the google info-window content. */
@Composable
internal fun PositionCalloutContent(position: Position, displayUnits: DisplayUnits) {
    @Composable
    fun PositionRow(label: String, value: String) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
    }

    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            PositionRow(
                label = stringResource(Res.string.latitude),
                value = NumberFormatter.format((position.latitude_i ?: 0) * DEG_D, 5),
            )
            PositionRow(
                label = stringResource(Res.string.longitude),
                value = NumberFormatter.format((position.longitude_i ?: 0) * DEG_D, 5),
            )
            PositionRow(label = stringResource(Res.string.sats), value = position.sats_in_view.toString())
            PositionRow(
                label = stringResource(Res.string.alt),
                value = (position.altitude ?: 0).metersIn(displayUnits).toString(displayUnits),
            )
            PositionRow(label = stringResource(Res.string.speed), value = "${position.ground_speed ?: 0} m/s")
            PositionRow(label = stringResource(Res.string.timestamp), value = position.formatPositionTime())
        }
    }
}
