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
package org.meshtastic.feature.map.maplibre.style

import androidx.compose.ui.graphics.Color

/**
 * Colours shared across the MapLibre map surfaces.
 *
 * Named here rather than inline so the same blue means "outbound route" on every screen, and so a theme pass has one
 * place to change.
 */
internal object MapColors {
    /** Cluster bubbles and traceroute hop markers. */
    val Slate = Color(0xFF2C2D3C)

    /** Outbound traceroute leg, and the node position track. */
    val RouteForward = Color(0xFF1E88E5)

    /** Return traceroute leg, and discovered nodes. */
    val RouteReturn = Color(0xFF43A047)

    /** Geofence zones and the currently selected track point — carried over from the OSMdroid map. */
    val Highlight = Color(0xFFFF8C00)
}
