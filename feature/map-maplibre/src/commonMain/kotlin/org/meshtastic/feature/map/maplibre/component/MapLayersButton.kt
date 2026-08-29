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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.manage_map_layers
import org.meshtastic.core.ui.icon.Layers
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.component.MapButton
import org.meshtastic.feature.map.component.RasterOverlayToggles
import org.meshtastic.feature.map.maplibre.style.MapOverlay
import org.meshtastic.feature.map.maplibre.style.MapOverlays
import org.meshtastic.feature.map.tiles.MapTileCatalogue

/**
 * The layers control: a button that opens a sheet, matching the Google flavor, which opens a modal sheet rather than a
 * dropdown for the same button.
 *
 * The sheet holds three things: the built-in raster overlays, offline downloads, and whatever layer management the host
 * contributes — on Android that is the imported GeoJSON/KML manager, which lives in the app because adding a layer
 * needs a file picker.
 *
 * Offline downloads are shown only where [offlineMapsSupported]. MapLibre's offline API compiles on every target, and
 * on desktop it will happily create a pack and report it — but the pack never downloads a tile, so the section would
 * offer a control that quietly does nothing. Better to not offer it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapLayersButton(
    overlays: List<MapOverlay>,
    onOverlaysChange: (List<MapOverlay>) -> Unit,
    offlineTarget: OfflineMapTarget,
    offlineMapsSupported: Boolean,
    extra: @Composable () -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }

    MapButton(
        icon = MeshtasticIcons.Layers,
        contentDescription = stringResource(Res.string.manage_map_layers),
        onClick = { sheetVisible = true },
    )

    if (sheetVisible) {
        ModalBottomSheet(onDismissRequest = { sheetVisible = false }) {
            MapLayersSheet(
                overlays = overlays,
                onOverlaysChange = onOverlaysChange,
                offlineTarget = offlineTarget,
                offlineMapsSupported = offlineMapsSupported,
                onDismiss = { sheetVisible = false },
                extra = extra,
            )
        }
    }
}

@Composable
private fun MapLayersSheet(
    overlays: List<MapOverlay>,
    onOverlaysChange: (List<MapOverlay>) -> Unit,
    offlineTarget: OfflineMapTarget,
    offlineMapsSupported: Boolean,
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.manage_map_layers),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        RasterOverlayToggles(
            available = MapTileCatalogue.overlays,
            enabledIds = overlays.mapTo(mutableSetOf()) { it.id },
            onToggle = { id -> MapOverlays.byId(id)?.let { onOverlaysChange(overlays.toggling(it)) } },
        )

        if (offlineMapsSupported) {
            HorizontalDivider()

            OfflineMapsSection(
                target = offlineTarget,
                onShowRegion = { bounds ->
                    onDismiss()
                    offlineTarget.showRegion(bounds)
                },
            )
        }

        HorizontalDivider()

        extra()
    }
}

private fun List<MapOverlay>.toggling(overlay: MapOverlay): List<MapOverlay> =
    if (any { it.id == overlay.id }) filterNot { it.id == overlay.id } else this + overlay
