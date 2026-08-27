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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflineManager
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.rememberOfflineManager
import org.maplibre.spatialk.geojson.BoundingBox
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.map_cache_manager
import org.meshtastic.core.resources.map_cache_megabytes
import org.meshtastic.core.resources.map_cache_size
import org.meshtastic.core.resources.map_cache_tiles
import org.meshtastic.core.resources.map_download_status_complete
import org.meshtastic.core.resources.map_download_status_downloading
import org.meshtastic.core.resources.map_download_status_paused
import org.meshtastic.core.resources.map_select_download_region
import org.meshtastic.core.resources.map_start_download
import org.meshtastic.core.resources.map_tile_download_estimate
import org.meshtastic.core.resources.map_tile_limit_reached
import org.meshtastic.core.resources.offline_maps_empty
import org.meshtastic.core.ui.icon.Delete
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.PlayArrow
import org.meshtastic.feature.map.maplibre.tileCount

/** What the map must tell the offline sheet: which style to pack, and which region is on screen. */
internal class OfflineMapTarget(
    val styleUrl: String?,
    val bounds: () -> BoundingBox?,
    val zoom: () -> Double,
    /** Moves the map onto a downloaded region, so a pack in the list can actually be gone to. */
    val showRegion: (BoundingBox) -> Unit,
)

/**
 * Offline downloads, as a section of the layers sheet.
 *
 * Only usable with a vector basemap: a pack is defined against a style document, and a raster basemap draws over
 * `BaseStyle.Empty` and so has no style URL to pack.
 */
@Composable
internal fun OfflineMapsSection(target: OfflineMapTarget, onShowRegion: (BoundingBox) -> Unit) {
    val manager = rememberOfflineManager()
    val scope = rememberCoroutineScope()
    val packs = manager.packs

    val range = target.zoomRange()
    val estimate = target.bounds()?.tileCount(range.first, range.last) ?: 0L
    val storedTiles =
        packs.sumOf { pack -> (pack.downloadProgress as? DownloadProgress.Healthy)?.completedTileCount ?: 0L }
    // Resource bytes, not tile bytes: a pack also holds the style, its glyphs and its sprites, and all of it occupies
    // the same disk the user is being asked about.
    val storedBytes =
        packs.sumOf { pack -> (pack.downloadProgress as? DownloadProgress.Healthy)?.completedResourceBytes ?: 0L }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = stringResource(Res.string.map_cache_manager), style = MaterialTheme.typography.titleMedium)

        CacheUsageLine(storedBytes = storedBytes, storedTiles = storedTiles)
        DownloadEstimateLines(estimate = estimate, range = range)

        Button(
            onClick = { scope.launch { manager.downloadVisibleArea(target) } },
            enabled = target.styleUrl != null && estimate > 0L,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Text(text = stringResource(Res.string.map_start_download))
        }

        if (packs.isEmpty()) {
            Text(
                text = stringResource(Res.string.offline_maps_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            packs.forEach { pack ->
                OfflinePackRow(
                    pack = pack,
                    onShow = { bounds -> onShowRegion(bounds) },
                    // Off the main thread: unlike create and delete, resume is not a suspending call — it does its
                    // work inline on whoever calls it, and from a click handler that is the main thread. Starting a
                    // pack this way froze the UI long enough for Android to raise "isn't responding".
                    onToggle = { scope.launch(ioDispatcher) { manager.resume(pack) } },
                    onDelete = { scope.launch { manager.delete(pack) } },
                )
            }
        }
    }
}

/**
 * How much disk the downloaded packs occupy.
 *
 * The OSMdroid map reported this in MB, which is the number that answers "is this filling my phone". No capacity beside
 * it: OSMdroid had one bounded SQLite cache, whereas MapLibre has explicitly downloaded packs the user deletes by hand
 * plus a separate ambient cache, and quoting a ceiling that governs neither would be a lie.
 */
@Composable
private fun CacheUsageLine(storedBytes: Long, storedTiles: Long) {
    Text(
        text =
        stringResource(Res.string.map_cache_size) +
            ": " +
            stringResource(Res.string.map_cache_megabytes, storedBytes.megabytes()) +
            " · " +
            stringResource(Res.string.map_cache_tiles, storedTiles.toInt()),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What the next download will cost, before committing to it.
 *
 * The OSMdroid map showed this, and it matters more than it looks: the same two extra zoom levels are a couple of
 * hundred tiles over a neighbourhood and tens of thousands over a state.
 */
@Composable
private fun DownloadEstimateLines(estimate: Long, range: IntRange) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(text = stringResource(Res.string.map_select_download_region), style = MaterialTheme.typography.bodyMedium)
        Text(
            text =
            stringResource(Res.string.map_tile_download_estimate) +
                " " +
                stringResource(Res.string.map_cache_tiles, estimate.toInt()) +
                "  (z${range.first}\u2013${range.last})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OfflinePackRow(
    pack: OfflinePack,
    onShow: (BoundingBox) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = pack.downloadProgress
    val bounds = (pack.definition as? OfflinePackDefinition.TilePyramid)?.bounds

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier =
            Modifier.fillMaxWidth(PACK_ROW_TEXT_FRACTION)
                .then(if (bounds != null) Modifier.clickable { onShow(bounds) } else Modifier),
        ) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            val status = (progress as? DownloadProgress.Healthy)?.status
            if (status == DownloadStatus.Paused) {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = MeshtasticIcons.PlayArrow,
                        contentDescription = stringResource(Res.string.map_start_download),
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(imageVector = MeshtasticIcons.Delete, contentDescription = stringResource(Res.string.delete))
            }
        }
    }
}

