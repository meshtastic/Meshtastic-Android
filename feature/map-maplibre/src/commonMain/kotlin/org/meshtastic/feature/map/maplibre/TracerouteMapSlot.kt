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
package org.meshtastic.feature.map.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.maplibre.component.customRasterBasemaps
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Position

/**
 * Host-facing traceroute map for the MapLibre hosts. The seam hands over an overlay plus snapshot positions, and the
 * shared selection helper resolves those into placeable nodes so every host counts "mappable hops" the same way.
 */
@Composable
fun MapLibreTracerouteMapSlot(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChange: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
    customBasemaps: @Composable () -> List<Basemap.Raster> = { customRasterBasemaps() },
) {
    val viewModel: SharedMapViewModel = koinViewModel()
    // The effect below restarts on hop-count changes; capturing the callback keeps a recomposition
    // with a fresh lambda from firing a stale one.
    val reportCount by rememberUpdatedState(onMappableCountChange)
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()

    val selection =
        viewModel.tracerouteNodeSelection(
            tracerouteOverlay = tracerouteOverlay,
            tracerouteNodePositions = tracerouteNodePositions,
            nodes = nodes,
        )

    // The host shows "n of m hops mappable"; m counts every hop the traceroute named, n only those
    // we can actually place, so a route through nodes we have never heard from still reports honestly.
    val total = selection.overlayNodeNums.size
    val shown = selection.overlayNodeNums.count { selection.nodeLookup.containsKey(it) }
    LaunchedEffect(shown, total) { reportCount(shown, total) }

    MapLibreTracerouteMap(
        forwardRoute = tracerouteOverlay?.forwardRoute.orEmpty(),
        returnRoute = tracerouteOverlay?.returnRoute.orEmpty(),
        nodeLookup = selection.nodeLookup,
        modifier = modifier,
        customBasemaps = customBasemaps,
    )
}
