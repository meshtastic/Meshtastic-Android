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
@file:Suppress("MagicNumber", "ReturnCount", "LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")

package org.meshtastic.feature.map.terrain.spatial

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

/** Where the spatial map reads elevation from: the terrain store in the app, a synthetic source in tests. */
interface ElevationTileSource {
    suspend fun elevationTile(z: Int, x: Int, y: Int): ElevationTile?

    /** The ground the tile's archive covers, null when nothing holds it. */
    suspend fun coverage(z: Int, x: Int, y: Int): GeoBounds?

    /** The finest zoom any configured archive carries, null with no archives. */
    suspend fun finestDataZoom(): Int?
}

/** Tile-grid math the height grid samples through. */
object TerrainSampling {
    const val TILE_SIZE = 256
    private const val EQUATOR_METERS = 40_075_016.686

    /** A coordinate located within a tile grid: the tile indices plus the sample-centered pixel position inside it. */
    data class TilePixel(val x: Int, val y: Int, val px: Double, val py: Double)

    fun tilePixel(latitude: Double, longitude: Double, z: Int, size: Int): TilePixel {
        val n = (1 shl z).toDouble()
        val lat = latitude.coerceIn(-85.05112878, 85.05112878)
        val lon = longitude.coerceIn(-180.0, 180.0)
        val xt = (lon + 180) / 360 * n
        val latRad = lat * PI / 180
        val yt = (1 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2 * n
        val maxIndex = (1 shl z) - 1
        val x = floor(xt).toInt().coerceIn(0, maxIndex)
        val y = floor(yt).toInt().coerceIn(0, maxIndex)
        return TilePixel(x, y, (xt - x) * size - 0.5, (yt - y) * size - 0.5)
    }

    /** Bilinear elevation at a sample-centered pixel position, edge-clamped. */
    fun sample(tile: ElevationTile, px: Double, py: Double): Float {
        val x0 = floor(px).toInt()
        val y0 = floor(py).toInt()
        val tx = (px - x0).toFloat()
        val ty = (py - y0).toFloat()
        val p00 = tile.elevationAt(x0, y0)
        val p10 = tile.elevationAt(x0 + 1, y0)
        val p01 = tile.elevationAt(x0, y0 + 1)
        val p11 = tile.elevationAt(x0 + 1, y0 + 1)
        val top = p00 + (p10 - p00) * tx
        val bottom = p01 + (p11 - p01) * tx
        return top + (bottom - top) * ty
    }

    /** Ground meters between samples of a tile at the given zoom and row. */
    fun metersPerSample(z: Int, y: Int, size: Int): Double {
        val n = (1 shl z).toDouble()
        val latitude = atan(sinh(PI * (1 - 2 * (y + 0.5) / n))) * 180 / PI
        return EQUATOR_METERS * cos(latitude * PI / 180) / n / size
    }

    fun equatorMeters(): Double = EQUATOR_METERS
}

/** Elevations sampled on a regular lon/lat grid over a box: the ground the terrain mesh is built on. */
class SpatialHeightGrid(
    val columns: Int,
    val rows: Int,
    /** Row-major, north to south, west to east. Meters above sea level. */
    val heights: FloatArray,
    val covered: BooleanArray,
    /** Lowest covered height after cleaning (meters); 0 with no coverage. */
    val baseElevation: Float,
    val maxElevation: Float,
) {
    /** How many vertices across and down, and which tile zoom to read them from. */
    data class Plan(val columns: Int, val rows: Int, val samplingZoom: Int) {
        val triangleCount: Int
            get() = 2 * (columns - 1) * (rows - 1)
    }

    val hasAnyTerrain: Boolean
        get() = covered.any { it }

    /** Cleaned, then bilinear height at a fraction across the grid (0..1 west to east, 0..1 north to south). */
    fun height(u: Double, v: Double): Float {
        val x = u.coerceIn(0.0, 1.0) * (columns - 1)
        val y = v.coerceIn(0.0, 1.0) * (rows - 1)
        val x0 = min(x.toInt(), columns - 2)
        val y0 = min(y.toInt(), rows - 2)
        val tx = (x - x0).toFloat()
        val ty = (y - y0).toFloat()
        val p00 = heights[y0 * columns + x0]
        val p10 = heights[y0 * columns + x0 + 1]
        val p01 = heights[(y0 + 1) * columns + x0]
        val p11 = heights[(y0 + 1) * columns + x0 + 1]
        val top = p00 + (p10 - p00) * tx
        val bottom = p01 + (p11 - p01) * tx
        return top + (bottom - top) * ty
    }

    /**
     * Sea clamp, median-of-9 despike (the source data has isolated spikes on water), flat water, then uncovered cells
     * sit at the lowest covered height with a short feathered slope into it.
     */
    fun cleaned(featherCells: Int = 6): SpatialHeightGrid {
        val count = columns * rows
        if (count == 0) return this
        val clamped = FloatArray(count) { if (covered[it]) max(heights[it], 0f) else 0f }

        val filtered = clamped.copyOf()
        val window = FloatArray(9)
        for (j in 0 until rows) {
            for (i in 0 until columns) {
                val index = j * columns + i
                if (!covered[index]) continue
                var n = 0
                for (dj in -1..1) {
                    val jj = (j + dj).coerceIn(0, rows - 1)
                    for (di in -1..1) {
                        val ii = (i + di).coerceIn(0, columns - 1)
                        val neighbor = jj * columns + ii
                        window[n++] = if (covered[neighbor]) clamped[neighbor] else clamped[index]
                    }
                }
                val median = medianOfNine(window)
                filtered[index] = if (median < 0.5f) 0f else median
            }
        }

        var base = Float.MAX_VALUE
        var top = 0f
        for (index in 0 until count) {
            if (covered[index]) {
                base = min(base, filtered[index])
                top = max(top, filtered[index])
            }
        }
        if (base == Float.MAX_VALUE) base = 0f

        // Chamfer distance (in cells) from the nearest uncovered cell, two passes.
        val far = Int.MAX_VALUE / 2
        val distance = IntArray(count) { if (covered[it]) far else 0 }
        if (distance.any { it == 0 } && featherCells > 0) {
            for (j in 0 until rows) {
                for (i in 0 until columns) {
                    val index = j * columns + i
                    if (i > 0) distance[index] = min(distance[index], distance[index - 1] + 1)
                    if (j > 0) distance[index] = min(distance[index], distance[index - columns] + 1)
                }
            }
            for (j in rows - 1 downTo 0) {
                for (i in columns - 1 downTo 0) {
                    val index = j * columns + i
                    if (i < columns - 1) distance[index] = min(distance[index], distance[index + 1] + 1)
                    if (j < rows - 1) distance[index] = min(distance[index], distance[index + columns] + 1)
                }
            }
        }
        for (index in 0 until count) {
            if (!covered[index]) {
                filtered[index] = base
            } else if (featherCells > 0 && distance[index] < featherCells) {
                filtered[index] = base + (filtered[index] - base) * distance[index].toFloat() / featherCells
            }
        }
        return SpatialHeightGrid(columns, rows, filtered, covered, base, max(top, base))
    }

    /** The same ground on a grid at least [minimum] vertices across and down, read bilinearly. */
    fun upsampled(minimum: Int): SpatialHeightGrid {
        if (columns >= minimum && rows >= minimum) return this
        val newColumns = max(columns, minimum)
        val newRows = max(rows, minimum)
        val newHeights = FloatArray(newColumns * newRows)
        val newCovered = BooleanArray(newColumns * newRows)
        for (j in 0 until newRows) {
            val v = j.toDouble() / (newRows - 1)
            for (i in 0 until newColumns) {
                val u = i.toDouble() / (newColumns - 1)
                newHeights[j * newColumns + i] = height(u, v)
                val nearest = (v * (rows - 1)).roundToInt() * columns + (u * (columns - 1)).roundToInt()
                newCovered[j * newColumns + i] = covered[nearest]
            }
        }
        return SpatialHeightGrid(newColumns, newRows, newHeights, newCovered, baseElevation, maxElevation)
    }

    companion object {
        /**
         * Picks a grid that stays under the triangle budget and does not sample finer than twice the data resolution
         * (overzoomed tiles are just bilinear upsampling).
         */
        fun plan(frame: SpatialFrame, maxTriangles: Int = 400_000, dataMaxZoom: Int): Plan {
            val width = max(frame.widthMeters, 1.0)
            val depth = max(frame.depthMeters, 1.0)
            val aspect = width / depth
            val rowsMinusOne = max(1, sqrt(maxTriangles / (2 * aspect)).toInt())
            val columnsMinusOne = max(1, (aspect * rowsMinusOne).toInt())
            val budgetStep = max(width / columnsMinusOne, depth / rowsMinusOne)
            val dataStep =
                TerrainSampling.equatorMeters() * cos(frame.centerLatitude * PI / 180) /
                    (1 shl dataMaxZoom).toDouble() /
                    TerrainSampling.TILE_SIZE
            val step = max(budgetStep, dataStep / 2)
            val columns = max(2, (width / step).toInt() + 1)
            val rows = max(2, (depth / step).toInt() + 1)
            val pixel =
                TerrainSampling.tilePixel(
                    frame.centerLatitude,
                    frame.centerLongitude,
                    8,
                    TerrainSampling.TILE_SIZE,
                )
            var zoom = 8
            while (zoom < min(dataMaxZoom + 2, 16)) {
                val y = pixel.y shl (zoom - 8)
                if (TerrainSampling.metersPerSample(zoom, y, TerrainSampling.TILE_SIZE) <= step) break
                zoom++
            }
            return Plan(columns, rows, zoom)
        }

        /**
         * Reads every vertex through the tiles at the plan's zoom, one tile fetch per tile. Vertices outside the
         * downloaded coverage are uncovered; the caller cleans and fills them.
         */
        suspend fun sample(frame: SpatialFrame, plan: Plan, source: ElevationTileSource): SpatialHeightGrid {
            val size = TerrainSampling.TILE_SIZE
            val z = plan.samplingZoom
            val bounds = frame.bounds
            val longitudes = List(plan.columns) { bounds.west + (bounds.east - bounds.west) * it / (plan.columns - 1) }
            val latitudes = List(plan.rows) { bounds.north - (bounds.north - bounds.south) * it / (plan.rows - 1) }
            // tilePixel is separable: x/px depend on longitude only, y/py on latitude only.
            val columnPixels = longitudes.map { TerrainSampling.tilePixel(frame.centerLatitude, it, z, size) }
            val rowPixels = latitudes.map { TerrainSampling.tilePixel(it, frame.centerLongitude, z, size) }

            val heights = FloatArray(plan.columns * plan.rows)
            val covered = BooleanArray(plan.columns * plan.rows)
            val tiles = HashMap<Long, Pair<ElevationTile, GeoBounds>?>()
            var currentTileRow = -1
            for ((j, row) in rowPixels.withIndex()) {
                if (row.y != currentTileRow) {
                    tiles.clear()
                    currentTileRow = row.y
                    currentCoroutineContext().ensureActive()
                }
                for ((i, column) in columnPixels.withIndex()) {
                    val key = (column.x.toLong() shl 32) or row.y.toLong()
                    val entry =
                        if (tiles.containsKey(key)) {
                            tiles[key]
                        } else {
                            val coverage = source.coverage(z, column.x, row.y)
                            val tile = if (coverage != null) source.elevationTile(z, column.x, row.y) else null
                            (if (coverage != null && tile != null) tile to coverage else null).also {
                                tiles[key] = it
                            }
                        }
                    if (entry == null || !entry.second.contains(LatLon(latitudes[j], longitudes[i]))) continue
                    val index = j * plan.columns + i
                    // Pixels are in a [size]-pixel tile; a source may hand back a finer one (Mapterhorn's are 512).
                    // Scaled about the texel's own edge, so the half-texel centering is not scaled with it.
                    val tile = entry.first
                    heights[index] =
                        TerrainSampling.sample(
                            tile,
                            (column.px + 0.5) * tile.width / size - 0.5,
                            (row.py + 0.5) * tile.height / size - 0.5,
                        )
                    covered[index] = true
                }
            }
            return SpatialHeightGrid(plan.columns, plan.rows, heights, covered, 0f, 0f)
        }

        /** Median of nine values by a fixed comparison network, no sorting allocation per cell. */
        fun medianOfNine(v: FloatArray): Float {
            fun swap(a: Int, b: Int) {
                if (v[a] > v[b]) {
                    val t = v[a]
                    v[a] = v[b]
                    v[b] = t
                }
            }
            swap(1, 2)
            swap(4, 5)
            swap(7, 8)
            swap(0, 1)
            swap(3, 4)
            swap(6, 7)
            swap(1, 2)
            swap(4, 5)
            swap(7, 8)
            swap(0, 3)
            swap(5, 8)
            swap(4, 7)
            swap(3, 6)
            swap(1, 4)
            swap(2, 5)
            swap(4, 7)
            swap(4, 2)
            swap(6, 4)
            swap(4, 2)
            return v[4]
        }
    }
}
