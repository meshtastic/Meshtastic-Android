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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import org.meshtastic.feature.map.terrain.spatial.SpatialCircle
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import org.meshtastic.feature.map.terrain.spatial.SpatialSignal
import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import org.meshtastic.feature.map.terrain.spatial.Vec3
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Where a node stands on the table: its foot on the ground and the top of its pin or tower. */
internal class NodeMarker(val node: SpatialNodeSnapshot, val foot: Vec3) {
    val height: Float
        get() = if (node.isInfrastructure) TOWER_HEIGHT else PIN_HEIGHT

    val top: Vec3
        get() = Vec3(foot.x, foot.y + height, foot.z)

    val color: Rgb
        get() {
            val own = if (node.color == 0) PIN else node.color.toRgb()
            return if (node.isOnline) own else own.scaled(OFFLINE_DIM)
        }
}

/** The nodes inside [circle], standing on [geometry]'s ground. */
internal fun nodeMarkers(
    geometry: SpatialTerrainGeometry,
    nodes: List<SpatialNodeSnapshot>,
    circle: SpatialCircle,
): List<NodeMarker> =
    nodes.filter { circle.contains(it.coordinate) }.map { NodeMarker(it, geometry.surfacePosition(it.coordinate)) }

/**
 * A pin for each node in its own color, a tower for a router, repeater or client base, and an arc from the connected
 * radio to each node it hears directly, colored by how well. Null with no nodes in view.
 */
internal fun markerMeshData(markers: List<NodeMarker>): TerrainMeshData? {
    val acc = MeshAccumulator()
    for (marker in markers) {
        val foot = marker.foot
        val node = marker.node
        if (node.isInfrastructure) {
            val split = marker.height * TOWER_BASE_SHARE
            acc.box(foot.x, foot.y + split / 2, foot.z, TOWER_BASE, split / 2, TOWER_BASE, TOWER)
            val mast = marker.height - split
            acc.box(foot.x, foot.y + split + mast / 2, foot.z, PIN_STALK, mast / 2, PIN_STALK, TOWER)
            acc.sphere(foot.x, foot.y + split, foot.z, PIN_RADIUS * TOWER_BALL, marker.color)
            acc.sphere(foot.x, marker.top.y, foot.z, BEACON_RADIUS, BEACON)
        } else {
            acc.box(foot.x, foot.y + marker.height / 2, foot.z, PIN_STALK, marker.height / 2, PIN_STALK, STALK)
            val radius = if (node.isConnectedRadio) PIN_RADIUS * RADIO_BALL else PIN_RADIUS
            acc.sphere(foot.x, marker.top.y, foot.z, radius, if (node.isConnectedRadio) RADIO else marker.color)
        }
    }
    val radio = markers.firstOrNull { it.node.isConnectedRadio }
    if (radio != null) {
        for (marker in markers) {
            if (marker.node.isDirect) acc.arc(radio.top, marker.top, signalColor(marker.node.signal))
        }
    }
    return acc.build().takeIf { it.vertexCount > 0 }
}

/** A thin four-sided rod from [from] to [to] that rises in the middle, so it clears the ground between them. */
private fun MeshAccumulator.arc(from: Vec3, to: Vec3, color: Rgb) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    val lift = min(ARC_MAX_LIFT, ARC_LIFT_SHARE * sqrt(dx * dx + dz * dz))
    var last = from
    for (step in 1..ARC_STEPS) {
        val t = step.toFloat() / ARC_STEPS
        val next = Vec3(from.x + dx * t, from.y + dy * t + lift * sin(PI.toFloat() * t), from.z + dz * t)
        rod(last, next, ARC_HALF_WIDTH, color)
        last = next
    }
}

