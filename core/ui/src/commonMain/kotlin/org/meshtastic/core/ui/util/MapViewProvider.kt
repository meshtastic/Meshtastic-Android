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

/**
 * Interface for providing a flavored MapView. This allows the map feature to be decoupled from specific map
 * implementations (Google Maps vs osmdroid).
 */
interface MapViewProvider {
    @Composable
    fun MapView(
        modifier: Modifier,
        // We use Any here to avoid circular dependency with feature:map
        viewModel: Any,
        navigateToNodeDetails: (Int) -> Unit,
        focusedNodeNum: Int? = null,
        // Using List<Any> to avoid dependency on proto.Position if needed
        nodeTracks: List<Any>? = null,
        tracerouteOverlay: Any? = null,
        tracerouteNodePositions: Map<Int, Any> = emptyMap(),
        onTracerouteMappableCountChanged: (Int, Int) -> Unit = { _, _ -> },
        waypointId: Int? = null,
    )
}

val LocalMapViewProvider = compositionLocalOf<MapViewProvider?> { null }
