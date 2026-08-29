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
package org.meshtastic.feature.map.tiles

/**
 * Tile-scheme description for one raster source.
 *
 * Renderer-neutral: both map engines can draw XYZ tiles, and each turns this into its own tile source — MapLibre into a
 * raster source, the Google map into a `UrlTileProvider`. Placeholders are substituted by name rather than position, so
 * an ArcGIS-style `.../tile/{z}/{y}/{x}` template works unchanged.
 */
data class RasterTileSpec(
    val tiles: List<String>,
    val tileSize: Int = DEFAULT_TILE_SIZE,
    val minZoom: Int = 0,
    val maxZoom: Int = DEFAULT_MAX_ZOOM,
    val attributionHtml: String? = null,
) {
    companion object {
        const val DEFAULT_TILE_SIZE = 256
        const val DEFAULT_MAX_ZOOM = 19
    }
}
