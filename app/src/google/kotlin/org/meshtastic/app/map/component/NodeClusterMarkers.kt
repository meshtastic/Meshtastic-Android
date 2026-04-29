/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.ClusteringMarkerProperties
import org.meshtastic.app.map.model.NodeClusterItem
import org.meshtastic.feature.map.BaseMapViewModel

@OptIn(MapsComposeExperimentalApi::class)
@Suppress("NestedBlockDepth")
@Composable
fun NodeClusterMarkers(
    nodeClusterItems: List<NodeClusterItem>,
    mapFilterState: BaseMapViewModel.MapFilterState,
    navigateToNodeDetails: (Int) -> Unit,
    onClusterClick: (Cluster<NodeClusterItem>) -> Boolean,
) {
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current

    // Workaround for https://github.com/googlemaps/android-maps-compose/issues/858
    // and https://github.com/googlemaps/android-maps-compose/issues/875
    // The maps clustering library creates an internal ComposeView to snapshot markers.
    // If that view is not attached to the hierarchy (which it often isn't during rendering),
    // it fails to find the Lifecycle and SavedState owners. We propagate them to the root view
    // so the internal snapshot view can find them when walking up the tree.
    // We do this in a SideEffect to ensure it happens before or during composition of children.
    SideEffect {
        val root = view.rootView
        if (root.findViewTreeLifecycleOwner() == null) {
            root.setViewTreeLifecycleOwner(lifecycleOwner)
        }
        if (root.findViewTreeSavedStateRegistryOwner() == null) {
            root.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        }
    }

    Clustering(
        items = nodeClusterItems,
        onClusterClick = onClusterClick,
        onClusterItemInfoWindowClick = { item ->
            navigateToNodeDetails(item.node.num)
            false
        },
        clusterItemContent = { clusterItem -> PulsingNodeChip(node = clusterItem.node) },
        onClusterManager = { clusterManager ->
            (clusterManager.renderer as DefaultClusterRenderer).minClusterSize = 10
        },
        clusterItemDecoration = { clusterItem ->
            if (mapFilterState.showPrecisionCircle) {
                clusterItem.getPrecisionMeters()?.let { precisionMeters ->
                    if (precisionMeters > 0) {
                        Circle(
                            center = clusterItem.position,
                            radius = precisionMeters,
                            fillColor = Color(clusterItem.node.colors.second).copy(alpha = 0.2f),
                            strokeColor = Color(clusterItem.node.colors.second),
                            strokeWidth = 2f,
                            zIndex = 0f,
                        )
                    }
                }
            }
            // Use the item's own priority-based zIndex (5f for My Node/Favorites, 4f for others)
            ClusteringMarkerProperties(zIndex = clusterItem.getZIndex())
        },
    )
}
