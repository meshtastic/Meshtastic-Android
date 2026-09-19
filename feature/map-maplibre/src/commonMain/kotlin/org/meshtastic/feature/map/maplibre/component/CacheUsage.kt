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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.OfflinePack
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_cache_megabytes
import org.meshtastic.core.resources.map_cache_size
import org.meshtastic.core.resources.map_cache_tiles

/** Disk occupied by every downloaded pack. */
internal data class CacheUsage(val tiles: Long = 0L, val bytes: Long = 0L)

/**
 * Totals across the packs, each of which reports its progress as its own flow.
 *
 * `combine` over an empty list never emits, so no pack means no value at all rather than a zero.
 */
@Composable
internal fun rememberCacheUsage(packs: Set<OfflinePack>): CacheUsage {
    val usage by
        remember(packs) {
            if (packs.isEmpty()) {
                flowOf(CacheUsage())
            } else {
                combine(packs.map { pack -> pack.downloadProgress }) { progress ->
                    val healthy = progress.filterIsInstance<DownloadProgress.Healthy>()
                    // Resource bytes, not tile bytes: a pack also holds the style, its glyphs and its sprites, and
                    // all of it occupies the same disk the user is being asked about.
                    CacheUsage(
                        tiles = healthy.sumOf { it.completedTileCount },
                        bytes = healthy.sumOf { it.completedResourceBytes },
                    )
                }
            }
        }
            .collectAsState(CacheUsage())
    return usage
}

/**
 * How much disk the downloaded packs occupy.
 *
 * The OSMdroid map reported this in MB, which is the number that answers "is this filling my phone". No capacity beside
 * it: OSMdroid had one bounded SQLite cache, whereas MapLibre has explicitly downloaded packs the user deletes by hand
 * plus a separate ambient cache, and quoting a ceiling that governs neither would be a lie.
 */
@Composable
internal fun CacheUsageLine(storedBytes: Long, storedTiles: Long) {
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
