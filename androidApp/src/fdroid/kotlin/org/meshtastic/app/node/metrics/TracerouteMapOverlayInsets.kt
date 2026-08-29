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
package org.meshtastic.app.node.metrics

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.util.TracerouteMapOverlayInsets

/**
 * Where the hop count and route legend sit over the MapLibre traceroute map.
 *
 * Bottom-centre, the same place the Google flavor puts them, and for the same reason: the lower trailing corner now
 * holds this map's zoom controls, as Google's own always did there. This flavour used to override the alignment to
 * BottomEnd because that corner was empty — with zoom in it, the legend was covering the zoom-out button. Left as an
 * explicit override rather than deleted so the next person sees the corner is taken.
 */
fun getTracerouteMapOverlayInsets(): TracerouteMapOverlayInsets = TracerouteMapOverlayInsets(
    overlayAlignment = Alignment.BottomCenter,
    // Clear of the logo and attribution row along the bottom edge.
    overlayPadding = PaddingValues(bottom = 48.dp),
    contentHorizontalAlignment = Alignment.CenterHorizontally,
)
