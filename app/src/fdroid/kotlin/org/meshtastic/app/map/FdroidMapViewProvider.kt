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
package org.meshtastic.app.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.meshtastic.core.ui.util.MapViewProvider

class FdroidMapViewProvider : MapViewProvider {
    @Composable
    override fun MapView(
        modifier: Modifier,
        viewModel: Any,
        navigateToNodeDetails: (Int) -> Unit,
        focusedNodeNum: Int?,
        nodeTracks: List<Any>?,
        tracerouteOverlay: Any?,
        tracerouteNodePositions: Map<Int, Any>,
        onTracerouteMappableCountChanged: (Int, Int) -> Unit,
    ) {
        val mapViewModel: MapViewModel = hiltViewModel()
        org.meshtastic.app.map.MapView(
            modifier = modifier,
            mapViewModel = mapViewModel,
            navigateToNodeDetails = navigateToNodeDetails,
            focusedNodeNum = focusedNodeNum,
            nodeTracks = nodeTracks as? List<org.meshtastic.proto.Position>,
            tracerouteOverlay = tracerouteOverlay as? org.meshtastic.feature.map.model.TracerouteOverlay,
            tracerouteNodePositions = tracerouteNodePositions as? Map<Int, org.meshtastic.proto.Position> ?: emptyMap(),
            onTracerouteMappableCountChanged = onTracerouteMappableCountChanged,
        )
    }
}
