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
import androidx.compose.ui.Modifier
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.proto.Position

/**
 * Flavor-unified entry point for the embeddable traceroute map. Delegates to the Google Maps implementation
 * ([TracerouteGoogleMap]).
 */
@Composable
fun TracerouteMap(
    tracerouteOverlay: TracerouteOverlay?,
    tracerouteNodePositions: Map<Int, Position>,
    onMappableCountChanged: (shown: Int, total: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TracerouteGoogleMap(
        tracerouteOverlay = tracerouteOverlay,
        tracerouteNodePositions = tracerouteNodePositions,
        onMappableCountChanged = onMappableCountChanged,
        modifier = modifier,
    )
}
