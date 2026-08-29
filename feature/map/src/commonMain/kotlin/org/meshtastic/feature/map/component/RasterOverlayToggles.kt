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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.feature.map.tiles.RasterOverlaySource

/**
 * Checkboxes for the raster overlays a map can composite over its basemap.
 *
 * Both flavours show these in their layers sheet. What differs is [available]: the MapLibre map offers every catalogue
 * overlay, while the Google map filters out the DEM-encoded ones, whose pixels are elevation rather than imagery and
 * would draw as noise without a renderer that shades them.
 */
@Composable
fun RasterOverlayToggles(
    available: List<RasterOverlaySource>,
    enabledIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        available.forEach { overlay ->
            ListItem(
                headlineContent = { Text(text = stringResource(overlay.label)) },
                trailingContent = {
                    Checkbox(checked = overlay.id in enabledIds, onCheckedChange = { onToggle(overlay.id) })
                },
            )
        }
    }
}
