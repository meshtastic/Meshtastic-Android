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
package org.meshtastic.app.map

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.net.toFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import org.koin.compose.koinInject
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.feature.map.maplibre.style.Basemap
import org.meshtastic.feature.map.maplibre.style.RasterTileSpec

/**
 * The user's own tile sources, as MapLibre basemaps.
 *
 * Shared by every map in this flavor that offers a basemap menu, so a source added once appears in all of them.
 */
@Composable
fun customRasterBasemaps(): List<Basemap.Raster> {
    val tileProviders: CustomTileProviderRepository = koinInject()
    val configs by tileProviders.getCustomTileProviders().collectAsStateWithLifecycle(emptyList())
    return configs.mapNotNull { config ->
        val tiles = config.tileUrl() ?: return@mapNotNull null
        Basemap.Raster(id = config.id, label = config.name, spec = RasterTileSpec(tiles = listOf(tiles)))
    }
}

/**
 * The tile URL for a source, or null if it cannot be resolved.
 *
 * A local archive becomes MapLibre's own `mbtiles://` scheme over the file's absolute path — the native MBTiles source
 * opens it with SQLite directly and takes no tile placeholders. `localUri` is a `file://` URI of a copy in app storage,
 * written when the archive was picked, so it resolves to a real path.
 *
 * The file must still be there. Handing MapLibre a path to a file that has gone does not fail softly: the native
 * MBTiles source aborts and takes the process with it — SIGABRT on a thread named MBTilesFileSour, with no Kotlin frame
 * to catch. An archive can disappear between sessions, so the check is not paranoia; the Google flavour guards its own
 * MBTiles provider the same way.
 */
private fun CustomTileProviderConfig.tileUrl(): String? = if (isLocal) {
    localUri?.let { uri ->
        val archive = runCatching { Uri.parse(uri).toFile() }.getOrNull()
        if (archive != null && archive.exists()) {
            mbTilesUrl(archive.absolutePath)
        } else {
            Logger.withTag("CustomBasemaps").w { "Skipping a local tile source whose archive is gone" }
            null
        }
    }
} else {
    urlTemplate.takeIf { it.isNotBlank() }
}
