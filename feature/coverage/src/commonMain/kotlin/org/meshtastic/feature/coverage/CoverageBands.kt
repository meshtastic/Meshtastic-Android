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
package org.meshtastic.feature.coverage

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** A closed ring of `lon to lat` positions, first and last equal. */
internal typealias CoverageRing = List<Pair<Double, Double>>

/** An outer ring followed by its holes. */
internal typealias CoveragePolygon = List<CoverageRing>

/**
 * The bands between successive ascending [floors]; the last band is open-ended.
 *
 * Each band is traced with marching squares at its floor, interpolated between samples, then lightly smoothed and
 * simplified. A band is its floor's outlines with the next floor's outlines reversed as holes, so the bands tile the
 * disc without overlapping. Outer rings are counterclockwise and holes clockwise, as GeoJSON wants.
 */
internal fun CoverageGrid.bandPolygons(floors: List<Double>): List<List<CoveragePolygon>> {
    val tracer = BandTracer(this)
    val smoother = OutlineSmoother(tracer.keepOnDiscEdge)
    val traced = floors.map(tracer::trace)
    // Pin every point where an outline leaves the disc edge, at every level, so outlines that share a
    // stretch of the edge keep the same points along it.
    traced.flatten().forEach(smoother::pinEndsOfDiscStretches)
    val outlines = traced.map { level -> level.mapNotNull(smoother::smooth) }
    return floors.indices.map { index ->
        val above = outlines.getOrNull(index + 1).orEmpty().map { it.reversed() }
        assemble(outlines[index] + above).map { polygon -> polygon.map(tracer::coordinates) }
    }
}

/** A point in grid units: x is the column and y is minus the row, so north is up and counterclockwise is positive. */
internal data class GridPoint(val x: Double, val y: Double) {
    fun distanceSquared(other: GridPoint): Double = (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)
}

/**
 * One closed outline, without the repeated closing point. Points on the disc edge are marked so every level keeps them
 * identical and the bands meet there exactly. [edges] is the grid edge each traced point crosses; empty once smoothed.
 */
private class Outline(val points: List<GridPoint>, val onDiscEdge: List<Boolean>, val edges: List<Int> = emptyList()) {
    /** Shoelace area; positive when counterclockwise. */
    val signedArea: Double by lazy {
        var sum = 0.0
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        sum / 2
    }

    /** Every point on the disc edge: the ring is the whole disc. */
    val isWholeDisc: Boolean
        get() = onDiscEdge.all { it }

    fun reversed(): Outline = Outline(points.reversed(), onDiscEdge.reversed(), edges.reversed())

    fun contains(point: GridPoint): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) {
                inside = !inside
            }
            j = i
        }
        return inside
    }
}

/**
 * Traces outlines on [grid] padded with one row and column of empty samples on every side, so every outline closes.
 *
 * Edge `id` runs from node `id / 2` right (even) or down (odd); samples sit on nodes.
 */
private class BandTracer(private val grid: CoverageGrid) {
    private val paddedWidth = grid.width + 2
    private val paddedHeight = grid.height + 2

    /** Where the disc edge crosses each edge between a sample and an empty one, as a fraction from its first node. */
    private val discFraction = DoubleArray(paddedWidth * paddedHeight * 2) { Double.NaN }

    /** Which disc edge points survive thinning the edge to about one per [DISC_EDGE_STEP_DEGREES] of bearing. */
    val keepOnDiscEdge = BooleanArray(paddedWidth * paddedHeight * 2)

    init {
        val firstInBin = mutableMapOf<Int, Pair<Double, Int>>()
        val centerX = (grid.width - 1) / 2.0
        val centerY = -(grid.height - 1) / 2.0
        for (node in 0 until paddedWidth * paddedHeight) {
            for (vertical in listOf(false, true)) {
                val edge = node * 2 + if (vertical) 1 else 0
                val fraction = discCrossing(node, vertical) ?: continue
                discFraction[edge] = fraction
                val point = point(edge, fraction)
                var angle = atan2(point.y - centerY, point.x - centerX) * DEGREES_PER_RADIAN
                if (angle < 0) angle += FULL_CIRCLE
                val bin = (angle / DISC_EDGE_STEP_DEGREES).toInt()
                val current = firstInBin[bin]
                if (current == null || angle < current.first) firstInBin[bin] = angle to edge
            }
        }
        for ((_, entry) in firstInBin) keepOnDiscEdge[entry.second] = true
    }

    /**
     * Where the disc's rim crosses the edge leaving [node], interpolated by distance from the site so the rim is round.
     * Null unless exactly one end is empty and the rim falls on the edge: an empty sample inside the disc is not its
     * rim.
     */
    private fun discCrossing(node: Int, vertical: Boolean): Double? {
        val column = node % paddedWidth
        val row = node / paddedWidth
        val inBounds = if (vertical) row + 1 < paddedHeight else column + 1 < paddedWidth
        val other = if (vertical) node + paddedWidth else node + 1
        if (!inBounds || value(node).isNaN() == value(other).isNaN()) return null
        val da = distanceKm(node)
        val db = distanceKm(other)
        val fraction = (grid.site.radiusKm - da) / (db - da)
        return fraction.takeIf { da != db && it in 0.0..1.0 }
    }

