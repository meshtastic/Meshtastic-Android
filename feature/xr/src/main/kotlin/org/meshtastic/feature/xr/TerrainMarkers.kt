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
@file:Suppress("MagicNumber", "TooManyFunctions")

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
 * A trace route to draw: the nodes a request passed on its way out, the radio first, and the ones the answer passed on
 * its way back.
 */
data class TerrainRoute(val forward: List<Long>, val back: List<Long>)

/** The way a comet flies in from each node the radio hears directly, in the link's color, by node. */
internal fun linkPaths(markers: List<NodeMarker>): Map<Long, CometPath> {
    val radio = markers.firstOrNull { it.node.isConnectedRadio } ?: return emptyMap()
    return markers
        .filter { it.node.isDirect }
        .associate { it.node.id to CometPath(listOf(it.top to radio.top), signalColor(it.node.signal)) }
}

/**
 * The way out and the way back of [route], in the 2D map's colors for them. A hop with no marker is passed over, so the
 * line carries on to the next one that has. Each way keeps to the right of its own direction, so the two run side by
 * side instead of one hiding the other.
 */
internal fun routePaths(markers: List<NodeMarker>, route: TerrainRoute?): List<CometPath> {
    route ?: return emptyList()
    val tops = markers.associate { it.node.id to it.top }
    fun legs(hops: List<Long>) = hops
        .mapNotNull { tops[it] }
        .zipWithNext()
        .filter { (a, b) -> a != b }
        .map { (a, b) ->
            val dx = b.x - a.x
            val dz = b.z - a.z
            val length = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6f)
            val sx = -dz / length * ROUTE_SHIFT
            val sz = dx / length * ROUTE_SHIFT
            Vec3(a.x + sx, a.y, a.z + sz) to Vec3(b.x + sx, b.y, b.z + sz)
        }
    return listOf(CometPath(legs(route.forward), ROUTE_OUT), CometPath(legs(route.back), ROUTE_BACK)).filter {
        it.legs.isNotEmpty()
    }
}

/**
 * A pin for each node in its own color, a tower for a router, repeater or client base, an arc from the connected radio
 * to each node it hears directly, colored by how well, and [route] over them, its way out drawn the heavier. Null with
 * no nodes in view.
 */
internal fun markerMeshData(markers: List<NodeMarker>, route: TerrainRoute? = null): TerrainMeshData? {
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
            if (marker.node.isDirect) acc.arc(radio.top, marker.top, ARC_HALF_WIDTH, signalColor(marker.node.signal))
        }
    }
    for ((index, path) in routePaths(markers, route).withIndex()) {
        val half = if (index == 0) ROUTE_OUT_HALF_WIDTH else ROUTE_BACK_HALF_WIDTH
        path.legs.forEach { (from, to) -> acc.arc(from, to, half, path.color) }
    }
    return acc.build().takeIf { it.vertexCount > 0 }
}

/** A point [t] of the way along the arc from [from] to [to], which rises in the middle to clear the ground. */
internal fun arcPoint(from: Vec3, to: Vec3, t: Float): Vec3 {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dz = to.z - from.z
    val lift = min(ARC_MAX_LIFT, ARC_LIFT_SHARE * sqrt(dx * dx + dz * dz))
    return Vec3(from.x + dx * t, from.y + dy * t + lift * sin(PI.toFloat() * t), from.z + dz * t)
}

