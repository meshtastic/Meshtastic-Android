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

import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

private const val NOAA_RADAR_PREFIX = "https://opengeo.ncep.noaa.gov/geoserver/conus/"
private const val NOAA_RADAR_LAYER = "conus_bref_qcd"

/** The smoothed tile is drawn at twice the pixels of the tile it replaces, so the rounded edges stay crisp. */
private const val OUTPUT_SCALE = 2

/** A `minx,miny,maxx,maxy` bounding box. */
private const val BBOX_PARTS = 4

/** Pixels across a smoothed tile for a tile of [tilePixels], for the tile size to report to the renderer. */
fun smoothedRadarTileSize(tilePixels: Int): Int = tilePixels * OUTPUT_SCALE

/**
 * A radar tile drawn smooth instead of in squares.
 *
 * NOAA's radar is a grid of cells about 1 km across, and its WMS server fills a finer tile by copying them. Where a
 * tile pixel is finer than a cell, [from] returns a request for the cells themselves, one pixel per cell, and [render]
 * scales them up. Elsewhere it returns null and the server's own tile is right.
 */
class RadarSmoothRequest
private constructor(
    /** The server request for the cells under the tile, padded so neighboring tiles meet without a seam. */
    val paddedUrl: String,
    private val grid: RadarCellGrid,
    private val tilePixels: Int,
) {
    /**
     * Fetches the padded cells through [fetch] and returns the smooth tile as PNG bytes, or null when the fetch or the
     * decode fails, so the caller treats it as an ordinary failed tile.
     */
    suspend fun render(context: CoroutineContext, fetch: suspend (String) -> ByteArray?): ByteArray? {
        val png = fetch(paddedUrl) ?: return null
        return withContext(context) {
            val cells = decodePngPixels(png) ?: return@withContext null
            if (cells.width != grid.requestWidth || cells.height != grid.requestHeight) return@withContext null
            val size = smoothedRadarTileSize(tilePixels)
            smoothRadar(cells.argb, grid, size, size)?.let { encodePngPixels(RasterPixels(size, size, it)) }
        }
    }

    companion object {
        /** Whether [url] is a request to NOAA's radar layer, smoothed or not. */
        fun isRadarUrl(url: String): Boolean = url.startsWith(NOAA_RADAR_PREFIX) && "LAYERS=$NOAA_RADAR_LAYER" in url

        /** The smoothing request for a NOAA radar tile [url], or null when the tile needs none or is not radar. */
        @Suppress("ReturnCount")
        fun from(url: String): RadarSmoothRequest? {
            if (!isRadarUrl(url)) return null
            val query = url.substringAfter('?', missingDelimiterValue = "")
            val params =
                query.split('&').associate { it.substringBefore('=').uppercase() to it.substringAfter('=', "") }
            val box = params["BBOX"]?.replace("%2C", ",")?.split(',')?.mapNotNull { it.toDoubleOrNull() }
            val width = params["WIDTH"]?.toIntOrNull()
            if (box == null || box.size != BBOX_PARTS || width == null || params["HEIGHT"]?.toIntOrNull() != width) {
                return null
            }
            val grid =
                RadarCellGrid.of(box[0], box[1], box[2], box.last(), outputPixels = smoothedRadarTileSize(width))
                    ?: return null
            val padded =
                listOf(grid.paddedMinX, grid.paddedMinY, grid.paddedMaxX, grid.paddedMaxY).joinToString(",") {
                    it.toPlainDecimal()
                }
            val paddedQuery =
                query.split('&').joinToString("&") { part ->
                    when (part.substringBefore('=').uppercase()) {
                        "BBOX" -> "BBOX=$padded"
                        "WIDTH" -> "WIDTH=${grid.requestWidth}"
                        "HEIGHT" -> "HEIGHT=${grid.requestHeight}"
                        else -> part
                    }
                }
            return RadarSmoothRequest(url.substringBefore('?') + "?" + paddedQuery, grid, width)
        }
    }
}