    private fun distanceKm(node: Int): Double = haversineKm(
        grid.site.latitude,
        grid.site.longitude,
        grid.latAt((node / paddedWidth - 1).toDouble()),
        grid.lonAt((node % paddedWidth - 1).toDouble()),
    )

    private fun value(node: Int): Double {
        val x = node % paddedWidth - 1
        val y = node / paddedWidth - 1
        return if (x in 0 until grid.width && y in 0 until grid.height) grid.at(x, y) else Double.NaN
    }

    private fun point(edge: Int, fraction: Double): GridPoint {
        val node = edge / 2
        val column = (node % paddedWidth - 1).toDouble()
        val row = (node / paddedWidth - 1).toDouble()
        return if (edge % 2 == 0) GridPoint(column + fraction, -row) else GridPoint(column, -(row + fraction))
    }

    /** Outlines of the area at or above [level], outer rings counterclockwise and holes clockwise, without specks. */
    fun trace(level: Double): List<Outline> {
        val nodes = paddedWidth * paddedHeight
        val inside = BooleanArray(nodes) { node -> value(node).let { !it.isNaN() && it >= level } }
        // next[edge] is the edge where the outline leaving `edge` arrives, keeping the inside on its left.
        val next = IntArray(nodes * 2) { -1 }
        for (row in 0 until paddedHeight - 1) {
            for (column in 0 until paddedWidth - 1) linkCell(row * paddedWidth + column, inside, level, next)
        }
        return next.indices.mapNotNull { start -> if (next[start] >= 0) walk(start, next, level) else null }
    }

    private fun linkCell(topLeft: Int, inside: BooleanArray, level: Double, next: IntArray) {
        // Corners and the edges between them, clockwise from the top left: top, right, bottom, left.
        val corners = intArrayOf(topLeft, topLeft + 1, topLeft + paddedWidth + 1, topLeft + paddedWidth)
        val state = BooleanArray(CORNERS) { inside[corners[it]] }
        if (state.all { it == state[0] }) return
        val edges = intArrayOf(topLeft * 2, (topLeft + 1) * 2 + 1, (topLeft + paddedWidth) * 2, topLeft * 2 + 1)
        val crossed = (0 until CORNERS).filter { state[it] != state[(it + 1) % CORNERS] }
        val entering = crossed.filter { state[(it + 1) % CORNERS] }
        if (entering.size == 1) {
            next[edges[entering[0]]] = edges[crossed.first { !state[(it + 1) % CORNERS] }]
        } else {
            // A saddle: the cell's mean decides whether the two inside corners join. Any empty corner keeps them apart.
            val mean = corners.sumOf { value(it) } / CORNERS
            val joined = !mean.isNaN() && mean >= level
            for (side in entering) {
                next[edges[side]] = edges[if (joined) (side + CORNERS - 1) % CORNERS else (side + 1) % CORNERS]
            }
        }
    }

    private fun walk(start: Int, next: IntArray, level: Double): Outline? {
        val points = mutableListOf<GridPoint>()
        val onEdge = mutableListOf<Boolean>()
        val edges = mutableListOf<Int>()
        var edge = start
        while (next[edge] >= 0) {
            points.add(crossing(edge, level))
            onEdge.add(!discFraction[edge].isNaN())
            edges.add(edge)
            val following = next[edge]
            next[edge] = -1
            edge = following
        }
        val outline = Outline(points, onEdge, edges)
        return outline.takeIf { points.size >= MIN_RING_POINTS && abs(it.signedArea) >= MIN_RING_AREA_CELLS }
    }

    private fun crossing(edge: Int, level: Double): GridPoint {
        val disc = discFraction[edge]
        if (!disc.isNaN()) return point(edge, disc)
        val node = edge / 2
        val a = value(node)
        val b = value(if (edge % 2 == 0) node + 1 else node + paddedWidth)
        // Kept off the nodes so neighboring crossings never coincide.
        val flat = a.isNaN() || b.isNaN() || a == b
        val fraction = if (flat) HALF else ((level - a) / (b - a)).coerceIn(NODE_MARGIN, 1 - NODE_MARGIN)
        return point(edge, fraction)
    }

    /** Closed `lon to lat` positions. */
    fun coordinates(outline: Outline): CoverageRing {
        val ring = outline.points.map { grid.lonAt(it.x) to grid.latAt(-it.y) }
        return ring + ring.first()
    }
}

/** Smooths traced outlines, keeping the disc edge points that [keepOnDiscEdge] marks so shared stretches match. */
private class OutlineSmoother(private val keepOnDiscEdge: BooleanArray) {
    /** Keeps the disc edge points where [outline] leaves or joins the edge, whichever level it was traced at. */
    fun pinEndsOfDiscStretches(outline: Outline) {
        val count = outline.points.size
        for (index in 0 until count) {
            val before = outline.onDiscEdge[(index + count - 1) % count]
            val after = outline.onDiscEdge[(index + 1) % count]
            if (outline.onDiscEdge[index] && !(before && after)) keepOnDiscEdge[outline.edges[index]] = true
        }
    }

