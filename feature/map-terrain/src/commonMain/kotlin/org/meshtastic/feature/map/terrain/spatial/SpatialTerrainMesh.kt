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

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Scales the local meters frame into the model: the box fits a footprint of about a meter and relief is exaggerated so
 * a tabletop model still reads as hills.
 */
class SpatialModelTransform(
    val frame: SpatialFrame,
    /** Meters above sea level that sit at y = 0. */
    val baseElevation: Double,
    maxElevation: Double,
    verticalExaggeration: Double = 1.5,
    modelSize: Vec3 = Vec3(1f, 0.3f, 1f),
) {
    /** Model units per ground meter. */
    val scale: Double

    /** Effective exaggeration after capping to the model height. */
    val verticalExaggeration: Double

    /** x and z extents of the ground in model units. */
    val footprint: Vec2

    init {
        val width = max(frame.widthMeters, 1.0)
        val depth = max(frame.depthMeters, 1.0)
        scale = min(modelSize.x / width, modelSize.z / depth)
        val relief = max(maxElevation - baseElevation, 0.0) * scale
        val cap = if (relief > 0) modelSize.y / relief else verticalExaggeration
        this.verticalExaggeration = max(min(verticalExaggeration, cap), 0.01)
        footprint = Vec2((width * scale).toFloat(), (depth * scale).toFloat())
    }

    /** Radius of the round map, the circle inscribed in the footprint, in model units. */
    val diskRadius: Float
        get() = min(footprint.x, footprint.y) / 2

    fun height(altitude: Double): Float = ((altitude - baseElevation) * verticalExaggeration * scale).toFloat()

    fun altitude(height: Float): Double = height / (verticalExaggeration * scale) + baseElevation

    fun position(coordinate: LatLon, altitude: Double): Vec3 {
        val meters = frame.localPosition(coordinate, 0.0)
        return Vec3((meters.x * scale).toFloat(), height(altitude), (meters.z * scale).toFloat())
    }

    fun coordinate(x: Float, z: Float): LatLon = frame.coordinate((x / scale).toFloat(), (z / scale).toFloat())
}

/** The buffers of a mesh drawn over the terrain. Positions and normals are xyz triples, uvs are uv pairs. */
class SpatialOverlayMesh(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
)

/**
 * The terrain mesh plus the height field it was built from, in model units. The mesh is a disk inscribed in the box: a
 * regular grid with the vertices outside the circle moved onto it, and a round skirt below the edge so the model reads
 * as a solid table. Buffers are flat: [positions] and [normals] hold xyz triples, [textureCoordinates] uv pairs.
 */
