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

package com.geeksville.mesh.ui.map.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.NodeClusterItem
import com.geeksville.mesh.ui.node.components.NodeChip
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.clustering.Clustering

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun NodeClusterMarkers(
    nodeClusterItems: List<NodeClusterItem>,
    mapFilterState: UIViewModel.MapFilterState,
    navigateToNodeDetails: (Int) -> Unit
) {
    Clustering(
        items = nodeClusterItems,
        onClusterItemInfoWindowClick = { item ->
            navigateToNodeDetails(item.node.num)
            false
        },
        clusterItemContent = {
            NodeChip(
                node = it.node,
                enabled = false,
                isThisNode = false,
                isConnected = false
            ) { }
        },
        onClusterManager = { clusterManager ->
            (clusterManager.renderer as DefaultClusterRenderer).minClusterSize = 7
        }
    )

    if (mapFilterState.showPrecisionCircle) {
        nodeClusterItems.forEach { clusterItem ->
            clusterItem.getPrecisionMeters()?.let { precisionMeters ->
                if (precisionMeters > 0) {
                    Circle(
                        center = clusterItem.position,
                        radius = precisionMeters,
                        fillColor = Color(clusterItem.node.colors.second).copy(alpha = 0.2f),
                        strokeColor = Color(clusterItem.node.colors.second),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }

}