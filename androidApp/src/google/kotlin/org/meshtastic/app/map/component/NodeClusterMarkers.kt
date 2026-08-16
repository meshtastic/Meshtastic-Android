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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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

private const val MIN_CLUSTER_SIZE = 10

/**
 * Threshold for switching to [NonHierarchicalViewBasedAlgorithm], matching that class's own KDoc describing itself as
 * being for "large numbers of items (>1000 markers)".
 */
private const val VIEW_BASED_ALGORITHM_NODE_THRESHOLD = 1000

/**
 * Renders node markers with clustering via the library's [Clustering] composable.
 *
 * Each unclustered node is composed as a [PulsingNodeChip] (`clusterItemContent`), with its z-index forwarded through
 * [ClusteringMarkerProperties]; cluster bubbles keep the library's default rendering. Native info windows
 * (title/snippet from [NodeClusterItem]) and click interactions are handled by the library renderer, and precision
 * circles are drawn for the currently-unclustered items via `clusterItemDecoration`.
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
) {
    val configuration = LocalConfiguration.current
    Clustering(
        items = nodeClusterItems,
        onClusterClick = onClusterClick,
        onClusterItemInfoWindowClick = { item -> navigateToNodeDetails(item.node.num) },
        clusterItemContent = { item ->
            ClusteringMarkerProperties(zIndex = item.zIndex)
            PulsingNodeChip(node = item.node)
        },
        clusterItemDecoration = { item ->
            if (mapFilterState.showPrecisionCircle) {
                item.getPrecisionMeters()?.let { precisionMeters ->
                    if (precisionMeters > 0) {
                        Circle(
                            center = item.position,
                            radius = precisionMeters,
                            fillColor = Color(item.node.colors.second).copy(alpha = 0.2f),
                            strokeColor = Color(item.node.colors.second),
                            strokeWidth = 2f,
                            zIndex = 0f,
                        )
                    }
                }
            }
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
