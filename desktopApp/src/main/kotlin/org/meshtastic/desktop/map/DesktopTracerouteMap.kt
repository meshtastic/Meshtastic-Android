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
package org.meshtastic.desktop.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.maplibre.MapLibreTracerouteMap
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Position

/**
 * Desktop adapter for the traceroute map seam.
 *
 * Mirrors the F-Droid adapter: the seam hands over an overlay plus snapshot positions, and the
 * shared selection helper resolves those into placeable nodes so both platforms count "mappable
 * hops" the same way.
 */
@Composable
fun DesktopTracerouteMap(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChange: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SharedMapViewModel = koinViewModel()
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()

    val selection =
        viewModel.tracerouteNodeSelection(
            tracerouteOverlay = tracerouteOverlay,
            tracerouteNodePositions = tracerouteNodePositions,
            nodes = nodes,
        )

    LaunchedEffect(selection.nodeLookup.size, selection.overlayNodeNums.size) {
        onMappableCountChange(
            selection.overlayNodeNums.count { selection.nodeLookup.containsKey(it) },
            selection.overlayNodeNums.size,
        )
    }

    MapLibreTracerouteMap(
        forwardRoute = tracerouteOverlay?.forwardRoute.orEmpty(),
        returnRoute = tracerouteOverlay?.returnRoute.orEmpty(),
        nodeLookup = selection.nodeLookup,
        modifier = modifier,
    )
}
