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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.include
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
    val mapState = checkNotNull(LocalMapState.current)
    val viewportInsets = LocalViewportInsets.current

    // A custom overlay fills the map and the library keeps its own inset helper internal, so the scale bar has to
    // carry the viewport insets, safe-area insets and edge margin that the built-in controls apply for themselves.
    Box(
        modifier =
        Modifier.fillMaxSize()
            .padding(viewportInsets)
            .consumeWindowInsets(viewportInsets)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(MaplibreOverlay.Spacing),
    ) {
        DisappearingScaleBar(
            metersPerDp = { mapState.viewport?.metersPerDpAtTarget ?: 0.0 },
            zoom = { mapState.cameraPosition.zoom },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }

    // The logo and the attribution button, in the places `MapOverlay.Default` puts them.
    include(MaplibreOverlay.AttributionOnly)
}
