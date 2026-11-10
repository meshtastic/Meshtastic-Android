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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.xr

import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Whether contour lines fall on round meters or round feet. */
enum class ContourUnits {
    Metric,
    Imperial,
}

/**
 * Contour lines as thin ribbons lying on the terrain: each drawn triangle is cut at every level that crosses it, so a
 * line sits exactly on the surface however steep or stretched the ground is. Every fifth line is an index line, drawn
 * wider.
 */
internal object TerrainContours {
    /** The spacing is widened until the lines fit in this many triangles. */
    const val TRIANGLE_BUDGET = 60_000

    /** The finest spacing is the one that puts at most this many lines between the lowest and highest ground. */
    const val MAX_LINES = 40

    /** Minor lines between one index line and the next, counting the index line. */
    const val INDEX_EVERY = 5
    const val MINOR_HALF_WIDTH = 0.00035f
    const val INDEX_HALF_WIDTH = 0.00075f

    /** Clear of the ground, so the two do not flicker where they would share a depth. */
    const val LIFT = 0.0008f
    private const val FOOT_METERS = 0.3048
    private const val SHORTEST = 1e-7f
    private val METERS = listOf(10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)
    private val FEET = listOf(20.0, 40.0, 100.0, 200.0, 500.0, 1000.0, 2000.0)

    // The 2D map's contour brown, lightened over an image, where the brown itself is lost in forest and shadow.
    private val INDEX_ON_IMAGE = Rgb(0.86f, 0.74f, 0.58f)
    private val MINOR_ON_IMAGE = Rgb(0.7f, 0.58f, 0.43f)
    private val INDEX_ON_COLORS = Rgb(0.36f, 0.22f, 0.1f)
    private val MINOR_ON_COLORS = Rgb(0.47f, 0.33f, 0.2f)

    /** Spacings in meters, finest first. */
    fun ladder(units: ContourUnits): List<Double> = when (units) {
        ContourUnits.Metric -> METERS
        ContourUnits.Imperial -> FEET.map { it * FOOT_METERS }
    }

    /** The finest spacing that keeps [reliefMeters] to [MAX_LINES] lines. */
    fun spacing(reliefMeters: Double, units: ContourUnits): Double {
        val ladder = ladder(units)
        return ladder.firstOrNull { reliefMeters / it <= MAX_LINES } ?: ladder.last()
    }

    /**
     * The lines over [geometry]'s surface, null on ground too flat to have any. [onImage] picks colors that read over a
     * draped image.
     */
    fun meshData(
        geometry: SpatialTerrainGeometry,
        units: ContourUnits,
        onImage: Boolean,
        triangleBudget: Int = TRIANGLE_BUDGET,
    ): TerrainMeshData? {
        val surface = Surface(geometry)
        if (surface.triangles == 0) return null
        val ladder = ladder(units)
        var step = ladder.indexOf(spacing(surface.highest - surface.lowest, units))
        while (step < ladder.lastIndex && 2 * surface.segments(ladder[step]) > triangleBudget) step++
        val spacing = ladder[step]
        val quads = Quads(surface.segments(spacing))
        val index = if (onImage) INDEX_ON_IMAGE else INDEX_ON_COLORS
        val minor = if (onImage) MINOR_ON_IMAGE else MINOR_ON_COLORS
        for (t in 0 until surface.triangles) {
            for (k in surface.levels(t, spacing)) {
                val isIndex = k % INDEX_EVERY == 0L
                surface.cut(t, k * spacing)?.let {
                    quads.ribbon(it, if (isIndex) INDEX_HALF_WIDTH else MINOR_HALF_WIDTH, if (isIndex) index else minor)
                }
            }
        }
        return quads.build()
    }

    /** One piece of a line across a triangle: its two ends, and the triangle's upward normal. */
    private class Piece(val from: FloatArray, val to: FloatArray, val normal: FloatArray)

    /** The drawn top of the terrain, without its skirt, with each vertex's height in meters. */
    private class Surface(geometry: SpatialTerrainGeometry) {
        private val positions = geometry.positions
        private val indices = geometry.indices
        private val altitudes =
            DoubleArray(geometry.columns * geometry.rows) { geometry.transform.altitude(positions[it * 3 + 1]) }
        val triangles = geometry.surfaceTriangleCount
        val lowest = altitudes.minOrNull() ?: 0.0
        val highest = altitudes.maxOrNull() ?: 0.0

