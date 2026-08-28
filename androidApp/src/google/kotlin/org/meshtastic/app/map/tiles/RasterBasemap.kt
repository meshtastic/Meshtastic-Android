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
package org.meshtastic.app.map.tiles

import org.meshtastic.feature.map.tiles.CustomTileProviderConfig
import org.meshtastic.feature.map.tiles.RasterTileSpec
import org.meshtastic.feature.map.tiles.isValidTileUrlTemplate

/**
 * A raster basemap the Google map can draw, once a selected id has been resolved to something concrete.
 *
 * A source we ship and a source the user typed in differ only in where the URL template came from, so both arrive here
 * as [Remote] and the map stops caring which it is.
 */
sealed interface RasterBasemap {
    val id: String

    /** Tiles fetched from a server. */
    data class Remote(override val id: String, val spec: RasterTileSpec) : RasterBasemap

    /** An MBTiles archive stored on the device. */
    data class Local(override val id: String, val uri: String) : RasterBasemap
}

/**
 * The user's stored source as something drawable, or null if it is not.
 *
 * A template that fails validation yields null rather than a provider that would request nonsense: the stored value is
 * whatever was typed, and it can also have been valid when saved and edited into nonsense since.
 */
internal fun CustomTileProviderConfig.toRasterBasemap(): RasterBasemap? {
    val archive = localUri
    return when {
        archive != null -> RasterBasemap.Local(id = id, uri = archive)

        urlTemplate.isValidTileUrlTemplate(requireHttps = false) ->
            RasterBasemap.Remote(id = id, spec = RasterTileSpec(tiles = listOf(urlTemplate)))

        else -> null
    }
}
