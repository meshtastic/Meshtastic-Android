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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
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
    // Local (MBTiles-style) sources are skipped: MapLibre needs a URL template it can fetch, and serving a
    // local file to it is a separate piece of work.
    return configs
        .filterNot { it.isLocal }
        .map { config ->
            Basemap.Raster(
                id = config.id,
                label = config.name,
                spec = RasterTileSpec(tiles = listOf(config.urlTemplate)),
            )
        }
}
