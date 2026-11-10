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

import org.meshtastic.feature.map.terrain.spatial.SpatialBezelMesh
import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import org.meshtastic.feature.map.terrain.spatial.SpatialWheelLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A vertex-colored triangle mesh: position and normal xyz, color rgba, three indices per triangle. */
internal class TerrainMeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val colors: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int
        get() = positions.size / 3

    /** Interleaved position and normal floats, then color as four normalized bytes: [BYTES_PER_VERTEX] bytes each. */
    fun vertexBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(vertexCount * BYTES_PER_VERTEX).order(ByteOrder.nativeOrder())
        for (i in 0 until vertexCount) {
            for (k in 0 until 3) buffer.putFloat(positions[i * 3 + k])
            for (k in 0 until 3) buffer.putFloat(normals[i * 3 + k])
            for (k in 0 until 4) buffer.put((colors[i * 4 + k].coerceIn(0f, 1f) * MAX_BYTE).roundToInt().toByte())
        }
        return buffer.also { it.flip() }
    }

    fun indexBuffer(): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(indices.size * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
        indices.forEach { buffer.putInt(it) }
        return buffer.also { it.flip() }
    }

    companion object {
        const val BYTES_PER_VERTEX = 28
        private const val MAX_BYTE = 255f
    }
}

/** Collects small solid pieces (boxes, spheres, flat strokes) into one [TerrainMeshData]. */
internal class MeshAccumulator {
    private val positions = ArrayList<Float>()
    private val normals = ArrayList<Float>()
    private val colors = ArrayList<Float>()
    private val indices = ArrayList<Int>()