/** A square rod between two points, each side drawn from both faces so it shows whichever way it is wound. */
internal fun MeshAccumulator.rod(a: Vec3, b: Vec3, half: Float, color: Rgb) {
    val d = floatArrayOf(b.x - a.x, b.y - a.y, b.z - a.z)
    val length = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
    if (length < 1e-7f) return
    for (k in 0..2) d[k] /= length
    // Any axis not along the rod gives a perpendicular.
    val axis = if (kotlin.math.abs(d[1]) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
    val u = cross(d, axis).normalized()
    val v = cross(u, d)
    val corners =
        listOf(1f to 1f, -1f to 1f, -1f to -1f, 1f to -1f).map { (s, t) ->
            FloatArray(3) { (u[it] * s + v[it] * t) * half }
        }
    for (k in 0..3) {
        val c0 = corners[k]
        val c1 = corners[(k + 1) % 4]
        val normal = FloatArray(3) { c0[it] + c1[it] }.normalized()
        val quad =
            listOf(
                floatArrayOf(a.x + c0[0], a.y + c0[1], a.z + c0[2]),
                floatArrayOf(a.x + c1[0], a.y + c1[1], a.z + c1[2]),
                floatArrayOf(b.x + c1[0], b.y + c1[1], b.z + c1[2]),
                floatArrayOf(b.x + c0[0], b.y + c0[1], b.z + c0[2]),
            )
        quad(quad, normal[0], normal[1], normal[2], color)
        quad(quad.reversed(), normal[0], normal[1], normal[2], color)
    }
}

private fun cross(a: FloatArray, b: FloatArray) =
    floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

private fun FloatArray.normalized(): FloatArray {
    val length = sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2]).coerceAtLeast(1e-9f)
    return FloatArray(3) { this[it] / length }
}

private fun signalColor(signal: SpatialSignal?): Rgb = when (signal) {
    SpatialSignal.GOOD -> Rgb(0.3f, 0.85f, 0.4f)
    SpatialSignal.FAIR -> Rgb(0.95f, 0.85f, 0.25f)
    SpatialSignal.BAD -> Rgb(0.98f, 0.55f, 0.2f)
    SpatialSignal.NONE -> Rgb(0.9f, 0.25f, 0.25f)
    null -> Rgb(0.7f, 0.72f, 0.76f)
}

private fun Int.toRgb() = Rgb(Color.red(this) / 255f, Color.green(this) / 255f, Color.blue(this) / 255f)

/** Name plates: a mesh of flat quads, one per label, and where each one's text is in the atlas. */
internal class TerrainLabels(val mesh: TerrainMeshData, val names: List<Pair<String, Rgb>>)

/**
 * A name plate lying flat beside the top of each marker that has a short name, the connected radio, favorites and
 * towers first, at most [LabelAtlas.CAPACITY]. They are textured quads in one model rather than a panel each: a scene
 * of dozens of panels is costly, and one image is not.
 */
internal fun terrainLabels(markers: List<NodeMarker>): TerrainLabels? {
    val chosen =
        markers
            .filter { it.node.shortName.isNotBlank() }
            .sortedBy {
                when {
                    it.node.isConnectedRadio -> 0
                    it.node.isFavorite -> 1
                    it.node.isInfrastructure -> 2
                    it.node.isOnline -> 3
                    else -> 4
                }
            }
            .take(LabelAtlas.CAPACITY)
    if (chosen.isEmpty()) return null
    val positions = ArrayList<Float>()
    val uvs = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for ((index, marker) in chosen.withIndex()) {
        val cell = LabelAtlas.cell(index)
        val x = marker.top.x
        val y = marker.top.y
        val z = marker.top.z + LABEL_OFFSET
        // South-west, south-east, north-east, north-west: counter-clockwise seen from above.
        val corners =
            listOf(
                Triple(x - LABEL_HALF_WIDTH, z + LABEL_HALF_HEIGHT, cell.left to cell.bottom),
                Triple(x + LABEL_HALF_WIDTH, z + LABEL_HALF_HEIGHT, cell.right to cell.bottom),
                Triple(x + LABEL_HALF_WIDTH, z - LABEL_HALF_HEIGHT, cell.right to cell.top),
                Triple(x - LABEL_HALF_WIDTH, z - LABEL_HALF_HEIGHT, cell.left to cell.top),
            )
        val base = positions.size / 3
        for ((px, pz, uv) in corners) {
            positions.addAll(listOf(px, y, pz))
            uvs.addAll(listOf(uv.first, uv.second))
        }
        indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
    }
    val count = positions.size / 3
    val mesh =
        TerrainMeshData(
            positions = positions.toFloatArray(),
            normals = FloatArray(count * 3) { if (it % 3 == 1) 1f else 0f },
            colors = FloatArray(count * 4) { 1f },
            indices = indices.toIntArray(),
            uvs = uvs.toFloatArray(),
        )
    return TerrainLabels(mesh, chosen.map { it.node.shortName to it.color })
}

