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
package org.meshtastic.feature.map.maplibre.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.ExpandingAttributionButton
import org.maplibre.compose.overlay.MaplibreLogo
import org.maplibre.compose.overlay.MapOverlay as MaplibreOverlay

/**
 * The map's own ornaments: a scale bar while zooming, and the logo and attribution along the bottom.
 *
 * This is `MapOverlay.Default` with its compass removed. The mesh map already has a compass in its toolbar — one that
 * also toggles heading-lock — and drawing the library's as well would put two compasses on screen. The Google flavor
 * makes the same call from the other direction with `compassEnabled = false`.
 *
 * The logo and attribution are deliberately kept: the styles this map serves are licensed on the condition that they
 * are shown. Do not replace this with `MapOverlay.None`.
 *
 * Scale-bar units are left to the library, which picks them by region — the same locale-driven approach the rest of the
 * app takes through `localeUnitsProvider`.
 */
internal val MeshMapOrnaments: MaplibreOverlay = MaplibreOverlay {
    DisappearingScaleBar(
        metersPerDp = cameraState.viewport?.metersPerDpAtTarget ?: 0.0,
        zoom = cameraState.position.zoom,
        modifier = Modifier.align(Alignment.TopStart),
    )

    // Read before entering the Row, whose scope shadows this one.
    val camera = cameraState
    val style = styleState
    Row(
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaplibreLogo()
        ExpandingAttributionButton(cameraState = camera, styleState = style)
    }
}