    private val count: Int
        get() = positions.size / 3

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, color: Rgb) {
        positions.addAll(listOf(x, y, z))
        normals.addAll(listOf(nx, ny, nz))
        colors.addAll(listOf(color.r, color.g, color.b, 1f))
    }

    /** A quad from four corners counter-clockwise seen from the side [nx], [ny], [nz] points to. */
    fun quad(c: List<FloatArray>, nx: Float, ny: Float, nz: Float, color: Rgb) {
        val base = count
        c.forEach { vertex(it[0], it[1], it[2], nx, ny, nz, color) }
        indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
    }

    fun box(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, color: Rgb) {
        fun p(sx: Int, sy: Int, sz: Int) = floatArrayOf(cx + sx * hx, cy + sy * hy, cz + sz * hz)
        quad(listOf(p(-1, 1, 1), p(1, 1, 1), p(1, 1, -1), p(-1, 1, -1)), 0f, 1f, 0f, color)
        quad(listOf(p(-1, -1, -1), p(1, -1, -1), p(1, -1, 1), p(-1, -1, 1)), 0f, -1f, 0f, color)
        quad(listOf(p(-1, -1, 1), p(1, -1, 1), p(1, 1, 1), p(-1, 1, 1)), 0f, 0f, 1f, color)
        quad(listOf(p(1, -1, -1), p(-1, -1, -1), p(-1, 1, -1), p(1, 1, -1)), 0f, 0f, -1f, color)
        quad(listOf(p(1, -1, 1), p(1, -1, -1), p(1, 1, -1), p(1, 1, 1)), 1f, 0f, 0f, color)
        quad(listOf(p(-1, -1, -1), p(-1, -1, 1), p(-1, 1, 1), p(-1, 1, -1)), -1f, 0f, 0f, color)
    }

    fun sphere(cx: Float, cy: Float, cz: Float, radius: Float, color: Rgb, rings: Int = 6, segments: Int = 10) {
        val base = count
        for (r in 0..rings) {
            val phi = PI.toFloat() * r / rings
            for (s in 0..segments) {
                val theta = 2 * PI.toFloat() * s / segments
                val nx = sin(phi) * cos(theta)
                val ny = cos(phi)
                val nz = sin(phi) * sin(theta)
                vertex(cx + nx * radius, cy + ny * radius, cz + nz * radius, nx, ny, nz, color)
            }
        }
        for (r in 0 until rings) {
            for (s in 0 until segments) {
                val a = base + r * (segments + 1) + s
                val b = a + segments + 1
                indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
            }
        }
    }

    /** Copies a bezel mesh in with one color. */
    fun append(bezel: SpatialBezelMesh, color: Rgb) {
        val base = count
        for (i in 0 until bezel.positions.size / 3) {
            vertex(
                bezel.positions[i * 3],
                bezel.positions[i * 3 + 1],
                bezel.positions[i * 3 + 2],
                bezel.normals[i * 3],
                bezel.normals[i * 3 + 1],
                bezel.normals[i * 3 + 2],
                color,
            )
        }
        bezel.indices.forEach { indices.add(base + it) }
    }

    /** A compass letter drawn flat at ([cx], [cz]) on a surface at [y], its up toward ([ux], [uz]). */
    fun glyph(letter: Char, cx: Float, y: Float, cz: Float, ux: Float, uz: Float, size: Float, color: Rgb) {
        val strokes = GLYPHS[letter] ?: return
        val rx = -uz
        val rz = ux
        val half = size * STROKE
        for (s in strokes) {
            // Glyph space is x across (-0.3..0.3 of size) and y up (-0.5..0.5 of size).
            fun at(gx: Float, gy: Float): Pair<Float, Float> {
                val a = (gx - 0.5f) * size * 0.6f
                val b = (gy - 0.5f) * size
                return (cx + rx * a + ux * b) to (cz + rz * a + uz * b)
            }
            val (x0, z0) = at(s[0], s[1])
            val (x1, z1) = at(s[2], s[3])
            val dx = x1 - x0
            val dz = z1 - z0
            val length = max(sqrt(dx * dx + dz * dz), 1e-6f)
            // Perpendicular, ordered so the quad faces up.
            val px = dz / length * half
            val pz = -dx / length * half
            quad(
                listOf(
                    floatArrayOf(x0 - px, y, z0 - pz),
                    floatArrayOf(x1 - px, y, z1 - pz),
                    floatArrayOf(x1 + px, y, z1 + pz),
                    floatArrayOf(x0 + px, y, z0 + pz),
                ),
                0f,
                1f,
                0f,
                color,
            )
        }
    }

    fun build() = TerrainMeshData(
        positions.toFloatArray(),
        normals.toFloatArray(),
        colors.toFloatArray(),
        indices.toIntArray(),
    )

    private companion object {
        const val STROKE = 0.07f
        val GLYPHS: Map<Char, List<FloatArray>> =
            mapOf(
                'N' to listOf(f(0, 0, 0, 1), f(0, 1, 1, 0), f(1, 0, 1, 1)),
                'E' to listOf(f(0, 0, 0, 1), f(0, 1, 1, 1), f(0, 0.5f, 0.8f, 0.5f), f(0, 0, 1, 0)),
                'S' to listOf(f(1, 1, 0, 1), f(0, 1, 0, 0.5f), f(0, 0.5f, 1, 0.5f), f(1, 0.5f, 1, 0), f(1, 0, 0, 0)),
                'W' to listOf(f(0, 1, 0.25f, 0), f(0.25f, 0, 0.5f, 0.6f), f(0.5f, 0.6f, 0.75f, 0), f(0.75f, 0, 1, 1)),
            )

        fun f(a: Number, b: Number, c: Number, d: Number) =
            floatArrayOf(a.toFloat(), b.toFloat(), c.toFloat(), d.toFloat())
    }
}

internal data class Rgb(val r: Float, val g: Float, val b: Float) {
    fun scaled(k: Float) = Rgb(min(r * k, 1f), min(g * k, 1f), min(b * k, 1f))

    fun mix(other: Rgb, t: Float) = Rgb(r + (other.r - r) * t, g + (other.g - g) * t, b + (other.b - b) * t)
}

/** The terrain surface and skirt as colored vertices, the light and the elevation ramp baked in. */
internal fun terrainMeshData(geometry: SpatialTerrainGeometry, lowest: Double, highest: Double): TerrainMeshData {
    val count = geometry.positions.size / 3
    val surface = geometry.columns * geometry.rows
    val colors = FloatArray(count * 4)
    val span = max(highest - lowest, 1.0)
    for (i in 0 until count) {
        val color =
            if (i >= surface) {
                SKIRT
            } else {
                val altitude = geometry.transform.altitude(geometry.positions[i * 3 + 1])
                val ground = if (altitude <= WATER_LEVEL_METERS) WATER else ramp(((altitude - lowest) / span).toFloat())
                val nx = geometry.normals[i * 3]
                val ny = geometry.normals[i * 3 + 1]
                val nz = geometry.normals[i * 3 + 2]
                ground.scaled(AMBIENT + (1 - AMBIENT) * max(0f, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]))
            }
        colors[i * 4] = color.r
        colors[i * 4 + 1] = color.g
        colors[i * 4 + 2] = color.b
        colors[i * 4 + 3] = 1f
    }
    return TerrainMeshData(geometry.positions, geometry.normals, colors, geometry.indices)
}

