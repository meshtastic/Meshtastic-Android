/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.model.NodeClusterItem

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
    // Workaround for https://github.com/googlemaps/android-maps-compose/issues/858
    // Ensure owners are set on the Activity decor view so the internal ComposeView created by
    // the clustering renderer can find them when walking up the view tree.
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        if (activity != null) {
            val decorView = activity.window.decorView
            if (decorView.findViewTreeLifecycleOwner() == null && activity is LifecycleOwner) {
                decorView.setViewTreeLifecycleOwner(activity)
            }
            if (decorView.findViewTreeSavedStateRegistryOwner() == null && activity is SavedStateRegistryOwner) {
                decorView.setViewTreeSavedStateRegistryOwner(activity)
            }
        }
    }

    if (mapFilterState.showPrecisionCircle) {
        nodeClusterItems.forEach { clusterItem ->
            key(clusterItem.node.num) {
                // Add a stable key for each circle
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
    )
}
