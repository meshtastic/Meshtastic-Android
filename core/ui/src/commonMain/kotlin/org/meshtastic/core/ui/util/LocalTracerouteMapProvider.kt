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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import org.meshtastic.core.ui.component.PlaceholderScreen

/**
 * Provides an embeddable traceroute map composable that renders node markers and forward/return offset polylines for a
 * traceroute result. Unlike [LocalMapViewProvider], this does **not** include a Scaffold, AppBar, waypoints, location
 * tracking, custom tiles, or any main-map features — it is designed to be embedded inside [TracerouteMapScreen]'s
 * scaffold.
 *
 * On Desktop/JVM targets where native maps are not yet available, it falls back to a [PlaceholderScreen].
 *
 * Parameters:
 * - `tracerouteOverlay`: The [Any]-typed overlay (actually [TracerouteOverlay]) with forward/return route node nums.
 * - `tracerouteNodePositions`: Map of node num to [Any]-typed position snapshots for the route nodes.
 * - `onMappableCountChanged`: Callback with (shown, total) node counts.
 * - `modifier`: Compose modifier for the map.
 */
@Suppress("Wrapping")
val LocalTracerouteMapProvider =
    compositionLocalOf<
        @Composable (
            tracerouteOverlay: Any?,
            tracerouteNodePositions: Map<Int, Any>,
            onMappableCountChanged: (Int, Int) -> Unit,
            modifier: Modifier,
        ) -> Unit,
        > {
        { _, _, _, _ -> PlaceholderScreen("Traceroute Map") }
    }