/** The bezel ring, its compass letters and the eight handles, all in model units. */
internal fun bezelMeshData(diskRadius: Float, rimTop: Float): TerrainMeshData {
    val acc = MeshAccumulator()
    val top = min(rimTop + SpatialWheelLayout.BEZEL_LIFT, SpatialWheelLayout.MAXIMUM_TOP).coerceAtLeast(MIN_BEZEL_TOP)
    acc.append(
        SpatialBezelMesh(
            innerRadius = diskRadius + SpatialWheelLayout.GAP,
            width = SpatialWheelLayout.BEZEL_WIDTH,
            bottom = -TERRAIN_SKIRT,
            top = top,
            chamfer = SpatialWheelLayout.CHAMFER,
        ),
        BEZEL,
    )
    val letterRadius = diskRadius + SpatialWheelLayout.GAP + SpatialWheelLayout.BEZEL_WIDTH / 2
    for (point in SpatialWheelLayout.compassPoints) {
        val p = SpatialWheelLayout.point(letterRadius, point.angle)
        val len = max(sqrt(p.x * p.x + p.y * p.y), 1e-6f)
        acc.glyph(
            point.letter[0],
            p.x,
            top + LETTER_LIFT,
            p.y,
            p.x / len,
            p.y / len,
            SpatialWheelLayout.BEZEL_WIDTH * LETTER_SCALE,
            if (point.letter == "N") NORTH else LETTER,
        )
    }
    val knobDistance = SpatialWheelLayout.knobDistance(diskRadius)
    val spokeStart = diskRadius + SpatialWheelLayout.GAP + SpatialWheelLayout.BEZEL_WIDTH
    for (angle in SpatialWheelLayout.handleAngles) {
        val knob = SpatialWheelLayout.point(knobDistance, angle)
        // A row of small spheres from the bezel to the knob reads as a rod.
        for (step in 0..SPOKE_STEPS) {
            val along = spokeStart + (knobDistance - spokeStart) * step / SPOKE_STEPS
            val at = SpatialWheelLayout.point(along, angle)
            acc.sphere(at.x, top / 2, at.y, SpatialWheelLayout.SPOKE_RADIUS, BEZEL, rings = 4, segments = 6)
        }
        acc.sphere(knob.x, top / 2, knob.y, SpatialWheelLayout.KNOB_RADIUS, KNOB)
    }
    return acc.build()
}

private fun ramp(t: Float): Rgb = when {
    t < 0.35f -> LOWLAND.mix(MIDLAND, t / 0.35f)
    t < 0.75f -> MIDLAND.mix(HIGHLAND, (t - 0.35f) / 0.4f)
    else -> HIGHLAND.mix(PEAK, (t - 0.75f) / 0.25f)
}

private const val WATER_LEVEL_METERS = 0.5
private const val AMBIENT = 0.45f
private const val MIN_BEZEL_TOP = 0.012f
private const val TERRAIN_SKIRT = 0.02f
private const val SPOKE_STEPS = 6
private const val LETTER_LIFT = 0.0006f
private const val LETTER_SCALE = 0.7f

/** From the north-west and above, the way a printed relief map is lit, normalized. */
private val LIGHT = floatArrayOf(-0.5f, 0.75f, -0.43f)
private val WATER = Rgb(0.2f, 0.42f, 0.62f)
private val LOWLAND = Rgb(0.36f, 0.56f, 0.34f)
private val MIDLAND = Rgb(0.62f, 0.55f, 0.36f)
private val HIGHLAND = Rgb(0.55f, 0.47f, 0.42f)
private val PEAK = Rgb(0.95f, 0.95f, 0.97f)
private val SKIRT = Rgb(0.28f, 0.22f, 0.17f)
private val BEZEL = Rgb(0.16f, 0.17f, 0.2f)
private val KNOB = Rgb(0.7f, 0.72f, 0.76f)
private val LETTER = Rgb(0.92f, 0.92f, 0.94f)
private val NORTH = Rgb(0.95f, 0.45f, 0.2f)
