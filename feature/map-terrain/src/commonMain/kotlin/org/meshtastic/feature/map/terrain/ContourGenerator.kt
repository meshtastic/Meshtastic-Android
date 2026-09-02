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
package org.meshtastic.feature.map.terrain

import kotlin.math.roundToLong

/** A point in a tile's own `[0,1]×[0,1]` unit square — same convention as the base offline layer's tile-local math. */
data class ContourPoint(val x: Float, val y: Float)

/** One contour line at [elevationMeters], already chained into a single connected polyline. */
data class ContourLine(val elevationMeters: Float, val points: List<ContourPoint>)

/**
 * Marching-squares contour generation, ported from the sibling iOS app's `ContourGenerator.swift`.
 *
 * Positive elevations only: Mapterhorn's data carries bathymetry banding, and generating contours below sea level would
 * ring every patch of open water. One pass per grid, not one pass per level: each cell's corner elevations bound which
 * of [levels] can possibly cross it, so a level far outside a cell's own [min,max] is skipped for that cell without a
 * full re-scan.
 */
object ContourGenerator {

    /**
     * Generates every requested contour level from [tile]'s elevation grid, chained into polylines.
     *
     * @param tile an [ElevationTile]. Unlike [Hillshade.shade], this doesn't need cross-tile margin padding — contour
     *   geometry only needs it if lines should visually continue past this tile's own edge, which is the caller's
     *   choice, not a requirement here.
     * @param levels the elevations (meters) to contour at — see [ContourIntervals] for the zoom-banded table this is
     *   normally called with.
     */
    fun generate(tile: ElevationTile, levels: List<Float>): List<ContourLine> {
        val positiveLevels = levels.filter { it > 0f }
        if (positiveLevels.isEmpty()) return emptyList()

        val segmentsByLevel = HashMap<Float, MutableList<Segment>>()
        for (y in 0 until tile.height - 1) {
            for (x in 0 until tile.width - 1) {
                val tl = tile.elevationAt(x, y)
                val tr = tile.elevationAt(x + 1, y)
                val br = tile.elevationAt(x + 1, y + 1)
                val bl = tile.elevationAt(x, y + 1)
                val cellMin = minOf(tl, tr, br, bl)
                val cellMax = maxOf(tl, tr, br, bl)
                if (cellMax <= 0f) continue

                for (level in positiveLevels) {
                    if (level < cellMin || level > cellMax) continue
                    val cellSegments = marchCell(x, y, tl, tr, br, bl, level, tile.width - 1, tile.height - 1)
                    if (cellSegments.isNotEmpty()) segmentsByLevel.getOrPut(level) { mutableListOf() } += cellSegments
                }
            }
        }

        return segmentsByLevel.entries.flatMap { (level, segments) ->
            chain(segments).map { points -> ContourLine(level, points) }
        }
    }

    private data class Segment(val a: ContourPoint, val b: ContourPoint)

    /**
     * One cell's contribution to a single [level]'s contour: zero segments (level doesn't cross this cell), one (the
     * ordinary case), or two (a saddle — the two diagonal corner pairs disagree with each other, so the level crosses
     * all four edges and two separate line pieces pass through the cell).
     *
     * Coordinates are normalized by [gridWidth]/[gridHeight] — the number of *cells*, one less than the elevation
     * grid's own sample count — so the output lands in `[0,1]` regardless of the source grid's resolution.
     */
    private fun marchCell(
        x: Int,
        y: Int,
        tl: Float,
        tr: Float,
        br: Float,
        bl: Float,
        level: Float,
        gridWidth: Int,
        gridHeight: Int,
    ): List<Segment> {
        val top = edgeCrossing(tl, tr, level)
        val right = edgeCrossing(tr, br, level)
        val bottom = edgeCrossing(bl, br, level)
        val left = edgeCrossing(tl, bl, level)

        fun point(cellX: Float, cellY: Float) = ContourPoint((x + cellX) / gridWidth, (y + cellY) / gridHeight)

        val topPoint = top?.let { point(it, 0f) }
        val rightPoint = right?.let { point(1f, it) }
        val bottomPoint = bottom?.let { point(it, 1f) }
        val leftPoint = left?.let { point(0f, it) }
        val crossings = listOfNotNull(topPoint, rightPoint, bottomPoint, leftPoint)

        return when (crossings.size) {
            0 -> emptyList()

            2 -> listOf(Segment(crossings[0], crossings[1])).filterNot { it.a == it.b }

            // Saddle: opposite corner pairs disagree with each other, so all four edges cross and there are two
            // separate segments through the cell. Center-average decider (the standard resolution — a corner-only
            // rule can misjudge some grid geometries): if the 4-corner average sits on the "TL/BR" side of the
            // level, pair (top,left) with (bottom,right), isolating TL and BR as their own corners; otherwise pair
            // (top,right) with (left,bottom), isolating TR and BL. Both segments are real and both are returned —
            // this differs from the ordinary case, which only ever has one.
            ALL_EDGES_CROSSING -> {
                val centerAverage = (tl + tr + br + bl) / CORNER_COUNT
                if (centerAverage >= level) {
                    listOf(Segment(topPoint!!, leftPoint!!), Segment(bottomPoint!!, rightPoint!!))
                } else {
                    listOf(Segment(topPoint!!, rightPoint!!), Segment(leftPoint!!, bottomPoint!!))
                }
            }

            else -> emptyList() // 1 or 3 crossings only happens when a corner sits exactly on the level; skip it.
        }
    }

