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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
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
import com.google.maps.android.compose.rememberComposeBitmapDescriptor
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.feature.map.BaseMapViewModel

private const val MIN_CLUSTER_SIZE = 10

/**
 * Renders node markers with clustering.
 *
 * Marker bitmaps are generated **in the maps compose scope** via [rememberComposeBitmapDescriptor], which composes each
 * chip in a `ComposeView` parented to the live host view (a real, attached view that has valid `ViewTreeLifecycleOwner`
 * / `SavedStateRegistryOwner`) and renders it synchronously to a [BitmapDescriptor].
 *
 * This deliberately avoids the clustering library's `clusterItemContent` path: that renderer
 * ([com.google.maps.android.compose.clustering.ComposeUiClusterRenderer]) composes each item in a *detached*
 * `ComposeView` that only carries a fake lifecycle owner and no `SavedStateRegistryOwner`, so it crashes when the
 * surrounding Navigation 3 / popup hierarchy lacks those owners (the top Crashlytics FATAL). A custom
 * [DefaultClusterRenderer] subclass assigns the pre-baked bitmaps instead, keeping native info windows (title/snippet
 * from [NodeClusterItem]) and click interactions intact.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Suppress("NestedBlockDepth")
@Composable
fun NodeClusterMarkers(
    nodeClusterItems: List<NodeClusterItem>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    navigateToNodeDetails: (Int) -> Unit,
    onClusterClick: (Cluster<NodeClusterItem>) -> Boolean,
) {
    val context = LocalContext.current
    val clusterManager = rememberClusterManager<NodeClusterItem>()

    // Bake each node's marker icon in-scope. Keyed by node so a bitmap is only re-rendered when that node
    // actually changes. The descriptors are stashed in a snapshot map the renderer reads at render time.
    val iconDescriptors = remember { mutableStateMapOf<Int, BitmapDescriptor>() }
    nodeClusterItems.forEach { item ->
        key(item.node.num) {
            val descriptor = rememberComposeBitmapDescriptor(item.node) { PulsingNodeChip(node = item.node) }
            DisposableEffect(descriptor) {
                iconDescriptors[item.node.num] = descriptor
                onDispose { iconDescriptors.remove(item.node.num) }
            }
        }
    }

    if (clusterManager != null) {
        val rendererState = remember { mutableStateOf<NodeClusterRenderer?>(null) }

        // The renderer needs the GoogleMap instance, only available inside the map scope.
        MapEffect(clusterManager) { map ->
            val renderer = NodeClusterRenderer(context, map, clusterManager) { iconDescriptors[it] }
            clusterManager.renderer = renderer
            rendererState.value = renderer
        }

        // Keep listeners current — the lambdas can change across recompositions.
        SideEffect {
            clusterManager.setOnClusterClickListener { cluster -> onClusterClick(cluster) }
            clusterManager.setOnClusterItemInfoWindowClickListener { item -> navigateToNodeDetails(item.node.num) }
        }

        // Re-cluster once the renderer is attached and freshly-baked icons arrive, so markers pick up the bitmaps
        // even when the cluster manager became available after the icons were rendered.
        val renderer = rendererState.value
        LaunchedEffect(renderer, iconDescriptors.size) { if (renderer != null) clusterManager.cluster() }

        Clustering(items = nodeClusterItems, clusterManager = clusterManager)

        // Precision circles for the currently-unclustered items (the renderer tracks them as the zoom changes).
        if (mapFilterState.showPrecisionCircle) {
            renderer?.unclusteredItems?.value?.forEach { item ->
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
}

/**
 * [DefaultClusterRenderer] that assigns the pre-baked [BitmapDescriptor]s (rendered in the maps compose scope) to
 * non-clustered item markers, and exposes the set of currently-unclustered items so the caller can decorate them (e.g.
 * precision circles). Cluster bubbles keep the library's default rendering.
 */
private class NodeClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<NodeClusterItem>,
    private val iconProvider: (Int) -> BitmapDescriptor?,
) : DefaultClusterRenderer<NodeClusterItem>(context, map, clusterManager) {

    val unclusteredItems = mutableStateOf<Set<NodeClusterItem>>(emptySet())

    init {
        minClusterSize = MIN_CLUSTER_SIZE
    }

    override fun onClustersChanged(clusters: Set<Cluster<NodeClusterItem>>) {
        super.onClustersChanged(clusters)
        unclusteredItems.value = clusters.filterNot { shouldRenderAsCluster(it) }.flatMap { it.items }.toSet()
    }

    override fun onBeforeClusterItemRendered(item: NodeClusterItem, markerOptions: MarkerOptions) {
        // super sets title/snippet from the ClusterItem, which drives the native info window.
        super.onBeforeClusterItemRendered(item, markerOptions)
        iconProvider(item.node.num)?.let { markerOptions.icon(it) }
        markerOptions.zIndex(item.getZIndex())
    }

    override fun onClusterItemUpdated(item: NodeClusterItem, marker: Marker) {
        // super keeps title/snippet (and the open info window) in sync.
        super.onClusterItemUpdated(item, marker)
        iconProvider(item.node.num)?.let { marker.setIcon(it) }
        marker.zIndex = item.getZIndex()
    }
}
