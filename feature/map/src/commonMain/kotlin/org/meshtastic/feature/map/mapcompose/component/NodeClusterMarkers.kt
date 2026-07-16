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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.meshtastic.core.model.Node
import org.meshtastic.core.ui.component.precisionBitsToMeters
import org.meshtastic.feature.map.mapcompose.geo.NormalizedPoint
import org.meshtastic.feature.map.mapcompose.geo.PolylineGeometry
import org.meshtastic.feature.map.mapcompose.geo.WebMercator
import org.meshtastic.feature.map.mapcompose.geo.toNormalized
import org.meshtastic.feature.map.mapcompose.scrollToBox
import ovh.plrapps.mapcompose.api.ClusterData
import ovh.plrapps.mapcompose.api.Custom
import ovh.plrapps.mapcompose.api.addClusterer
import ovh.plrapps.mapcompose.api.addMarker
import ovh.plrapps.mapcompose.api.moveMarker
import ovh.plrapps.mapcompose.api.removeMarker
import ovh.plrapps.mapcompose.api.setClustererExemptList
import ovh.plrapps.mapcompose.ui.state.MapState
import ovh.plrapps.mapcompose.ui.state.markers.model.RenderingStrategy

internal const val NODE_MARKER_PREFIX = "node-"
private const val NODES_CLUSTERER_ID = "nodes"

internal fun nodeMarkerId(num: Int): String = "$NODE_MARKER_PREFIX$num"

internal fun nodeNumFromMarkerId(id: String): Int? =
    if (id.startsWith(NODE_MARKER_PREFIX)) id.removePrefix(NODE_MARKER_PREFIX).toIntOrNull() else null

/**
 * Clustered node chip markers — the shared twin of the google flavor's `NodeClusterMarkers`.
 *
 * Nodes render as [PulsingNodeChip]s managed by a MapCompose clusterer (our own node is exempt so it never vanishes
 * into a bubble). Clicking a cluster zooms to its bounds, except when every member shares one location — zooming can
 * never separate those, so [onSameLocationCluster] shows a list instead (#same-location dialog parity). Unlike the
 * google renderer's count-based `minClusterSize = 10`, MapCompose clusters purely by marker overlap; the behavioral
 * deviation is documented in the PR.
 *
 * Marker identity is the stable node number (#6197/#6270 convention); chip content reads the node from an updated state
 * so metadata changes (name, colors) recompose in place without marker churn.
 */
@Composable
internal fun NodeClusterMarkers(
    mapState: MapState,
    nodes: List<Node>,
    myNodeNum: Int?,
    showPrecisionCircles: Boolean,
    scope: CoroutineScope,
    onSameLocationCluster: (List<Node>) -> Unit,
) {
    val nodesById by rememberUpdatedState(nodes.associateBy { nodeMarkerId(it.num) })
    val currentOnSameLocation by rememberUpdatedState(onSameLocationCluster)

    // One-time clusterer setup per MapState.
    remember(mapState) {
        mapState.addClusterer(
            id = NODES_CLUSTERER_ID,
            clusterClickBehavior =
            Custom(withDefaultBehavior = false) { data ->
                val members = data.markers.mapNotNull { nodesById[it.id] }
                if (data.isSingleLocation()) {
                    currentOnSameLocation(members)
                } else {
                    val box = WebMercator.boundingBox(data.markers.map { NormalizedPoint(it.x, it.y) })
                    if (box != null) scope.launch { mapState.scrollToBox(box) }
                }
            },
        ) { ids ->
            { ClusterBubble(count = ids.size) }
        }
        true
    }

    LaunchedEffect(myNodeNum, mapState) {
        mapState.setClustererExemptList(NODES_CLUSTERER_ID, myNodeNum?.let { setOf(nodeMarkerId(it)) } ?: emptySet())
    }

    // Keyed add/move/remove reconciliation against the current node list.
    val placed = remember(mapState) { mutableMapOf<String, NormalizedPoint>() }
    LaunchedEffect(nodes, mapState) {
        val targets = nodes.associateBy({ nodeMarkerId(it.num) }, { it.toNormalized() })
        (placed.keys - targets.keys).toList().forEach { id ->
            mapState.removeMarker(id)
            placed.remove(id)
        }
        targets.forEach { (id, point) ->
            val previous = placed[id]
            when {
                previous == null ->
                    mapState.addMarker(
                        id = id,
                        x = point.x,
                        y = point.y,
                        renderingStrategy = RenderingStrategy.Clustering(NODES_CLUSTERER_ID),
                        zIndex = nodesById[id].markerZIndex(myNodeNum),
                    ) {
                        nodesById[id]?.let { node -> PulsingNodeChip(node) }
                    }

                previous != point -> mapState.moveMarker(id, point.x, point.y)
            }
            placed[id] = point
        }
    }

    PrecisionCircles(mapState = mapState, nodes = nodes, show = showPrecisionCircles)
}

private const val Z_INDEX_DEFAULT = 1f
private const val Z_INDEX_NODE = 4f
private const val Z_INDEX_FAVORITE = 4.5f
private const val Z_INDEX_OUR_NODE = 5f

private fun Node?.markerZIndex(myNodeNum: Int?): Float = when {
    this == null -> Z_INDEX_DEFAULT
    num == myNodeNum -> Z_INDEX_OUR_NODE
    isFavorite -> Z_INDEX_FAVORITE
    else -> Z_INDEX_NODE
}

private fun ClusterData.isSingleLocation(): Boolean =
    markers.size > 1 && markers.all { it.x == markers.first().x && it.y == markers.first().y }

/**
 * Position-precision circles for every displayed node, drawn as filled paths. Deviation from google: circles are not
 * suppressed while a node is clustered away (MapCompose gives no per-item clustering callback); at cluster-forming
 * zooms the circles are sub-pixel anyway.
 */
@Composable
private fun PrecisionCircles(mapState: MapState, nodes: List<Node>, show: Boolean) {
    LaunchedEffect(nodes, show, mapState) {
        val wanted =
            if (show) {
                nodes
                    .filter { precisionBitsToMeters(it.position.precision_bits) > 0 }
                    .associateBy { "precision-${it.num}" }
            } else {
                emptyMap()
            }
        mapState.reconcilePaths(
            group = "precision-",
            targets = wanted,
            pathFor = { node ->
                PolylineGeometry.circlePolygon(
                    centerLatitude = node.latitude,
                    centerLongitude = node.longitude,
                    radiusMeters = precisionBitsToMeters(node.position.precision_bits),
                )
            },
            color = { node -> Color(node.colors.second) },
            fillAlpha = PRECISION_FILL_ALPHA,
        )
    }
}

/** The cluster bubble: a filled disc with the member count, matching the stock look of the other renderers. */
@Composable
private fun ClusterBubble(count: Int) {
    Box(
        modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = count.toString(), color = MaterialTheme.colorScheme.onPrimary)
    }
}

private const val PRECISION_FILL_ALPHA = 0.2f
