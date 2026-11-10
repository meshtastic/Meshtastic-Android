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
package org.meshtastic.feature.xr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainTileMath
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A raster basemap the terrain can wear: XYZ tiles in Web Mercator, the same sources the 2D map offers.
 *
 * @param tileUrl a template with `{z}`, `{x}` and `{y}`
 * @param attribution plain text, shown while the layer is up
 */
data class TerrainImagery(
    val id: String,
    val label: String,
    val tileUrl: String,
    val maxZoom: Int,
    val attribution: String,
)

/** Which tiles cover a box, and where the box sits among them, in pixels at [zoom]. */
internal class ImageryPlan(val zoom: Int, val left: Double, val top: Double, val width: Double, val height: Double) {
    val columns: IntRange
        get() = floor(left / TILE).toInt()..floor((left + width - EDGE) / TILE).toInt()

    val rows: IntRange
        get() = floor(top / TILE).toInt()..floor((top + height - EDGE) / TILE).toInt()

    val tileCount: Int
        get() = columns.count() * rows.count()

    companion object {
        const val TILE = 256
        private const val EDGE = 1e-6

        /** The coarsest zoom that puts [targetPixels] across the box, held to [maxZoom] and to [maxTiles] tiles. */
        fun of(bounds: GeoBounds, maxZoom: Int, targetPixels: Int, maxTiles: Int): ImageryPlan {
            fun at(zoom: Int): ImageryPlan {
                val scale = TILE * (1 shl zoom).toDouble()
                val (west, north) = TerrainTileMath.worldFraction(bounds.north, bounds.west)
                val (east, south) = TerrainTileMath.worldFraction(bounds.south, bounds.east)
                return ImageryPlan(zoom, west * scale, north * scale, (east - west) * scale, (south - north) * scale)
            }
            var zoom = 0
            while (zoom < maxZoom && at(zoom).width < targetPixels) zoom++
            while (zoom > 0 && at(zoom).tileCount > maxTiles) zoom--
            return at(zoom)
        }
    }
}

/**
 * The image to drape over the terrain for [bounds]: [imagery]'s tiles stitched and cropped to exactly the box, as JPEG.
 * The mesh's texture coordinates are linear in longitude and in Mercator y, which is how the crop is laid out. Null
 * when no tile could be had, so the caller falls back to the elevation colors.
 */
internal suspend fun terrainImage(bounds: GeoBounds, imagery: TerrainImagery, http: HttpClient): ByteArray? {
    val plan = ImageryPlan.of(bounds, imagery.maxZoom, TARGET_PIXELS, MAX_TILES)
    val limit = Semaphore(PARALLEL_FETCHES)
    val tiles = coroutineScope {
        plan.rows
            .flatMap { y -> plan.columns.map { x -> x to y } }
            .map { (x, y) -> async { limit.withPermit { Triple(x, y, fetchTile(imagery, plan.zoom, x, y, http)) } } }
            .awaitAll()
    }
    if (tiles.all { it.third == null }) return null
    val shrink = minOf(1.0, TARGET_PIXELS / maxOf(plan.width, plan.height))
    val width = maxOf(1, (plan.width * shrink).roundToInt())
    val height = maxOf(1, (plan.height * shrink).roundToInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(MISSING_TILE)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    for ((x, y, tile) in tiles) {
        tile ?: continue
        val left = ((x * ImageryPlan.TILE - plan.left) * shrink).toFloat()
        val top = ((y * ImageryPlan.TILE - plan.top) * shrink).toFloat()
        val edge = (ImageryPlan.TILE * shrink).toFloat()
        // Rounded out, so neighbors overlap by a texel instead of leaving a seam.
        canvas.drawBitmap(tile, null, RectF(floor(left), floor(top), ceil(left + edge), ceil(top + edge)), paint)
        tile.recycle()
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    bitmap.recycle()
    return out.toByteArray()
}

private suspend fun fetchTile(imagery: TerrainImagery, zoom: Int, x: Int, y: Int, http: HttpClient): Bitmap? {
    val wrapped = Math.floorMod(x, 1 shl zoom)
    val url =
        imagery.tileUrl.replace("{z}", zoom.toString()).replace("{x}", wrapped.toString()).replace("{y}", y.toString())
    return try {
        val response = http.get(url) { header(HttpHeaders.UserAgent, USER_AGENT) }
        if (!response.status.isSuccess()) return null
        val bytes = response.bodyAsBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // One missing tile leaves a gray square; it does not fail the map. The URL would place the mesh.
        Logger.d { "Imagery tile failed: ${e::class.simpleName}" }
        null
    }
}

private const val TARGET_PIXELS = 2048
private const val MAX_TILES = 100
private const val PARALLEL_FETCHES = 8
private const val JPEG_QUALITY = 88
private const val USER_AGENT = "Meshtastic-Android"
private val MISSING_TILE = Color.rgb(96, 100, 104)
