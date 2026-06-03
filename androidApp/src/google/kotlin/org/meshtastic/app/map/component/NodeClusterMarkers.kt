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

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.feature.map.BaseMapViewModel

private const val MIN_CLUSTER_SIZE = 10

// Match the bottom-center anchor maps-compose used for the old `clusterItemContent` chip
// (clusterItemContentAnchor defaults to Offset(0.5f, 1.0f)), so chips keep sitting on the node coordinate.
private const val CHIP_ANCHOR_U = 0.5f
private const val CHIP_ANCHOR_V = 1.0f

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun NodeClusterMarkers(
    nodeClusterItems: List<NodeClusterItem>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    navigateToNodeDetails: (Int) -> Unit,
    onClusterClick: (Cluster<NodeClusterItem>) -> Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale

    val clusterManager = rememberClusterManager<NodeClusterItem>() ?: return

    // Render each non-clustered node as a Canvas-built BitmapDescriptor through a custom
    // DefaultClusterRenderer instead of passing a `clusterItemContent` Composable. The Composable path makes
    // maps-compose rasterize the chip through an off-screen ComposeView (ComposeUiClusterRenderer); that view
    // walks the view tree for a ViewTreeLifecycleOwner/SavedStateRegistryOwner and, because the cluster
    // renderer drives marker creation from an async Handler after the screen may have stopped, finds none and
    // crashes with "Composed into the View which doesn't propagate ViewTreeLifecycleOwner!"
    // (googlemaps/android-maps-compose#325 / #875) — historically our #1 FATAL. A renderer that paints the icon
    // in onBeforeClusterItemRendered never creates a View, so the crash class is eliminated rather than raced
    // against (see the owner-propagation workarounds in #5704/#5708 that could not win that race).
    val rendererState: MutableState<NodeChipClusterRenderer?> = remember { mutableStateOf(null) }

    MapEffect(clusterManager, density, fontScale) { map ->
        val renderer = NodeChipClusterRenderer(context, map, clusterManager, density, fontScale)
        renderer.minClusterSize = MIN_CLUSTER_SIZE
        clusterManager.renderer = renderer
        rendererState.value = renderer
    }

    SideEffect {
        clusterManager.setOnClusterClickListener(onClusterClick)
        clusterManager.setOnClusterItemInfoWindowClickListener { item -> navigateToNodeDetails(item.node.num) }
    }

    Clustering(items = nodeClusterItems, clusterManager = clusterManager)

    // The library's `clusterItemDecoration` only fires for its internal ComposeUiClusterRenderer (the gating
    // ClusterRendererItemState type is library-internal), so it never runs for our custom renderer. Draw the
    // precision circles ourselves for exactly the unclustered items the renderer exposes, preserving the prior
    // "circle only on non-clustered nodes" behavior.
    val renderer = rendererState.value
    if (renderer != null && mapFilterState.showPrecisionCircle) {
        val unclusteredItems by renderer.unclusteredItems
        unclusteredItems.forEach { item ->
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
    }
}

/**
 * A [DefaultClusterRenderer] that draws each non-clustered node's chip as a Canvas [BitmapDescriptor]
 * ([buildNodeChipDescriptor]) instead of a Composable, avoiding maps-compose's crash-prone off-screen ComposeView
 * rasterization. It also exposes the current set of unclustered items so the caller can draw precision circles (the
 * library's `clusterItemDecoration` hook is unavailable to non-library renderers).
 */
private class NodeChipClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<NodeClusterItem>,
    private val density: Float,
    private val fontScale: Float,
) : DefaultClusterRenderer<NodeClusterItem>(context, map, clusterManager) {

    val unclusteredItems: MutableState<Set<NodeClusterItem>> = mutableStateOf(emptySet())

    // Called on a background render thread — building the descriptor here keeps marker rasterization off the
    // main thread, and BitmapDescriptorFactory is safe once the SDK is initialized (the live map guarantees it).
    override fun onBeforeClusterItemRendered(item: NodeClusterItem, markerOptions: MarkerOptions) {
        markerOptions
            .icon(buildNodeChipDescriptor(item.node, density, fontScale))
            .anchor(CHIP_ANCHOR_U, CHIP_ANCHOR_V)
            .zIndex(item.getZIndex())
    }

    override fun onClusterItemUpdated(item: NodeClusterItem, marker: Marker) {
        marker.setIcon(buildNodeChipDescriptor(item.node, density, fontScale))
        marker.zIndex = item.getZIndex()
    }

    override fun onClustersChanged(clusters: Set<Cluster<NodeClusterItem>>) {
        super.onClustersChanged(clusters)
        unclusteredItems.value = clusters.filterNot { shouldRenderAsCluster(it) }.flatMap { it.items }.toSet()
    }
}