    private fun edgeCrossing(a: Float, b: Float, level: Float): Float? {
        val aAbove = a >= level
        val bAbove = b >= level
        if (aAbove == bAbove) return null
        val denominator = b - a
        return if (denominator == 0f) DEFAULT_CROSSING_POSITION else ((level - a) / denominator).coerceIn(0f, 1f)
    }

    /**
     * Chains segments sharing an endpoint into ordered polylines. Endpoints from adjacent cells are computed from the
     * same two corner samples, so they're bit-identical in the overwhelming majority of cases; quantizing to
     * [QUANTIZATION] only mops up the rare degenerate corner-touch, matching iOS's own approach. A chain that closes on
     * itself terminates naturally: once every segment around the loop is visited, the next lookup at its own starting
     * point finds nothing left unvisited.
     */
    private fun chain(segments: List<Segment>): List<List<ContourPoint>> {
        val adjacency = HashMap<Long, MutableList<Segment>>()
        segments.forEach { segment ->
            adjacency.getOrPut(quantize(segment.a)) { mutableListOf() }.add(segment)
            adjacency.getOrPut(quantize(segment.b)) { mutableListOf() }.add(segment)
        }

        val visited = HashSet<Segment>()
        val chains = mutableListOf<List<ContourPoint>>()

        for (start in segments) {
            if (start in visited) continue
            visited += start
            val chainPoints = ArrayDeque<ContourPoint>()
            chainPoints.addLast(start.a)
            chainPoints.addLast(start.b)

            extend(chainPoints, adjacency, visited, atHead = false)
            extend(chainPoints, adjacency, visited, atHead = true)

            chains += chainPoints.toList()
        }
        return chains
    }

    private fun extend(
        chainPoints: ArrayDeque<ContourPoint>,
        adjacency: Map<Long, MutableList<Segment>>,
        visited: MutableSet<Segment>,
        atHead: Boolean,
    ) {
        while (true) {
            val tip = if (atHead) chainPoints.first() else chainPoints.last()
            val next = adjacency[quantize(tip)].orEmpty().firstOrNull { it !in visited } ?: return
            visited += next
            val other = if (quantize(next.a) == quantize(tip)) next.b else next.a
            if (atHead) chainPoints.addFirst(other) else chainPoints.addLast(other)
        }
    }

    private const val QUANTIZATION = 1_048_576.0 // 2^20, matching iOS's endpoint-quantization precision.
    private const val CORNER_COUNT = 4f
    private const val ALL_EDGES_CROSSING = 4 // Saddle cell: all 4 edges of the cell cross the contour level.
    private const val DEFAULT_CROSSING_POSITION = 0.5f // Midpoint when both corners match the level.

    // Packs two quantized 32-bit coordinates into one Long key: qx in the high 32 bits, qy in the low 32.
    private const val LOW_BITS_WIDTH = 32
    private const val LOW_32_BITS_MASK = 0xFFFFFFFFL

    private fun quantize(point: ContourPoint): Long {
        val qx = (point.x * QUANTIZATION).roundToLong()
        val qy = (point.y * QUANTIZATION).roundToLong()
        return (qx shl LOW_BITS_WIDTH) or (qy and LOW_32_BITS_MASK)
    }
}