/**
 * Packs the region currently on screen, from the current zoom a couple of levels deeper.
 *
 * What bounds the download is the *width* of the zoom range, not an absolute ceiling: a pack covers every tile in its
 * range, but the visible box shrinks as you zoom in, so two extra levels costs roughly the same work at any zoom. An
 * absolute cap is worse than useless here — it silently inverts the range once you are past it, which MapLibre rejects
 * outright with "offline region zoom range is invalid".
 *
 * Errors are caught rather than thrown: `create` reports a native failure by throwing, and this runs in a UI-scoped
 * coroutine, so letting it escape takes the window's event thread down with it.
 *
 * A created pack is paused until [OfflineManager.resume] is called, so this does both.
 */
private suspend fun OfflineManager.downloadVisibleArea(target: OfflineMapTarget): Boolean {
    val styleUrl = target.styleUrl
    val bounds = target.bounds()
    if (styleUrl == null || bounds == null) return false

    val range = target.zoomRange()

    return safeCatching {
        create(
            OfflinePackDefinition.TilePyramid(
                styleUrl = styleUrl,
                bounds = bounds,
                minZoom = range.first,
                maxZoom = range.last,
            ),
        )
    }
        .onFailure { error -> Logger.w(error) { "Could not create an offline pack" } }
        .isSuccess
}

private fun OfflinePack.label(): String {
    val definition = definition
    val bounds = (definition as? OfflinePackDefinition.TilePyramid)?.bounds
    val centre = bounds?.let { "${it.south.round()}, ${it.west.round()}" } ?: EM_DASH
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

/**
 * One line describing a pack's state, assembled from resources rather than written in English.
 *
 * `status.name` went straight into the UI before, so every locale read the library's own enum constants. The tile count
 * and byte size reuse the strings the cache figures above already use, which keeps one set of units to translate.
 */
@Composable
private fun DownloadProgress.summary(): String = when (this) {
    is DownloadProgress.Healthy ->
        listOf(
            stringResource(
                when (status) {
                    DownloadStatus.Paused -> Res.string.map_download_status_paused
                    DownloadStatus.Downloading -> Res.string.map_download_status_downloading
                    DownloadStatus.Complete -> Res.string.map_download_status_complete
                },
            ),
            stringResource(Res.string.map_cache_tiles, completedTileCount.toInt()),
            stringResource(Res.string.map_cache_megabytes, completedResourceBytes.megabytes()),
        )
            .joinToString(SUMMARY_SEPARATOR)

    // Upstream's message, which is not ours to localise, and more useful than a generic failure line.
    is DownloadProgress.Error -> message

    is DownloadProgress.TileLimitExceeded -> stringResource(Res.string.map_tile_limit_reached, limit.toInt())

    DownloadProgress.Unknown -> EM_DASH
}

/**
 * Bytes as megabytes, to one decimal place.
 *
 * Decimal megabytes rather than mebibytes: this number sits next to a phone's own storage figures, and those are
 * decimal.
 */
internal fun Long.megabytes(): String = NumberFormatter.format(this.toDouble() / BYTES_PER_MEGABYTE, 1)

private fun Double.round(): String {
    val scaled = (this * COORD_SCALE).toInt() / COORD_SCALE
    return scaled.toString()
}

private const val BYTES_PER_MEGABYTE = 1_000_000.0
private const val PACK_ROW_TEXT_FRACTION = 0.8f
private const val PACK_EXTRA_ZOOM_LEVELS = 2

/** MaplibreMap's own default zoomRange, which an offline region may not exceed. */
private const val MIN_PACK_ZOOM = 0
private const val MAX_PACK_ZOOM = 20
private const val COORD_SCALE = 100.0

/**
 * The zoom levels a download covers: the current level plus a couple deeper.
 *
 * Deliberately the only place this is worked out. Computing it twice is what produced an inverted range — an absolute
 * ceiling below the current zoom — which MapLibre rejects outright.
 */
private fun OfflineMapTarget.zoomRange(): IntRange {
    val current = zoom().toInt().coerceIn(MIN_PACK_ZOOM, MAX_PACK_ZOOM)
    val max = (current + PACK_EXTRA_ZOOM_LEVELS).coerceAtMost(MAX_PACK_ZOOM)
    return current.coerceAtMost(max)..max
}

/** The separator between the parts of a pack's summary line. */
private const val SUMMARY_SEPARATOR = " \u00b7 "

/** Stands in for a value there is nothing to show for; a symbol, so it needs no translation. */
private const val EM_DASH = "\u2014"
