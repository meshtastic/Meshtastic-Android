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
package org.meshtastic.app.map.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.algo.NonHierarchicalDistanceBasedAlgorithm
import com.google.maps.android.clustering.algo.NonHierarchicalViewBasedAlgorithm
import com.google.maps.android.clustering.algo.ScreenBasedAlgorithmAdapter
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.ClusteringMarkerProperties
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.stackBadgeLabel

private const val MIN_CLUSTER_SIZE = 10

// Matches the MapLibre map's precision fill, so both maps shade a degraded position alike.
private const val PRECISION_FILL_ALPHA = 0.15f

// BadgedBox draws the badge past its anchor's top end corner, and a marker is rasterized at its measured size, so the
// marker is padded by this much above and to the sides to keep the badge unclipped. Never below: the marker anchors on
// its bottom edge, and bottom padding would lift every chip off its point.
private val STACK_BADGE_INSET = 12.dp

/** A node's chip, with a Material 3 count badge when it stands for several nodes at one spot. */
@Composable
private fun StackedNodeChip(item: NodeClusterItem, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = STACK_BADGE_INSET, top = STACK_BADGE_INSET, end = STACK_BADGE_INSET)) {
        BadgedBox(
            badge = {
                if (item.stackMembers.size > 1) {
                    // Secondary rather than the default error color: a count of nodes is not an alert.
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                        Text(stackBadgeLabel(item.stackMembers.size))
                    }
                }
            },
        ) {
            PulsingNodeChip(node = item.node)
        }
    }
}

/**
 * Threshold for switching to [NonHierarchicalViewBasedAlgorithm], matching that class's own KDoc describing itself as
 * being for "large numbers of items (>1000 markers)".
 */
private const val VIEW_BASED_ALGORITHM_NODE_THRESHOLD = 1000

/**
 * Renders node markers with clustering via the library's [Clustering] composable.
 *
 * Each unclustered item is composed as a [PulsingNodeChip] (`clusterItemContent`), badged with a count when it stands
 * for several nodes at one spot, with its z-index forwarded through [ClusteringMarkerProperties]; cluster bubbles keep
 * the library's default rendering. Precision circles are drawn for every item, clustered or not.
 *
 * Requires maps-compose >= 8.4.0: earlier versions composed cluster items into a detached `ComposeView` that carried no
 * lifecycle/saved-state owners, crashing under the Navigation 3 hierarchy (fixed upstream in
 * googlemaps/android-maps-compose#930, which this file previously worked around with a custom [DefaultClusterRenderer]
 * assigning pre-baked bitmaps).
 *
 * Below [VIEW_BASED_ALGORITHM_NODE_THRESHOLD] nodes, clusters with the default `NonHierarchicalDistanceBasedAlgorithm`
 * (pans smoother, and clustering the whole node set is cheap at this scale). Above it, switches to
 * [NonHierarchicalViewBasedAlgorithm] to avoid a spread-out mesh returning nearly every node as its own unclustered
 * result at high zoom (#4544) - at the cost of its zero-margin, camera-idle-only viewport bounds popping markers in
 * abruptly while panning.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun NodeClusterMarkers(
    nodeClusterItems: List<NodeClusterItem>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    navigateToNodeDetails: (Int) -> Unit,
    onClusterClick: (Cluster<NodeClusterItem>) -> Boolean,
    onStackClick: (List<NodeClusterItem>) -> Unit,
) {
    val configuration = LocalConfiguration.current
    // One circle per stack, drawn whether or not the stack sits inside a cluster bubble, as the Apple and MapLibre maps
    // do: the circle says where the nodes could be, which a count bubble does not.
    if (mapFilterState.showPrecisionCircle) {
        nodeClusterItems.forEach { item ->
            val precisionMeters = item.getPrecisionMeters()
            if (precisionMeters != null && precisionMeters > 0) {
                key(item.node.num) {
                    Circle(
                        center = item.position,
                        radius = precisionMeters,
                        fillColor = Color(item.node.colors.second).copy(alpha = PRECISION_FILL_ALPHA),
                        strokeColor = Color(item.node.colors.second),
                        strokeWidth = 2f,
                        zIndex = 0f,
                    )
                }
            }
        }
    }
    Clustering(
        items = nodeClusterItems,
        onClusterClick = onClusterClick,
        // A tap goes where the MapLibre map's does: a stack lists its nodes, a single node opens its details.
        onClusterItemClick = { item ->
            if (item.stackMembers.size > 1) onStackClick(item.stackMembers) else navigateToNodeDetails(item.node.num)
            true
        },
        clusterItemContent = { item ->
            ClusteringMarkerProperties(zIndex = item.zIndex)
            StackedNodeChip(item)
        },
        onClusterManager = { clusterManager ->
            // The library renderer extends DefaultClusterRenderer; raise its clustering threshold once.
            val renderer = clusterManager.renderer as? DefaultClusterRenderer<*>
            if (renderer != null && renderer.minClusterSize != MIN_CLUSTER_SIZE) {
                renderer.minClusterSize = MIN_CLUSTER_SIZE
                clusterManager.cluster()
            }
            // Only swap on a threshold crossing; otherwise just resize in place - replacing re-migrates every item.
            val algorithm = clusterManager.algorithm
            val needsViewBasedAlgorithm = nodeClusterItems.size > VIEW_BASED_ALGORITHM_NODE_THRESHOLD
            when {
                needsViewBasedAlgorithm && algorithm is NonHierarchicalViewBasedAlgorithm<*> ->
                    algorithm.updateViewSize(configuration.screenWidthDp, configuration.screenHeightDp)

                needsViewBasedAlgorithm ->
                    clusterManager.setAlgorithm(
                        NonHierarchicalViewBasedAlgorithm(configuration.screenWidthDp, configuration.screenHeightDp),
                    )

                algorithm is NonHierarchicalViewBasedAlgorithm<*> ->
                    // Kotlin won't resolve setAlgorithm's Algorithm<T> overload for a plain
                    // NonHierarchicalDistanceBasedAlgorithm; wrap it to force the ScreenBasedAlgorithm<T> overload.
                    clusterManager.setAlgorithm(
                        ScreenBasedAlgorithmAdapter(NonHierarchicalDistanceBasedAlgorithm<NodeClusterItem>()),
                    )
            }
        },
    )
}