    /**
     * Smooths and simplifies the stretches between disc edge points, and thins the disc edge to fixed bearings, so an
     * outline shared by two bands comes out the same in both.
     */
    fun smooth(outline: Outline): Outline? {
        val firstDisc = outline.onDiscEdge.indexOf(true)
        val smoothed = if (firstDisc < 0) smoothFree(outline.points) else smoothAlongDisc(outline, firstDisc)
        return smoothed.takeIf { it.points.size >= MIN_RING_POINTS }
    }

    private fun smoothFree(points: List<GridPoint>): Outline {
        val size = hypot(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
        val tolerance = min(SIMPLIFY_TOLERANCE_CELLS, size / SPECK_TOLERANCE_DIVISOR)
        var out = simplifyClosed(chaikin(points, closed = true), tolerance)
        // A speck simplified to a few corners would read as a square again; one pass gives it eight.
        if (out.size < SPECK_MIN_POINTS) out = chaikin(points, closed = true, passes = 1)
        return Outline(out, List(out.size) { false })
    }

    private fun smoothAlongDisc(outline: Outline, firstDisc: Int): Outline {
        val count = outline.points.size
        // Rotate to start on the disc edge, then walk it as stretches between disc points.
        val points = outline.points.rotate(firstDisc)
        val onEdge = outline.onDiscEdge.rotate(firstDisc)
        val edges = outline.edges.rotate(firstDisc)
        val outPoints = mutableListOf<GridPoint>()
        val outFlags = mutableListOf<Boolean>()
        var index = 0
        while (index < count) {
            if (onEdge[index]) {
                if (keepOnDiscEdge[edges[index]]) {
                    outPoints.add(points[index])
                    outFlags.add(true)
                }
                index++
            } else {
                // A stretch of interior points between two disc points, which stay put.
                var end = index
                while (!onEdge[end % count]) end++
                val run = listOf(points[index - 1]) + points.subList(index, end) + points[end % count]
                val smoothed = simplifyOpen(chaikin(run, closed = false), SIMPLIFY_TOLERANCE_CELLS)
                val interior = smoothed.subList(1, smoothed.size - 1)
                outPoints.addAll(interior)
                repeat(interior.size) { outFlags.add(false) }
                index = end
            }
        }
        return Outline(outPoints, outFlags)
    }
}

/** Groups outlines into polygons: each hole goes to the smallest outer ring holding it. */
private fun CoverageGrid.assemble(outlines: List<Outline>): List<List<Outline>> {
    val areas = outlines.map { it.signedArea }
    val outers = outlines.indices.filter { areas[it] > 0 }
    val holes = mutableMapOf<Int, MutableList<Outline>>()
    val cancelled = mutableSetOf<Int>()
    val center = GridPoint((width - 1) / 2.0, -(height - 1) / 2.0)
    for (index in outlines.indices.filter { areas[it] < 0 }) {
        val hole = outlines[index]
        // Test with the hole's point farthest from the disc edge, where the outline holding it can't pass close by.
        val probe =
            hole.points.indices
                .filter { !hole.onDiscEdge[it] }
                .minByOrNull { hole.points[it].distanceSquared(center) }
                ?.let { hole.points[it] }
        if (probe == null) {
            // A hole made only of disc edge points is the whole disc at the level above. The outer made only
            // of disc edge points at this level is the same ring, so the band between them is empty.
            outers.firstOrNull { it !in cancelled && outlines[it].isWholeDisc }?.let { cancelled.add(it) }
        } else {
            outers
                .filter { it !in cancelled && areas[it] > -areas[index] && outlines[it].contains(probe) }
                .minByOrNull { areas[it] }
                ?.let { holes.getOrPut(it) { mutableListOf() }.add(hole) }
        }
    }
    return outers.filter { it !in cancelled }.map { listOf(outlines[it]) + holes[it].orEmpty() }
}

private fun <T> List<T>.rotate(start: Int): List<T> = subList(start, size) + subList(0, start)

/** Douglas-Peucker tolerance, in grid cells. A small closed outline uses a tenth of its size instead. */
private const val SIMPLIFY_TOLERANCE_CELLS = 0.1
private const val SPECK_TOLERANCE_DIVISOR = 10.0
private const val SPECK_MIN_POINTS = 6

/** Outlines enclosing less than this, in square grid cells, are dropped. */
private const val MIN_RING_AREA_CELLS = 0.01
private const val MIN_RING_POINTS = 3

/** The disc edge keeps about one point per this many degrees of bearing. */
private const val DISC_EDGE_STEP_DEGREES = 1.0
private const val CORNERS = 4
private const val HALF = 0.5
private const val NODE_MARGIN = 0.01
private const val FULL_CIRCLE = 360.0
private const val DEGREES_PER_RADIAN = 180.0 / PI