/** The grid the label texts are drawn into. */
internal object LabelAtlas {
    const val WIDTH = 1024
    const val HEIGHT = 512
    const val CELL_WIDTH = 160
    const val CELL_HEIGHT = 56
    private const val COLUMNS = WIDTH / CELL_WIDTH
    const val CAPACITY = COLUMNS * (HEIGHT / CELL_HEIGHT)

    /** A cell's edges as texture coordinates, v down from the image's first row. */
    class Cell(val left: Float, val top: Float, val right: Float, val bottom: Float)

    fun cell(index: Int): Cell {
        val x = index % COLUMNS * CELL_WIDTH
        val y = index / COLUMNS * CELL_HEIGHT
        return Cell(
            x.toFloat() / WIDTH,
            y.toFloat() / HEIGHT,
            (x + CELL_WIDTH).toFloat() / WIDTH,
            (y + CELL_HEIGHT).toFloat() / HEIGHT,
        )
    }

    /** Each name in its cell as PNG: light text on a dark plate, with a stripe in the node's color. */
    fun draw(names: List<Pair<String, Rgb>>): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val plate = Paint().apply { color = Color.rgb(20, 22, 28) }
        val stripe = Paint()
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = CELL_HEIGHT * TEXT_SHARE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
        for ((index, name) in names.withIndex()) {
            val x = (index % COLUMNS * CELL_WIDTH).toFloat()
            val y = (index / COLUMNS * CELL_HEIGHT).toFloat()
            canvas.drawRect(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, plate)
            val (r, g, b) = name.second
            stripe.color = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
            canvas.drawRect(x, y, x + STRIPE, y + CELL_HEIGHT, stripe)
            val baseline = y + CELL_HEIGHT / 2f - (text.descent() + text.ascent()) / 2f
            canvas.drawText(name.first, x + STRIPE + (CELL_WIDTH - STRIPE) / 2f, baseline, text)
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    private const val STRIPE = 12f
    private const val TEXT_SHARE = 0.62f
}

private const val PIN_HEIGHT = 0.03f
private const val PIN_STALK = 0.0012f
private const val PIN_RADIUS = 0.007f
private const val RADIO_BALL = 1.35f
private const val TOWER_HEIGHT = 0.05f
private const val TOWER_BASE = 0.0026f
private const val TOWER_BASE_SHARE = 0.6f
private const val TOWER_BALL = 0.8f
private const val BEACON_RADIUS = 0.0035f
private const val OFFLINE_DIM = 0.45f
private const val ARC_STEPS = 14
private const val ARC_MAX_LIFT = 0.06f
private const val ARC_LIFT_SHARE = 0.18f
private const val ARC_HALF_WIDTH = 0.0009f
private const val LABEL_HALF_WIDTH = 0.017f
private const val LABEL_HALF_HEIGHT = LABEL_HALF_WIDTH * LabelAtlas.CELL_HEIGHT / LabelAtlas.CELL_WIDTH
private const val LABEL_OFFSET = 0.016f

private val PIN = Rgb(0.4f, 0.92f, 0.56f)
private val STALK = Rgb(0.92f, 0.92f, 0.94f)
private val RADIO = Rgb(0.95f, 0.45f, 0.2f)
private val TOWER = Rgb(0.78f, 0.8f, 0.84f)
private val BEACON = Rgb(0.95f, 0.2f, 0.2f)
