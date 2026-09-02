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

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.map_cache_megabytes
import org.meshtastic.core.resources.map_cache_size
import org.meshtastic.core.resources.map_cache_tiles
import org.meshtastic.core.resources.map_select_download_region
import org.meshtastic.core.resources.map_start_download
import org.meshtastic.core.resources.map_tile_download_estimate
import org.meshtastic.core.resources.map_tile_limit_reached
import org.meshtastic.core.resources.map_zoom_levels
import org.meshtastic.core.resources.offline_terrain_empty
import org.meshtastic.core.resources.offline_terrain_manager
import org.meshtastic.core.resources.offline_terrain_regional_detail
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.map.maplibre.terrain.OfflineTerrainRegion
import org.meshtastic.feature.map.maplibre.terrain.OfflineTerrainRepository
import org.meshtastic.feature.map.maplibre.terrain.estimateTerrainTiles
import org.meshtastic.feature.map.maplibre.terrain.toBoundingBox
import org.meshtastic.feature.map.maplibre.terrain.toGeoBounds
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.TerrainDownloadState
import org.meshtastic.feature.map.terrain.TerrainRegionExtractor

/**
 * Offline terrain — hillshade and elevation contours for the viewport, downloaded as its own section of the layers
 * sheet.
 *
 * Deliberately not gated by `offlineMapsSupported` like [OfflineMapsSection]: that flag exists because MapLibre's
 * native `OfflinePack` API silently downloads nothing on Desktop (see this module's README), but
 * [OfflineTerrainRepository] downloads over plain Ktor/Okio — the same reason `:feature:map-terrain`'s own
 * build.gradle.kts says its storage "must also work on Desktop". This section works on both hosts and is shown on both.
 */
@Composable
internal fun OfflineTerrainSection(target: OfflineMapTarget, onShowRegion: (BoundingBox) -> Unit) {
    val repository = remember { OfflineTerrainRepository.default }
    val scope = rememberCoroutineScope()
    val region by repository.region.collectAsState()
    // Not a local composable state: startDownload runs on the repository's own scope (see its doc comment for
    // why), so the state it reports has to be read from there too, or progress would appear to vanish the moment
    // this composable leaves composition and reappear wrong on the next one.
    val downloadState by repository.downloadState.collectAsState()

    LaunchedEffect(Unit) { repository.refresh() }

    val bounds = target.bounds()
    val maxZoom = target.terrainMaxZoom()
    val estimate = bounds?.let { estimateTerrainTiles(it.toGeoBounds(), maxZoom) } ?: 0L
    val overLimit = estimate > TerrainRegionExtractor.MAX_TILES
    val isDownloading = downloadState is TerrainDownloadState.InProgress

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = stringResource(Res.string.offline_terrain_manager), style = MaterialTheme.typography.titleMedium)

        val downloaded = region
        if (downloaded == null) {
            Text(
                text = stringResource(Res.string.offline_terrain_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DownloadedTerrainRow(
                region = downloaded,
                onShow = { onShowRegion(downloaded.bounds.toBoundingBox()) },
                onDelete = { scope.launch { repository.delete() } },
            )
        }

        TerrainDownloadControls(
            bounds = bounds,
            maxZoom = maxZoom,
            estimate = estimate,
            overLimit = overLimit,
            isDownloading = isDownloading,
            downloadState = downloadState,
            onStartDownload = { b -> repository.startDownload(b, maxZoom) },
        )
    }
}

/**
 * The estimate, the Start Download button, and its progress bar — split out only to keep [OfflineTerrainSection] short.
 */
@Composable
private fun TerrainDownloadControls(
    bounds: BoundingBox?,
    maxZoom: Int,
    estimate: Long,
    overLimit: Boolean,
    isDownloading: Boolean,
    downloadState: TerrainDownloadState?,
    onStartDownload: (GeoBounds) -> Unit,
) {
    // One outer Column, not three sibling top-level emitters: compose-rules' MultipleEmitters wants a composable to
    // emit from a single source at its own top level, same as every other section composable in this file.
    Column {
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = stringResource(Res.string.map_select_download_region),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text =
                stringResource(Res.string.map_tile_download_estimate) +
                    " " +
                    stringResource(Res.string.map_cache_tiles, estimate.toInt()) +
                    "  " +
                    stringResource(Res.string.map_zoom_levels, 0, maxZoom),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (overLimit) {
                Text(
                    text = stringResource(Res.string.map_tile_limit_reached, TerrainRegionExtractor.MAX_TILES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Button(
            onClick = { bounds?.let { onStartDownload(it.toGeoBounds()) } },
            enabled = bounds != null && estimate in 1L..TerrainRegionExtractor.MAX_TILES.toLong() && !isDownloading,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(text = stringResource(Res.string.map_start_download))
        }

        if (downloadState is TerrainDownloadState.InProgress) {
            val fraction =
                if (downloadState.total > 0) {
                    downloadState.completed.toFloat() / downloadState.total.toFloat()
                } else {
                    0f
                }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DownloadedTerrainRow(region: OfflineTerrainRegion, onShow: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onShow),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.fillMaxWidth(TERRAIN_ROW_TEXT_FRACTION)) {
            Text(
                text =
                stringResource(Res.string.map_cache_size) +
                    ": " +
                    stringResource(Res.string.map_cache_megabytes, region.byteSize.megabytes()) +
                    " · " +
                    stringResource(Res.string.map_cache_tiles, region.tileCount.toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (region.hasRegionalDetail) {
                Text(
                    text = stringResource(Res.string.offline_terrain_regional_detail),
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
 * The zoom levels a terrain download covers: the current level plus a couple deeper, mirroring [OfflineMapTarget]'s own
 * private `zoomRange` convention for the base map's offline packs.
 *
 * Bounded by [MapterhornEndpoints.REGIONAL_MAX_ZOOM] rather than MapLibre's own 0..20, since [estimateTerrainTiles] and
 * [TerrainRegionExtractor] never fetch anything deeper than that regardless of what is asked for.
 */
private fun OfflineMapTarget.terrainMaxZoom(): Int {
    val current = zoom().toInt().coerceIn(0, MapterhornEndpoints.REGIONAL_MAX_ZOOM)
    return (current + TERRAIN_EXTRA_ZOOM_LEVELS).coerceAtMost(MapterhornEndpoints.REGIONAL_MAX_ZOOM)
}

private const val TERRAIN_EXTRA_ZOOM_LEVELS = 2
private const val TERRAIN_ROW_TEXT_FRACTION = 0.8f
