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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.precisionBitsToMeters
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.Temperature
import org.meshtastic.core.ui.util.DiscoveryNeighborType
import org.meshtastic.feature.map.mapcompose.MapComposeMode
import org.meshtastic.feature.map.mapcompose.geo.PolylineGeometry
import org.meshtastic.feature.map.mapcompose.geo.GeoPoint
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import org.meshtastic.feature.map.mapcompose.geo.toNormalized
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.removeAllMarkers
import ovh.plrapps.mapcompose.api.removeAllPaths
import ovh.plrapps.mapcompose.ui.state.MapState

private val DirectColor = Color(0xFF4CAF50)
private val MeshColor = Color(0xFF2196F3)
private val UserColor = Color(0xFFFF9800)
private val DirectLineColor = Color(0xFF4CAF50).copy(alpha = 0.5f)

/**
 * Discovery-map content: color-coded markers per discovered node (green = direct, blue = mesh), polylines from the
 * user position to direct neighbors — the shared twin of `DiscoveryGoogleMap`'s content.
 */
@Composable
internal fun DiscoveryLayer(mapState: MapState, mode: MapComposeMode.Discovery) {
    LaunchedEffect(mode, mapState) {
        mapState.removeAllMarkers()
        mapState.removeAllPaths()

        val hasUser = mode.userLatitude != 0.0 || mode.userLongitude != 0.0
        val validNodes = mode.nodes.filter { it.latitude != 0.0 || it.longitude != 0.0 }

        if (hasUser) {
            val p = WebMercator.toNormalized(mode.userLatitude, mode.userLongitude)
            mapState.addMarker(id = "discovery-user", x = p.x, y = p.y, zIndex = 5f) {
                DiscoveryMarkerChip(label = "You", color = UserColor)
            }
        }

        validNodes.forEachIndexed { index, node ->
            val p = WebMercator.toNormalized(node.latitude, node.longitude)
            val color =
                when (node.neighborType) {
                    DiscoveryNeighborType.DIRECT -> DirectColor
                    DiscoveryNeighborType.MESH -> MeshColor
                }
            mapState.addMarker(id = "discovery-$index", x = p.x, y = p.y, zIndex = 4f) {
                // Icon getters are composable, so resolve them inside the marker content.
                val icon = if (node.isSensorNode) MeshtasticIcons.Temperature else MeshtasticIcons.Person
                DiscoveryMarkerChip(label = node.shortName ?: "?", color = color, icon = icon)
            }
        }

        if (hasUser) {
            validNodes
                .filter { it.neighborType == DiscoveryNeighborType.DIRECT }
                .forEachIndexed { index, node ->
                    mapState.setLatLonPath(
                        id = "discovery-line-$index",
                        points =
                        listOf(
                            GeoPoint(mode.userLatitude, mode.userLongitude),
                            GeoPoint(node.latitude, node.longitude),
                        ),
                        color = DirectLineColor,
                        width = 2.dp,
                    )
                }
        }
    }
}

@Composable
private fun DiscoveryMarkerChip(label: String, color: Color, icon: ImageVector? = null) {
    Surface(color = color, shape = MaterialTheme.shapes.small, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

/** Inline single-node map content: the node's chip plus its precision circle; gestures are disabled by the caller. */
@Composable
internal fun InlineLayer(mapState: MapState, node: Node, showPrecision: Boolean) {
    LaunchedEffect(node.num, mapState) {
        mapState.removeAllMarkers()
        mapState.removeAllPaths()

        val precisionMeters = precisionBitsToMeters(node.position.precision_bits)
        if (showPrecision && precisionMeters > 0) {
            val color = Color(node.colors.second)
            mapState.setLatLonPath(
                id = "inline-precision",
                points = PolylineGeometry.circlePolygon(node.latitude, node.longitude, precisionMeters),
                color = color,
                fillColor = color.copy(alpha = 0.2f),
                width = 2.dp,
            )
        }

        val p = node.toNormalized()
        mapState.addMarker(id = nodeMarkerId(node.num), x = p.x, y = p.y, clickable = false) {
            PulsingNodeChip(node)
        }
    }
}
