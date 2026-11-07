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
package org.meshtastic.app.map.offline.terrain

import android.graphics.Bitmap
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.Hillshade
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.terrain.TileIndex
import org.meshtastic.feature.map.terrain.decodeTerrariumTile
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private val LOG = Logger.withTag("HillshadeTileProvider")

/**
 * Pre-rendered hillshade tiles for the Google flavor: shadow-alpha PNGs decoded from [store]'s downloaded Terrarium
 * tiles, following the same shape as [org.meshtastic.app.map.MBTilesProvider] and
 * [org.meshtastic.app.map.tiles.RasterTileProvider] — Google's [TileProvider.getTile] is a synchronous callback the
 * Maps SDK already invokes off the main thread, so this does its decode+shade+encode work inline rather than returning
 * a `Deferred`/`Flow`; there's nowhere to hand async work off to that the SDK would wait on anyway.
 *
 * Each output pixel is black RGB with shadow depth as alpha, matching [Hillshade.shade]'s own contract: it composites
 * correctly over any basemap without a separate light/dark palette. [Hillshade.shade] needs a margin ring of real
 * neighbor-tile elevation data around the tile it shades ([ElevationStitcher] assembles that, gracefully clamping to
 * this tile's own edge wherever a neighbor wasn't downloaded — see its own doc comment).
 *
 * A tile past the deepest downloaded zoom at its spot is resampled from the nearest parent that has data, fading out
 * over the zoom after one past the data and absent beyond. With [shadeValleys], ground that sees little sky also
 * darkens.
 *
 * [getTile] is called concurrently by the Maps SDK from multiple threads, so both caches below are guarded by
 * `synchronized`, as is [org.meshtastic.app.map.offline.pmtiles.OfflineVectorRenderer]'s identically-shaped tile cache.
 */
internal class HillshadeTileProvider(private val store: TerrainTileStore, private val shadeValleys: Boolean = false) :
    TileProvider {

    private val elevationCache =
        object : LinkedHashMap<String, ElevationTile>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ElevationTile>): Boolean =
                size > MAX_CACHED_ELEVATION_TILES
        }

    private val renderedCache =
        object : LinkedHashMap<String, Tile>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Tile>): Boolean =
                size > MAX_CACHED_RENDERED_TILES
        }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val key = "$zoom/$x/$y"
        val cached = synchronized(renderedCache) { renderedCache[key] }
        if (cached != null) return cached

        val levels = (0..MAX_OVERZOOM_LEVELS).firstOrNull { hasData(zoom - it, x shr it, y shr it) }
        val strength = levels?.let { Hillshade.strength(zoom, zoom - it) } ?: 0f
        val parent = levels?.takeIf { strength > 0f }?.let { decode(zoom - it, x shr it, y shr it) }
        val tile = if (levels == null || parent == null) null else render(x, y, zoom, levels, parent, strength)
        return tile?.also { synchronized(renderedCache) { renderedCache[key] = it } } ?: TileProvider.NO_TILE
    }

    private fun render(x: Int, y: Int, zoom: Int, levels: Int, parent: ElevationTile, strength: Float): Tile {
        val size = parent.width
        val metersPerSample = Hillshade.metersPerSample(zoom, y, size)
        val margin = if (shadeValleys) Hillshade.valleyMargin(metersPerSample) else Hillshade.MARGIN
        val parentMargin = if (levels == 0) margin else Hillshade.overzoomMargin(margin, levels)
        val parentZoom = zoom - levels
        val parentX = x shr levels
        val parentY = y shr levels
        val neighbors =
            NEIGHBOR_OFFSETS.mapNotNull { (dx, dy) ->
                decode(parentZoom, parentX + dx, parentY + dy)?.let { (dx to dy) to it }
            }
                .toMap()
        val padded = ElevationStitcher.buildPadded(parent, neighbors, parentMargin)
        val mask = (1 shl levels) - 1
        val grid =
            if (levels == 0) {
                padded
            } else {
                Hillshade.overzoom(padded, parentMargin, levels, x and mask, y and mask, size, margin)
            }
        val shadow =
            Hillshade.shade(
                grid,
                size,
                size,
                MAX_SHADOW_ALPHA * strength,
                metersPerSample,
                margin,
                shadeValleys,
            )
        return Tile(size, size, renderShadowPng(shadow, size, size))
    }

    private fun hasData(zoom: Int, x: Int, y: Int): Boolean =
        zoom >= 0 && store.hasTile(terrainSourceForZoom(zoom), TileIndex(zoom, x, y))

    private fun decode(zoom: Int, x: Int, y: Int): ElevationTile? {
        val source = terrainSourceForZoom(zoom)
        val tile = TileIndex(zoom, x, y)
        val key = "${source.dirName}/$zoom/$x/$y"
        val cached = synchronized(elevationCache) { elevationCache[key] }
        if (cached != null) return cached

        val decoded = store.readTile(source, tile)?.let { bytes -> decodeSafely(bytes) }
        if (decoded != null) synchronized(elevationCache) { elevationCache[key] = decoded }
        return decoded
    }

    private fun decodeSafely(bytes: ByteArray): ElevationTile? = try {
        decodeTerrariumTile(bytes)
    } catch (e: IllegalStateException) {
        // No tile coordinates in the message: zoom/x/y is location-adjacent data (AGENTS.md Privacy First).
        LOG.w(e) { "Could not decode a downloaded Terrarium tile" }
        null
    }

    private fun renderShadowPng(shadow: FloatArray, width: Int, height: Int): ByteArray {
        // RGB is always black (0,0,0), so premultiplied-vs-straight alpha makes no difference to the pixels
        // encoded here (0 times any alpha is still 0) — unlike ElevationTile's own Terrarium decode, which must
        // disable premultiplication because its elevation data actually lives in non-zero RGB channels.
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (i in shadow.indices) {
            val alpha = (shadow[i] * BYTE_MAX).roundToInt().coerceIn(0, BYTE_MAX.toInt())
            pixels[i] = alpha shl ALPHA_SHIFT
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    internal companion object {
        /**
         * Fixed shadow strength, matching [Hillshade]'s own fixed light source (azimuth/altitude aren't configurable
         * either). Not tuned against real on-device rendering — no display was available in this environment; revisit
         * alongside [Hillshade]'s own `DESPIKE_THRESHOLD_METERS` once one is.
         */
        const val MAX_SHADOW_ALPHA = 0.5f

        private val NEIGHBOR_OFFSETS = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

        private const val MAX_OVERZOOM_LEVELS = 2

        private const val MAX_CACHED_ELEVATION_TILES = 128
        private const val MAX_CACHED_RENDERED_TILES = 64
        private const val INITIAL_CAPACITY = 16
        private const val LOAD_FACTOR = 0.75f

        private const val BYTE_MAX = 255f
        private const val ALPHA_SHIFT = 24
        private const val PNG_QUALITY = 100
    }
}
