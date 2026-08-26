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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManager
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.rememberOfflineManager
import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.download_this_area
import org.meshtastic.core.resources.offline_maps
import org.meshtastic.core.resources.offline_maps_empty
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons

/** What the map must tell the offline sheet: which style to pack, and which region is on screen. */
internal class OfflineMapTarget(val styleUrl: String?, val bounds: () -> BoundingBox?, val zoom: () -> Double)

/**
 * Menu entry for downloading the visible area for offline use.
 *
 * Disabled for raster basemaps: an offline pack is defined against a style document, and a raster basemap draws over
 * `BaseStyle.Empty` and so has no style URL to pack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfflineMapsMenuItem(target: OfflineMapTarget) {
    var sheetVisible by remember { mutableStateOf(false) }

    DropdownMenuItem(
        text = { Text(text = stringResource(Res.string.offline_maps)) },
        onClick = { sheetVisible = true },
        enabled = target.styleUrl != null,
    )

    if (sheetVisible) {
        ModalBottomSheet(onDismissRequest = { sheetVisible = false }) { OfflineMapsSheet(target = target) }
    }
}

@Composable
private fun OfflineMapsSheet(target: OfflineMapTarget) {
    val manager = rememberOfflineManager()
    val scope = rememberCoroutineScope()
    val packs = manager.packs

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = stringResource(Res.string.offline_maps), style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = { scope.launch { manager.downloadVisibleArea(target) } },
            enabled = target.styleUrl != null && target.bounds() != null,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(text = stringResource(Res.string.download_this_area))
        }

        if (packs.isEmpty()) {
            Text(
                text = stringResource(Res.string.offline_maps_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            packs.forEach { pack -> OfflinePackRow(pack = pack, onDelete = { scope.launch { manager.delete(pack) } }) }
        }
    }
}

@Composable
private fun OfflinePackRow(pack: OfflinePack, onDelete: () -> Unit) {
    val progress = pack.downloadProgress

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(PACK_ROW_TEXT_FRACTION)) {
            Text(text = pack.label(), style = MaterialTheme.typography.bodyLarge)
            if (progress is DownloadProgress.Healthy && progress.status != DownloadStatus.Complete) {
                LinearProgressIndicator(
                    progress = { progress.fraction() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            } else {
                Text(
                    text = progress.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete))
        }
    }
}

/**
 * Packs the region currently on screen.
 *
 * The zoom range runs from the current level inward rather than all the way to the maximum: a pack covers every tile in
 * its range, so reaching for the deepest zoom over a city-sized box is how you accidentally download gigabytes.
 */
private suspend fun OfflineManager.downloadVisibleArea(target: OfflineMapTarget) {
    val styleUrl = target.styleUrl ?: return
    val bounds = target.bounds() ?: return
    val currentZoom = target.zoom().toInt().coerceAtLeast(0)

    create(
        OfflinePackDefinition.TilePyramid(
            styleUrl = styleUrl,
            bounds = bounds,
            minZoom = currentZoom,
            maxZoom = (currentZoom + PACK_EXTRA_ZOOM_LEVELS).coerceAtMost(PACK_MAX_ZOOM),
        ),
    )
}

private fun OfflinePack.label(): String {
    val definition = definition
    val bounds = (definition as? OfflinePackDefinition.TilePyramid)?.bounds
    val centre = bounds?.let { "${it.south.round()}, ${it.west.round()}" } ?: "—"
    return "$centre  z${definition.minZoom}–${definition.maxZoom ?: definition.minZoom}"
}

private fun DownloadProgress.fraction(): Float = when (this) {
    is DownloadProgress.Healthy ->
        if (requiredResourceCount > 0L) {
            (completedResourceCount.toFloat() / requiredResourceCount.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    else -> 0f
}

private fun DownloadProgress.summary(): String = when (this) {
    is DownloadProgress.Healthy -> "${status.name} · $completedTileCount tiles"
    is DownloadProgress.Error -> message
    is DownloadProgress.TileLimitExceeded -> "Tile limit reached ($limit)"
    DownloadProgress.Unknown -> "—"
}

private fun Double.round(): String {
    val scaled = (this * COORD_SCALE).toInt() / COORD_SCALE
    return scaled.toString()
}

private const val PACK_ROW_TEXT_FRACTION = 0.8f
private const val PACK_EXTRA_ZOOM_LEVELS = 2
private const val PACK_MAX_ZOOM = 16
private const val COORD_SCALE = 100.0
