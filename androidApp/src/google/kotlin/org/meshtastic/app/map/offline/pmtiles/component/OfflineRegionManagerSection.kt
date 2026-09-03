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
package org.meshtastic.app.map.offline.pmtiles.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLngBounds
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.app.map.offline.pmtiles.OfflineDownloadFailure
import org.meshtastic.app.map.offline.pmtiles.OfflineDownloadState
import org.meshtastic.app.map.offline.pmtiles.OfflineRegion
import org.meshtastic.app.map.offline.pmtiles.OfflineRegionExtractor
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.map_cache_tiles
import org.meshtastic.core.resources.map_download_status_complete
import org.meshtastic.core.resources.map_offline_download_failed_build
import org.meshtastic.core.resources.map_offline_download_failed_io
import org.meshtastic.core.resources.map_offline_download_failed_region_limit
import org.meshtastic.core.resources.map_offline_download_failed_storage
import org.meshtastic.core.resources.map_offline_download_failed_tile_limit
import org.meshtastic.core.resources.map_offline_download_terrain
import org.meshtastic.core.resources.map_offline_downloading_progress
import org.meshtastic.core.resources.map_offline_manager
import org.meshtastic.core.resources.map_offline_region_label
import org.meshtastic.core.resources.map_offline_show_on_map
import org.meshtastic.core.resources.map_offline_terrain_contours
import org.meshtastic.core.resources.map_offline_terrain_download_complete
import org.meshtastic.core.resources.map_offline_terrain_download_failed_io
import org.meshtastic.core.resources.map_offline_terrain_download_failed_tile_limit
import org.meshtastic.core.resources.map_offline_terrain_downloading_progress
import org.meshtastic.core.resources.map_overlay_hillshade
import org.meshtastic.core.resources.map_select_download_region
import org.meshtastic.core.resources.map_start_download
import org.meshtastic.core.resources.map_tile_download_estimate
import org.meshtastic.core.resources.map_zoom_levels
import org.meshtastic.core.resources.offline_maps_empty
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.terrain.TerrainDownloadFailure
import org.meshtastic.feature.map.terrain.TerrainDownloadState

/** How many zoom levels deeper than the current view a download covers — matches the MapLibre flavor's own choice. */
private const val EXTRA_ZOOM_LEVELS = 2
private const val MIN_ZOOM = 0
private const val MAX_ZOOM = 20

/**
 * The Google flavor's offline-regions manager, a section of the layers sheet — the same slot the MapLibre flavor puts
 * its own [org.meshtastic.feature.map.maplibre.component.OfflineMapsSection] equivalent in. Not shared code: that
 * composable is `internal` to `feature:map-maplibre` and drives MapLibre's native `OfflinePack` API, which has no
 * equivalent here — this one drives [org.meshtastic.app.map.offline.pmtiles.OfflineRegionExtractor] instead. The visual
 * shape (estimate before download, a start button, a list of downloaded regions with delete) is kept the same on
 * purpose, for cross-flavor consistency, which is the design-standards justification for this section not going through
 * a full design review as new UI.
 */
