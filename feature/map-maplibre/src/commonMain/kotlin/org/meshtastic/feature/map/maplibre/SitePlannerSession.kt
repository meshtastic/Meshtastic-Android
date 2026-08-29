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

import androidx.compose.runtime.Stable
import org.maplibre.spatialk.geojson.Position

/**
 * A Site Planner session the map is asking its host to run.
 *
 * Host-supplied for the same reason as the waypoint editor: the planner itself lives in the app, not in this module,
 * and it has no desktop host. A host that supplies nothing gets no Site Planner button at all.
 *
 * @property nodeNum The node a deep link asked to plan for, or null for a session started from the toolbar.
 */
@Stable
class SitePlannerSession(
    val nodeNum: Int?,
    /** The current map centre, for the planner's "use map centre" shortcut. */
    val mapCenter: () -> Position,
    /** Moves the map, so freshly imported coverage can be brought on screen. */
    val moveTo: (Position) -> Unit,
    val onDismiss: () -> Unit,
)
