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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.zoom_in
import org.meshtastic.core.resources.zoom_out
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Remove

/**
 * Zoom in and out, stacked, for the corner of a map.
 *
 * Where Google Maps puts its own: `MapUiSettings(zoomControlsEnabled = true)` draws a vertical pair against the lower
 * trailing edge, clear of the top of the map, and every Google-flavor map surface in this app turns it on. MapLibre
 * publishes no zoom ornament at all, so its maps draw this instead — placed the same way, so the two flavors feel alike
 * and the reachable half of a phone screen is where the frequent control lives.
 *
 * Separate from [MapControlsOverlay] on purpose. That toolbar holds the occasional controls — filter, basemap, layers —
 * and belongs at the top; zoom belongs where the thumb is.
 *
 * @param compact shrinks the pair for a map only a couple of hundred dp tall — the node-detail mini-map, where the
 *   default size takes up half the map. It uses Material 3's own small icon-button metrics rather than an arbitrary
 *   `size` modifier, so the visual container shrinks while the touch target stays accessible.
 */
@Composable
fun MapZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    VerticalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
        // The buttons are only part of the height; the toolbar's own leading and trailing space is the rest, and on a
        // thumbnail that is the part worth reclaiming. Shrinking the buttons alone moved the total by 14%.
        contentPadding =
        if (compact) PaddingValues(COMPACT_CONTENT_PADDING.dp) else FloatingToolbarDefaults.ContentPadding,
    ) {
        MapButton(
            icon = MeshtasticIcons.Add,
            contentDescription = stringResource(Res.string.zoom_in),
            onClick = onZoomIn,
            compact = compact,
        )
        MapButton(
            icon = MeshtasticIcons.Remove,
            contentDescription = stringResource(Res.string.zoom_out),
            onClick = onZoomOut,
            compact = compact,
        )
    }
}

/** Leading and trailing space for the compact pair, down from the toolbar's default. */
private const val COMPACT_CONTENT_PADDING = 2