@Suppress("LongParameterList")
@Composable
internal fun OfflineRegionManagerSection(
    visibleBounds: LatLngBounds?,
    currentZoom: Int,
    regions: List<OfflineRegion>,
    downloadState: OfflineDownloadState?,
    offlineOverlayEnabled: Boolean,
    estimateTileCount: (LatLngBounds, IntRange) -> Long,
    onDownload: (LatLngBounds, IntRange) -> Unit,
    onDeleteRegion: (String) -> Unit,
    onToggleOfflineOverlay: (Boolean) -> Unit,
    terrainDownloadState: TerrainDownloadState?,
    terrainDownloadRegionId: String?,
    terrainHillshadeEnabled: Boolean,
    terrainContoursEnabled: Boolean,
    onDownloadTerrain: (String) -> Unit,
    onToggleHillshade: (Boolean) -> Unit,
    onToggleContours: (Boolean) -> Unit,
) {
    val zoomRange = currentZoom.coerceIn(MIN_ZOOM, MAX_ZOOM).let { it..(it + EXTRA_ZOOM_LEVELS).coerceAtMost(MAX_ZOOM) }
    val estimate = visibleBounds?.let { estimateTileCount(it, zoomRange) } ?: 0L

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = stringResource(Res.string.map_offline_manager), style = MaterialTheme.typography.titleMedium)
            if (regions.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.map_offline_show_on_map),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Switch(checked = offlineOverlayEnabled, onCheckedChange = onToggleOfflineOverlay)
                }
            }
        }

        Text(text = stringResource(Res.string.map_select_download_region), style = MaterialTheme.typography.bodyMedium)
        Text(
            text =
            stringResource(Res.string.map_tile_download_estimate) +
                " " +
                stringResource(Res.string.map_cache_tiles, estimate.toInt()) +
                "  " +
                stringResource(Res.string.map_zoom_levels, zoomRange.first, zoomRange.last),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DownloadStatusLine(downloadState)

        Button(
            onClick = { visibleBounds?.let { onDownload(it, zoomRange) } },
            enabled = visibleBounds != null && estimate > 0L && downloadState !is OfflineDownloadState.InProgress,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(text = stringResource(Res.string.map_start_download))
        }

        DownloadedRegionsList(
            regions = regions,
            terrainDownloadState = terrainDownloadState,
            terrainDownloadRegionId = terrainDownloadRegionId,
            terrainHillshadeEnabled = terrainHillshadeEnabled,
            terrainContoursEnabled = terrainContoursEnabled,
            onDeleteRegion = onDeleteRegion,
            onDownloadTerrain = onDownloadTerrain,
            onToggleHillshade = onToggleHillshade,
            onToggleContours = onToggleContours,
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun DownloadedRegionsList(
    regions: List<OfflineRegion>,
    terrainDownloadState: TerrainDownloadState?,
    terrainDownloadRegionId: String?,
    terrainHillshadeEnabled: Boolean,
    terrainContoursEnabled: Boolean,
    onDeleteRegion: (String) -> Unit,
    onDownloadTerrain: (String) -> Unit,
    onToggleHillshade: (Boolean) -> Unit,
    onToggleContours: (Boolean) -> Unit,
) {
    if (regions.isEmpty()) {
        Text(
            text = stringResource(Res.string.offline_maps_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // The shared budget covers every region's base archive plus any terrain already attached to it — see
    // OfflineRegionStore.totalBytes, which this mirrors without a store call from composition.
    val terrainStorageAvailable =
        regions.sumOf { it.byteSize + it.terrainByteSize } < OfflineRegionExtractor.MAX_TOTAL_BYTES
    regions.forEach { region ->
        OfflineRegionRow(
            region = region,
            onDelete = { onDeleteRegion(region.id) },
            terrainStorageAvailable = terrainStorageAvailable,
            terrainDownloadState = terrainDownloadState.takeIf { terrainDownloadRegionId == region.id },
            isDownloadingTerrain = terrainDownloadRegionId == region.id,
            terrainHillshadeEnabled = terrainHillshadeEnabled,
            terrainContoursEnabled = terrainContoursEnabled,
            onDownloadTerrain = { onDownloadTerrain(region.id) },
            onToggleHillshade = onToggleHillshade,
            onToggleContours = onToggleContours,
        )
    }
}

@Composable
private fun DownloadStatusLine(state: OfflineDownloadState?) {
    when (state) {
        null -> Unit

        is OfflineDownloadState.InProgress -> {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = stringResource(Res.string.map_offline_downloading_progress, state.completed, state.total),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.completed.toFloat() / state.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }

        is OfflineDownloadState.Complete ->
            Text(
                text = stringResource(Res.string.map_download_status_complete),
                style = MaterialTheme.typography.bodySmall,
            )

        is OfflineDownloadState.Failed ->
            Text(
                text = stringResource(state.reason.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

private fun OfflineDownloadFailure.messageRes() = when (this) {
    OfflineDownloadFailure.NO_BUILD_AVAILABLE -> Res.string.map_offline_download_failed_build
    OfflineDownloadFailure.TILE_LIMIT_EXCEEDED -> Res.string.map_offline_download_failed_tile_limit
    OfflineDownloadFailure.REGION_LIMIT_EXCEEDED -> Res.string.map_offline_download_failed_region_limit
    OfflineDownloadFailure.STORAGE_LIMIT_EXCEEDED -> Res.string.map_offline_download_failed_storage
    OfflineDownloadFailure.IO_ERROR -> Res.string.map_offline_download_failed_io
}

@Suppress("LongParameterList")
@Composable
private fun OfflineRegionRow(
    region: OfflineRegion,
    onDelete: () -> Unit,
    terrainStorageAvailable: Boolean,
    terrainDownloadState: TerrainDownloadState?,
    isDownloadingTerrain: Boolean,
    terrainHillshadeEnabled: Boolean,
    terrainContoursEnabled: Boolean,
    onDownloadTerrain: () -> Unit,
    onToggleHillshade: (Boolean) -> Unit,
    onToggleContours: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth(REGION_ROW_TEXT_FRACTION)) {
                Text(text = region.label(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(Res.string.map_cache_tiles, region.tileCount.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete))
            }
        }

        // Rendering only ever shows terrain for whichever single region currently covers the viewport (see
        // MapViewModel.offlineRegionCovering), so these two switches are global toggles, not per-region state —
        // every terrain-having row reflects/controls the same MapViewModel.terrainHillshadeEnabled/ContoursEnabled.
        if (region.hasTerrain) {
            TerrainToggleRow(
                label = stringResource(Res.string.map_overlay_hillshade),
                checked = terrainHillshadeEnabled,
                onCheckedChange = onToggleHillshade,
            )
            TerrainToggleRow(
                label = stringResource(Res.string.map_offline_terrain_contours),
                checked = terrainContoursEnabled,
                onCheckedChange = onToggleContours,
            )
        } else if (isDownloadingTerrain) {
            TerrainDownloadStatusLine(terrainDownloadState)
        } else {
            TextButton(onClick = onDownloadTerrain, enabled = terrainStorageAvailable) {
                Text(stringResource(Res.string.map_offline_download_terrain))
            }
        }
    }
}

@Composable
private fun TerrainToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(REGION_ROW_TEXT_FRACTION),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TerrainDownloadStatusLine(state: TerrainDownloadState?) {
    when (state) {
        null -> Unit

        is TerrainDownloadState.InProgress -> {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text =
                    stringResource(
                        Res.string.map_offline_terrain_downloading_progress,
                        state.completed,
                        state.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { if (state.total > 0) state.completed.toFloat() / state.total else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }

        is TerrainDownloadState.Complete ->
            Text(
                text = stringResource(Res.string.map_offline_terrain_download_complete),
                style = MaterialTheme.typography.bodySmall,
            )

        is TerrainDownloadState.Failed ->
            Text(
                text = stringResource(state.reason.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
    }
}

private fun TerrainDownloadFailure.messageRes() = when (this) {
    TerrainDownloadFailure.TILE_LIMIT_EXCEEDED -> Res.string.map_offline_terrain_download_failed_tile_limit
    TerrainDownloadFailure.IO_ERROR -> Res.string.map_offline_terrain_download_failed_io
}

@Composable
private fun OfflineRegion.label(): String {
    val lat = NumberFormatter.format((southLat * COORD_SCALE).toInt() / COORD_SCALE, COORD_DECIMALS)
    val lon = NumberFormatter.format((westLon * COORD_SCALE).toInt() / COORD_SCALE, COORD_DECIMALS)
    return stringResource(
        Res.string.map_offline_region_label,
        lat,
        lon,
        stringResource(Res.string.map_zoom_levels, minZoom, maxZoom),
    )
}

private const val REGION_ROW_TEXT_FRACTION = 0.8f
private const val COORD_SCALE = 100.0
private const val COORD_DECIMALS = 2