/** A thin four-sided rod along the arc from [from] to [to]. */
private fun MeshAccumulator.arc(from: Vec3, to: Vec3, half: Float, color: Rgb) {
    var last = from
    for (step in 1..ARC_STEPS) {
        val next = arcPoint(from, to, step.toFloat() / ARC_STEPS)
        rod(last, next, half, color)
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

/** The app's signal colors, as the node list's signal bars show them; gray when the radio gave no reading. */
internal fun signalColor(signal: SpatialSignal?): Rgb = when (signal) {
    SpatialSignal.GOOD -> rgb(0x3FB86D)
    SpatialSignal.FAIR -> rgb(0xE8A33E)
    SpatialSignal.BAD -> rgb(0xE07000)
    SpatialSignal.NONE -> rgb(0xE05252)
    null -> Rgb(0.5f, 0.52f, 0.56f)
}

private fun rgb(hex: Int) = Rgb((hex shr 16 and 0xFF) / 255f, (hex shr 8 and 0xFF) / 255f, (hex and 0xFF) / 255f)

private fun Int.toRgb() = Rgb(Color.red(this) / 255f, Color.green(this) / 255f, Color.blue(this) / 255f)

/** A node chip: the node's short name and color, and where the middle of the chip stands above its pin. */
internal class TerrainChip(val name: String, val color: Rgb, val at: Vec3)

/**
 * The 2D map's node chip for each marker that has a short name, the connected radio, favorites and towers first, at
 * most [MAX_CHIPS]. Each stands upright just above its pin. A chip is an entity of its own, so it can be turned to face
 * the front whichever way the wheel is.
 */
internal fun terrainChips(markers: List<NodeMarker>): List<TerrainChip> = markers
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
    .take(MAX_CHIPS)
    .map {
        TerrainChip(it.node.shortName, it.color, Vec3(it.top.x, it.top.y + CHIP_LIFT + CHIP_HALF_HEIGHT, it.top.z))
    }

/** One upright quad about its own middle, facing +z, with the whole of an image on it: every chip is this shape. */
internal fun chipMeshData(): TerrainMeshData {
    val w = CHIP_HALF_WIDTH
    val h = CHIP_HALF_HEIGHT
    return TerrainMeshData(
        positions = floatArrayOf(-w, -h, 0f, w, -h, 0f, w, h, 0f, -w, h, 0f),
        normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        colors = FloatArray(16) { 1f },
        indices = intArrayOf(0, 1, 2, 0, 2, 3),
        // An image's first row is the top.
        uvs = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
    )
}

/** A chip's image: its name on a plate. */
internal object ChipImage {
    const val WIDTH = 320
    const val HEIGHT = 112

    /**
     * The name in the middle as PNG, drawn as the 2D map draws a node chip: a rounded plate in the node's color, as
     * wide as its name, with the name in black or white, whichever reads on that color. Clear around it.
     */
    fun draw(name: String, color: Rgb): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val plate = Paint(Paint.ANTI_ALIAS_FLAG)
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = HEIGHT * TEXT_SHARE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
        val middle = WIDTH / 2f
        val half = (text.measureText(name) / 2 + PADDING).coerceIn(MIN_HALF_WIDTH, WIDTH / 2f)
        plate.color = Color.rgb((color.r * 255).toInt(), (color.g * 255).toInt(), (color.b * 255).toInt())
        canvas.drawRoundRect(middle - half, 0f, middle + half, HEIGHT.toFloat(), CORNER, CORNER, plate)
        text.color = if (isLight(color)) Color.BLACK else Color.WHITE
        val baseline = HEIGHT / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(name, middle, baseline, text)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** The rule the app's node colors use to pick black text over white. */
    fun isLight(color: Rgb): Boolean = 0.299f * color.r + 0.587f * color.g + 0.114f * color.b > 0.5f

    // The 2D chip is 28 dp high, with 8 dp corners and padding, and at least 64 dp wide: four pixels to the dp here.
    private const val CORNER = 32f
    private const val PADDING = 32f
    private const val MIN_HALF_WIDTH = 128f
    private const val TEXT_SHARE = 0.55f
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
private const val ROUTE_OUT_HALF_WIDTH = 0.0016f
private const val ROUTE_BACK_HALF_WIDTH = 0.0013f
private const val ROUTE_SHIFT = 0.003f
private const val CHIP_HALF_WIDTH = 0.017f
private const val CHIP_HALF_HEIGHT = CHIP_HALF_WIDTH * ChipImage.HEIGHT / ChipImage.WIDTH

/** The most chips on the table: each is an entity and an image of its own. */
private const val MAX_CHIPS = 18

/** Above the ball at the top of the pin, the connected radio's larger one included. */
private const val CHIP_LIFT = 0.011f

/** The 2D map's trace route colors: orange out, blue back. */
private val ROUTE_OUT = rgb(0xE86A00)
private val ROUTE_BACK = rgb(0x0081C7)
private val PIN = Rgb(0.4f, 0.92f, 0.56f)
private val STALK = Rgb(0.92f, 0.92f, 0.94f)
private val RADIO = Rgb(0.95f, 0.45f, 0.2f)
private val TOWER = Rgb(0.78f, 0.8f, 0.84f)
private val BEACON = Rgb(0.95f, 0.2f, 0.2f)
