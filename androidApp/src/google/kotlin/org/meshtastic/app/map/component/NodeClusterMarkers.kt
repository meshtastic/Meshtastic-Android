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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()

    // maps-compose renders each non-clustered item to a bitmap through an off-screen ComposeView that
    // it attaches under the MapView (see ComposeUiClusterRenderer + NoDrawContainerView in
    // MapComposeViewRender). That ComposeView walks up the view tree for a ViewTreeLifecycleOwner and,
    // when it finds none, crashes with "Composed into the View which doesn't propagate
    // ViewTreeLifecycleOwner!" (googlemaps/android-maps-compose#875 / #325) — a FATAL on the map screen.
    //
    // Propagate the owners onto this map screen's host view (LocalView.current), which is an ancestor
    // of the internally-created MapView, so the renderer's ComposeView can resolve them. We deliberately
    // do NOT touch view.rootView (the activity root): attaching a transient NavEntry lifecycle there is
    // what caused the node-list popup regression (#5684), which is why #5704 removed the prior, broader
    // workaround entirely. Scoping to the map host view and restoring the previous owners on dispose
    // keeps the fix local to the map and leaves Popups/DropdownMenus untouched.
    DisposableEffect(view, lifecycleOwner, savedStateRegistryOwner) {
        val prevLifecycleOwner = view.findViewTreeLifecycleOwner()
        val prevSavedStateRegistryOwner = view.findViewTreeSavedStateRegistryOwner()
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        onDispose {
            view.setViewTreeLifecycleOwner(prevLifecycleOwner)
            view.setViewTreeSavedStateRegistryOwner(prevSavedStateRegistryOwner)
        }
    }

    // The cluster renderer drives marker rendering from an async Handler (DefaultClusterRenderer's
    // MarkerModifier), which can fire after this screen has stopped and the internal ComposeView is
    // detached — at which point no owner is reachable regardless of the above. Skip rendering once the
    // lifecycle is no longer at least STARTED to close most of that race.
    if (!lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return

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