class SpatialTerrainGeometry(
    source: SpatialHeightGrid,
    val transform: SpatialModelTransform,
    skirtDepth: Float = DEFAULT_SKIRT_DEPTH,
) {
    val columns: Int
    val rows: Int

    /** Row-major ground heights in model units (y). */
    val groundHeights: FloatArray
    val covered: BooleanArray
    val hasAnyTerrain: Boolean
    val positions: FloatArray
    val normals: FloatArray
    val textureCoordinates: FloatArray
    val indices: IntArray
    val surfaceTriangleCount: Int

    /** Vertices on the top of the skirt, copies of the surface's edge; the same number again sit below them. */
    val skirtRingCount: Int

    /** Height of the highest point on the map's edge, in model units, so the bezel can stand above it. */
    val rimTop: Float

    init {
        val grid = source.upsampled(MINIMUM_GRID_SIZE)
        columns = grid.columns
        rows = grid.rows
        covered = grid.covered
        hasAnyTerrain = grid.hasAnyTerrain
        val frame = transform.frame
        val bounds = frame.bounds
        val radius = transform.diskRadius
        val count = columns * rows
        val heights = FloatArray(count) { transform.height(grid.heights[it].toDouble()) }
        groundHeights = heights

        // Surface normals from the height field: (-dh/dx, 1, -dh/dz), one-sided at the edges.
        val dx = transform.footprint.x / (columns - 1)
        val dz = transform.footprint.y / (rows - 1)
        val gridNormals = FloatArray(count * 3)
        for (j in 0 until rows) {
            for (i in 0 until columns) {
                val left = heights[j * columns + max(i - 1, 0)]
                val right = heights[j * columns + min(i + 1, columns - 1)]
                val north = heights[max(j - 1, 0) * columns + i]
                val south = heights[min(j + 1, rows - 1) * columns + i]
                val spanX = (min(i + 1, columns - 1) - max(i - 1, 0)) * dx
                val spanZ = (min(j + 1, rows - 1) - max(j - 1, 0)) * dz
                normalizeInto(gridNormals, j * columns + i, -(right - left) / spanX, 1f, -(south - north) / spanZ)
            }
        }

        val pos = FloatArray(count * 3)
        val nrm = gridNormals.copyOf()
        val uv = FloatArray(count * 2)
        val moved = BooleanArray(count)
        val lonSpan = bounds.east - bounds.west
        val latSpan = bounds.north - bounds.south

        // A point on the ground at a model x/z, written to vertex [index]: height and normal read bilinearly off the
        // grid, texture coordinate from its coordinate in the Mercator image.
        fun surfacePoint(index: Int, x: Float, z: Float) {
            val coordinate = transform.coordinate(x, z)
            val u = (coordinate.longitude - bounds.west) / lonSpan
            val v = (bounds.north - coordinate.latitude) / latSpan
            val gx = u.coerceIn(0.0, 1.0) * (columns - 1)
            val gy = v.coerceIn(0.0, 1.0) * (rows - 1)
            val x0 = min(gx.toInt(), columns - 2)
            val y0 = min(gy.toInt(), rows - 2)
            val tx = (gx - x0).toFloat()
            val ty = (gy - y0).toFloat()
            val i00 = y0 * columns + x0
            val i10 = i00 + 1
            val i01 = i00 + columns
            val i11 = i01 + 1
            val w00 = (1 - tx) * (1 - ty)
            val w10 = tx * (1 - ty)
            val w01 = (1 - tx) * ty
            val w11 = tx * ty
            val height = heights[i00] * w00 + heights[i10] * w10 + heights[i01] * w01 + heights[i11] * w11
            fun blend(axis: Int) = gridNormals[i00 * 3 + axis] * w00 +
                gridNormals[i10 * 3 + axis] * w10 +
                gridNormals[i01 * 3 + axis] * w01 +
                gridNormals[i11 * 3 + axis] * w11
            pos[index * 3] = x
            pos[index * 3 + 1] = height
            pos[index * 3 + 2] = z
            normalizeInto(nrm, index, blend(0), blend(1), blend(2))
            val mercatorV = frame.mercatorFraction(coordinate.latitude, coordinate.longitude).second
            uv[index * 2] = u.toFloat()
            uv[index * 2 + 1] = if (FLIP_TEXTURE_V) (1 - mercatorV).toFloat() else mercatorV.toFloat()
        }

        for (j in 0 until rows) {
            val latitude = bounds.north - latSpan * j / (rows - 1)
            val v = frame.mercatorFraction(latitude, bounds.west).second
            for (i in 0 until columns) {
                val longitude = bounds.west + lonSpan * i / (columns - 1)
                val index = j * columns + i
                val p = transform.position(LatLon(latitude, longitude), grid.heights[index].toDouble())
                val distance = sqrt(p.x * p.x + p.z * p.z)
                if (distance > radius) {
                    // Outside the circle: onto it at the same angle, with the ground found there.
                    surfacePoint(index, p.x * radius / distance, p.z * radius / distance)
                    moved[index] = true
                } else {
                    pos[index * 3] = p.x
                    pos[index * 3 + 1] = p.y
                    pos[index * 3 + 2] = p.z
                    uv[index * 2] = i.toFloat() / (columns - 1)
                    uv[index * 2 + 1] = if (FLIP_TEXTURE_V) (1 - v).toFloat() else v.toFloat()
                }
            }
        }

        // Two counter-clockwise (seen from above) triangles per cell. Triangles squeezed onto the edge (every corner
        // moved) or folded flat by the move are left out.
        val minimumArea = dx * dz * 1e-3f
        val tri = ArrayList<Int>((columns - 1) * (rows - 1) * 6)
        fun add(a: Int, b: Int, c: Int): Boolean {
            if (moved[a] && moved[b] && moved[c]) return false
            val doubleArea =
                (pos[b * 3 + 2] - pos[a * 3 + 2]) * (pos[c * 3] - pos[a * 3]) -
                    (pos[b * 3] - pos[a * 3]) * (pos[c * 3 + 2] - pos[a * 3 + 2])
            if (doubleArea <= minimumArea) return false
            tri.add(a)
            tri.add(b)
            tri.add(c)
            return true
        }
        // Which of each cell's two triangles were kept: [cell * 2] is a-c-b, [cell * 2 + 1] is b-c-d.
        val kept = BooleanArray((columns - 1) * (rows - 1) * 2)
        for (j in 0 until rows - 1) {
            for (i in 0 until columns - 1) {
                val a = j * columns + i
                val b = a + 1
                val c = a + columns
                val d = c + 1
                val cell = j * (columns - 1) + i
                kept[cell * 2] = add(a, c, b)
                kept[cell * 2 + 1] = add(b, c, d)
            }
        }
        surfaceTriangleCount = tri.size / 3

        var ringCount = 0
        var rim = 0f
        var extraPositions = FloatArray(0)
        var extraNormals = FloatArray(0)
        var extraUvs = FloatArray(0)
        if (skirtDepth > 0) {
            fun isKept(i: Int, j: Int, second: Int): Boolean =
                i >= 0 && j >= 0 && i < columns - 1 && j < rows - 1 && kept[(j * (columns - 1) + i) * 2 + second]
            // Edges in the order their triangle lists them, which runs counter-clockwise seen from above.
            val edges = ArrayList<Int>()
            fun edge(p: Int, q: Int) {
                edges.add(p)
                edges.add(q)
            }
            for (j in 0 until rows - 1) {
                for (i in 0 until columns - 1) {
                    val a = j * columns + i
                    val b = a + 1
                    val c = a + columns
                    val d = c + 1
                    if (isKept(i, j, 0)) {
                        if (!isKept(i - 1, j, 1)) edge(a, c)
                        if (!isKept(i, j, 1)) edge(c, b)
                        if (!isKept(i, j - 1, 1)) edge(b, a)
                    }
                    if (isKept(i, j, 1)) {
                        if (!isKept(i, j, 0)) edge(b, c)
                        if (!isKept(i, j + 1, 0)) edge(c, d)
                        if (!isKept(i + 1, j, 0)) edge(d, b)
                    }
                }
            }
            val ring = IntArray(count) { -1 }
            val ringVertices = ArrayList<Int>()
            for (v in edges) {
                if (ring[v] == -1) {
                    ring[v] = ringVertices.size
                    ringVertices.add(v)
                }
            }
            val n = ringVertices.size
            val base = count
            extraPositions = FloatArray(n * 2 * 3)
            extraNormals = FloatArray(n * 2 * 3)
            extraUvs = FloatArray(n * 2 * 2)
            for (layer in 0..1) {
                for ((k, v) in ringVertices.withIndex()) {
                    val o = layer * n + k
                    val px = pos[v * 3]
                    val pz = pos[v * 3 + 2]
                    normalizeInto(extraNormals, o, px, 0f, pz)
                    extraUvs[o * 2] = uv[v * 2]
                    extraUvs[o * 2 + 1] = uv[v * 2 + 1]
                    extraPositions[o * 3] = px
                    extraPositions[o * 3 + 1] = if (layer == 1) -skirtDepth else pos[v * 3 + 1]
                    extraPositions[o * 3 + 2] = pz
                }
            }
            for (e in 0 until edges.size / 2) {
                val pTop = base + ring[edges[e * 2]]
                val qTop = base + ring[edges[e * 2 + 1]]
                val pBelow = pTop + n
                val qBelow = qTop + n
                tri.addAll(listOf(qTop, pTop, qBelow, pTop, pBelow, qBelow))
            }
            ringCount = n
            rim = ringVertices.maxOfOrNull { pos[it * 3 + 1] } ?: 0f
        } else {
            rim = (0 until count).filter { moved[it] }.maxOfOrNull { pos[it * 3 + 1] } ?: 0f
        }
        skirtRingCount = ringCount
        rimTop = rim
        positions = pos + extraPositions
        normals = nrm + extraNormals
        textureCoordinates = uv + extraUvs
        indices = tri.toIntArray()
    }

    /** Ground height (model units) under a coordinate, clamped to the box. */
    fun groundHeight(coordinate: LatLon): Float {
        val bounds = transform.frame.bounds
        val u = (coordinate.longitude - bounds.west) / (bounds.east - bounds.west)
        val v = (bounds.north - coordinate.latitude) / (bounds.north - bounds.south)
        return SpatialHeightGrid(columns, rows, groundHeights, covered, 0f, 0f).height(u, v)
    }

    /** Where to put something that sits on the ground at a coordinate. */
    fun surfacePosition(coordinate: LatLon, clearance: Float = 0f): Vec3 {
        val p = transform.position(coordinate, transform.baseElevation)
        return Vec3(p.x, groundHeight(coordinate) + clearance, p.z)
    }

    /**
     * The top surface alone, raised by [lift], for an overlay drawn over the terrain with the same texture coordinates.
     * The skirt's vertices and triangles come after the surface's, so the first `columns * rows` vertices and
     * [surfaceTriangleCount] triangles are the surface.
     */
    fun overlaySurface(lift: Float = OVERLAY_LIFT): SpatialOverlayMesh {
        val count = columns * rows
        val lifted = positions.copyOf(count * 3)
        for (i in 0 until count) lifted[i * 3 + 1] += lift
        return SpatialOverlayMesh(
            lifted,
            normals.copyOf(count * 3),
            textureCoordinates.copyOf(count * 2),
            indices.copyOf(surfaceTriangleCount * 3),
        )
    }

    companion object {
        /** The north edge is v = 1 for renderers that sample textures with v = 0 at the bottom of the image. */
        const val FLIP_TEXTURE_V = true

        /** Coarser grids are resampled to this many vertices across, so the edge still has enough to look round. */
        const val MINIMUM_GRID_SIZE = 97

        /** How far the skirt hangs below y = 0, in model units. */
        const val DEFAULT_SKIRT_DEPTH = 0.02f

        /** How far an overlay floats over the ground, in model units: a couple of millimeters on the table. */
        const val OVERLAY_LIFT = 0.003f

        private fun normalizeInto(buffer: FloatArray, index: Int, x: Float, y: Float, z: Float) {
            val length = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: 1f
            buffer[index * 3] = x / length
            buffer[index * 3 + 1] = y / length
            buffer[index * 3 + 2] = z / length
        }
    }
}
