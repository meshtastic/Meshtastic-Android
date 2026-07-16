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
package org.meshtastic.feature.map.mapcompose

import org.meshtastic.core.model.Node
import org.meshtastic.core.model.TracerouteOverlay
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.proto.Position

/** The mode sealed for the shared renderer — mirrors the google flavor's `GoogleMapMode` (plus Discovery/Inline). */
sealed interface MapComposeMode {
    /** Standard map: clustered nodes, waypoints with editing, geofence overlays. */
    data class Main(val waypointId: Int? = null) : MapComposeMode

    /** Focused node position track: fading polyline + selectable historical positions. */
    data class NodeTrack(
        val focusedNode: Node?,
        val positions: List<Position>,
        val selectedPositionTime: Int? = null,
        val onPositionSelect: ((Int) -> Unit)? = null,
    ) : MapComposeMode

    /** Traceroute visualization: offset forward/return polylines + hop markers. */
    data class Traceroute(
        val overlay: TracerouteOverlay?,
        val nodePositions: Map<Int, Position>,
        val onMappableCountChange: (shown: Int, total: Int) -> Unit,
    ) : MapComposeMode

    /** Discovery results around a scanned position. */
    data class Discovery(val userLatitude: Double, val userLongitude: Double, val nodes: List<DiscoveryMapNode>) :
        MapComposeMode

    /** Small non-interactive single-node map embedded in detail screens. */
    data class Inline(val node: Node) : MapComposeMode
}
