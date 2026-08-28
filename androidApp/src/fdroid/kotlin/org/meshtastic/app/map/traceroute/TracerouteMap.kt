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
package org.meshtastic.app.map.traceroute

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.map.androidCustomRasterBasemaps
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.maplibre.MapLibreTracerouteMap
import org.meshtastic.feature.map.tracerouteNodeSelection
import org.meshtastic.proto.Position

/** Flavor-unified entry point for the embeddable traceroute map. MapLibre implementation. */
@Composable
fun TracerouteMap(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChange: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
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
    LaunchedEffect(selection.nodeLookup.size, selection.overlayNodeNums.size) {
        reportCount(
            selection.overlayNodeNums.count { selection.nodeLookup.containsKey(it) },
            selection.overlayNodeNums.size,
        )
    }

    MapLibreTracerouteMap(
        forwardRoute = tracerouteOverlay?.forwardRoute.orEmpty(),
        returnRoute = tracerouteOverlay?.returnRoute.orEmpty(),
        nodeLookup = selection.nodeLookup,
        modifier = modifier,
        customBasemaps = { androidCustomRasterBasemaps() },
    )
}