        private fun altitude(triangle: Int, corner: Int) = altitudes[indices[triangle * 3 + corner]]

        /** The multiples of [spacing] above sea level that cross a triangle: above its lowest corner, up to its top. */
        fun levels(triangle: Int, spacing: Double): LongRange {
            val a = altitude(triangle, 0)
            val b = altitude(triangle, 1)
            val c = altitude(triangle, 2)
            val first = floor(min(a, min(b, c)) / spacing).toLong() + 1
            return max(first, 1L)..floor(max(a, max(b, c)) / spacing).toLong()
        }

        fun segments(spacing: Double): Int = (0 until triangles).sumOf { levels(it, spacing).count() }

        /** Where the triangle's two edges that straddle [level] meet it. */
        fun cut(triangle: Int, level: Double): Piece? {
            val ends = ArrayList<FloatArray>(2)
            for (corner in 0 until 3) {
                val p = indices[triangle * 3 + corner]
                val q = indices[triangle * 3 + (corner + 1) % 3]
                if ((altitudes[p] >= level) == (altitudes[q] >= level)) continue
                // From the lower index, so the triangle across this edge works out the very same point.
                val lo = min(p, q)
                val hi = max(p, q)
                val t = ((level - altitudes[lo]) / (altitudes[hi] - altitudes[lo])).toFloat()
                ends += FloatArray(3) { positions[lo * 3 + it] + (positions[hi * 3 + it] - positions[lo * 3 + it]) * t }
            }
            return if (ends.size == 2) Piece(ends[0], ends[1], normal(triangle)) else null
        }

        private fun normal(triangle: Int): FloatArray {
            val a = indices[triangle * 3] * 3
            val b = indices[triangle * 3 + 1] * 3
            val c = indices[triangle * 3 + 2] * 3
            val u = FloatArray(3) { positions[b + it] - positions[a + it] }
            val v = FloatArray(3) { positions[c + it] - positions[a + it] }
            val n = cross(u, v)
            val length = max(sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]), SHORTEST)
            val up = if (n[1] < 0) -length else length
            return FloatArray(3) { n[it] / up }
        }
    }

    /** Room for a known number of quads, written straight into the buffers. */
    private class Quads(capacity: Int) {
        private val positions = FloatArray(capacity * 12)
        private val normals = FloatArray(capacity * 12)
        private val colors = FloatArray(capacity * 16)
        private val indices = IntArray(capacity * 6)
        private var count = 0

        /** A strip [halfWidth] each side of the piece, lying in its triangle's plane and facing up from it. */
        fun ribbon(piece: Piece, halfWidth: Float, color: Rgb) {
            val along = FloatArray(3) { piece.to[it] - piece.from[it] }
            val length = sqrt(along[0] * along[0] + along[1] * along[1] + along[2] * along[2])
            if (length < SHORTEST) return
            for (i in 0 until 3) along[i] /= length
            val n = piece.normal
            val side = cross(n, along)
            // Counter-clockwise seen from above the triangle: along the line, then across it.
            val ends = listOf(piece.from, piece.to, piece.to, piece.from)
            for (corner in 0 until 4) {
                val across = if (corner < 2) -halfWidth else halfWidth
                val v = count * 4 + corner
                for (i in 0 until 3) {
                    positions[v * 3 + i] = ends[corner][i] + side[i] * across + n[i] * LIFT
                    normals[v * 3 + i] = n[i]
                }
                colors[v * 4] = color.r
                colors[v * 4 + 1] = color.g
                colors[v * 4 + 2] = color.b
                colors[v * 4 + 3] = 1f
            }
            val base = count * 4
            intArrayOf(base, base + 1, base + 2, base, base + 2, base + 3).copyInto(indices, count * 6)
            count++
        }

        fun build(): TerrainMeshData? = if (count == 0) {
            null
        } else {
            TerrainMeshData(
                positions.copyOf(count * 12),
                normals.copyOf(count * 12),
                colors.copyOf(count * 16),
                indices.copyOf(count * 6),
            )
        }
    }

    private fun cross(u: FloatArray, v: FloatArray) =
        floatArrayOf(u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
}
