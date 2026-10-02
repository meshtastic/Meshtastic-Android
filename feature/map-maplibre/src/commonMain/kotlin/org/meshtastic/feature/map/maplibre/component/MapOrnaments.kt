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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.overlay.AttributionDefaults
import org.maplibre.compose.overlay.AttributionLinks
import org.maplibre.compose.overlay.DisappearingScaleBar
import org.maplibre.compose.overlay.LocalViewportInsets
import org.maplibre.compose.overlay.MaplibreLogo
import org.maplibre.compose.overlay.attributions
import org.maplibre.compose.overlay.include
import org.maplibre.compose.overlay.MapOverlay as MaplibreOverlay

/**
 * The map's own ornaments: a scale bar while zooming, and the logo and attribution along the bottom.
 *
 * This reproduces `MapOverlay.Default`, which is a scale bar plus `AttributionOnly`, and is spelled out rather than
 * `include`d because the library's inset helper is internal to it.
 *
 * The library's own compass and zoom pair are absent by choice rather than by removal: the mesh map already has a
 * compass in its toolbar — one that also toggles heading-lock — and draws its zoom pair as `MapZoom`. The Google flavor
 * makes the same call from the other direction with `compassEnabled = false`.
 *
 * The logo and attribution are deliberately kept: the styles this map serves are licensed on the condition that they
 * are shown. Do not replace this with `MapOverlay.None`; the main map is where the credit is read, not hidden behind a
 * tap.
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

/**
 * `MapOverlay.Default` with the credit collapsed, for a map too small to host the credit without it overflowing: the
 * scale bar, the wordmark and the button all stay, and the button is what reveals the text.
 *
 * Composed rather than reached for because the library cannot be asked for it — [CollapsedAttributionButton] says why —
 * and the insets are spelled out because its `DefaultControls` is internal to it.
 */
internal val CollapsedAttributionOrnaments: MaplibreOverlay = MaplibreOverlay {
    val mapState = checkNotNull(LocalMapState.current)
    val viewportInsets = LocalViewportInsets.current
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
        MaplibreLogo(Modifier.align(Alignment.BottomStart))
        CollapsedAttributionButton(Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * How much of the map's width the expanded credit may take, the rest going to the toggle. Under half, so the strip
 * still reads as a strip on a thumbnail rather than a banner, and the map beside it stays usable.
 */
private const val CREDIT_WIDTH_FRACTION = 0.55f

/**
 * The library's attribution button, starting collapsed.
 *
 * `ExpandingAttributionButton` holds its own `expanded` flag, initialised to `true` and not settable from outside, so
 * `collapsedStyle` only styles the branch it is already in and the credit still opens. Holding the flag here is what
 * makes it start shut; everything else — icon, label, links — still comes from the library.
 */
@Composable
private fun CollapsedAttributionButton(modifier: Modifier = Modifier) {
    val mapState = checkNotNull(LocalMapState.current)
    val mapStyle = mapState.style
    // Derived, not remembered: a basemap arrives as a style URL, so its sources are still empty on the first
    // composition and a remembered list would never re-read. As in the library's own button.
    val attributions by remember(mapStyle) { derivedStateOf { mapStyle.attributions() } }
    if (attributions.isEmpty()) return

    var expanded by remember(mapStyle) { mutableStateOf(false) }

    // As the library's button: a gesture closes the credit, so it does not sit over a short map after a pan. Narrowed
    // to gestures, or the programmatic moves our own zoom controls make would fold it away mid-animation.
    LaunchedEffect(mapState) {
        snapshotFlow { mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.GESTURE }
            .filter { it }
            .collect { expanded = false }
    }

    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).background(AttributionDefaults.ContainerColor)) {
        // Read out here, not inside AnimatedVisibility: that lambda's receiver hides this scope.
        val creditMaxWidth = maxWidth * CREDIT_WIDTH_FRACTION
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The toggle is last: the container is trailing-aligned, so a leading toggle would sit inboard of the
            // credit
            // and drift inwards as it opens. A Row mirrors itself, so last is the trailing edge in RTL too.
            AnimatedVisibility(visible = expanded) {
                Box(
                    Modifier
                        // `AttributionLinks` scrolls its own single line, so it only needs a ceiling: unbounded it
                        // measures at the credit's full width and sprawls past a short map.
                        .widthIn(max = creditMaxWidth)
                        .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    AttributionLinks(attributions = attributions, textStyle = AttributionDefaults.ContentTextStyle)
                }
            }
            AttributionDefaults.button { expanded = !expanded }
        }
    }
}
