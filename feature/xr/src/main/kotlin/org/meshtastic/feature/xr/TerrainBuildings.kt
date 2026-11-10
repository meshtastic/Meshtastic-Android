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

import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import org.meshtastic.feature.map.terrain.spatial.Vec2
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One building: its outline and how tall it is. Most buildings in OpenStreetMap carry no height.
 *
 * @param minHeightMeters where a part starts above the ground, for the upper floors of a tower
 * @param isPart a piece of a building drawn in parts, like a tower's legs, deck and spire
 * @param holes courtyards, left open in the roof and walled round
 */
data class BuildingFootprint(
    val outline: List<LatLon>,
    val heightMeters: Double?,
    val minHeightMeters: Double = 0.0,
    val isPart: Boolean = false,
    val holes: List<List<LatLon>> = emptyList(),
)

/**
 * Where the terrain map gets its buildings.
 *
 * @param attribution plain text, shown while buildings are up
 * @param available whether there are any to offer, asked each time the map opens
 * @param footprints the buildings in a box, empty when the box is too wide for any to show
 */
class TerrainBuildingSource(
    val attribution: String,
    val available: suspend () -> Boolean,
    val footprints: suspend (GeoBounds) -> List<BuildingFootprint>,
)

/** Building outlines raised to their heights and stood on the terrain, as one mesh. */
internal object TerrainBuildings {
    /** For buildings with no height in the data: about two floors. */
    const val DEFAULT_HEIGHT_METERS = 6.0

    /** The biggest buildings are kept first when there are more triangles than this. */
    const val TRIANGLE_BUDGET = 60_000

    /** Flat ground is stretched further than this to read as relief; a tower stretched that far is a needle. */
    const val MAX_EXAGGERATION = 1.5

    /** Buildings stand inside this share of the disk's radius, clear of the bezel. */
    private const val DISK_SHARE = 0.98f
    private const val SAME_POINT = 1e-7f
    private const val DEGREE_SCALE = 1e5

    /** A quiet gray, so the pins, links and routes in color are what stands out over a city. */
    private val WALLS = Rgb(0.6f, 0.61f, 0.63f)

    /** [outline] runs in order of increasing angle, x toward z, and each of [holes] the other way round. */
    private class Placed(
        val outline: List<Vec2>,
        val holes: List<List<Vec2>>,
        val bottom: Float,
        val top: Float,
        val area: Float,
    )

    /**
     * Walls and a flat roof for each building, standing on the lowest ground under its outline and raised by the
     * terrain's own height scale up to [MAX_EXAGGERATION]. A building with a corner past the disk's edge is left out.
     * Null with none to draw.
     */
    fun meshData(
        footprints: List<BuildingFootprint>,
        geometry: SpatialTerrainGeometry,
        triangleBudget: Int = TRIANGLE_BUDGET,
    ): TerrainMeshData? {
        val placed =
            withoutOutlinesOfParts(footprints).mapNotNull { place(it, geometry) }.sortedByDescending { it.area }
        val acc = MeshAccumulator()
        var triangles = 0
        for (building in placed) {
            val corners = Courtyards.cut(building.outline, building.holes)
            val roof = triangulate(corners)
            val cost = 2 * (building.outline.size + building.holes.sumOf { it.size }) + roof.size / 3
            if (triangles + cost > triangleBudget) break
            triangles += cost
            acc.building(building, corners, roof)
        }
        return acc.build().takeIf { it.vertexCount > 0 }
    }

    private fun place(footprint: BuildingFootprint, geometry: SpatialTerrainGeometry): Placed? {
        val transform = geometry.transform
        fun ring(points: List<LatLon>) = outline(
            points.map {
                val p = transform.position(it, transform.baseElevation)
                Vec2(p.x, p.z)
            },
        )
        val corners = ring(footprint.outline)
        val area = signedArea(corners)
        val limit = transform.diskRadius * DISK_SHARE
        if (corners.size < 3 || area == 0f || corners.any { hypot(it.x, it.y) > limit }) return null
        val ground = footprint.outline.minOf { geometry.groundHeight(it) }
        val heightScale = minOf(transform.verticalExaggeration, MAX_EXAGGERATION) * transform.scale
        val height = max(footprint.heightMeters ?: DEFAULT_HEIGHT_METERS, 1.0)
        val minHeight = footprint.minHeightMeters.coerceIn(0.0, height - 1)
        return Placed(
            // Corners in order of increasing angle, x toward z, the order the faces expect.
            outline = if (area < 0) corners.reversed() else corners,
            holes =
            footprint.holes
                .map(::ring)
                .filter { it.size >= 3 && signedArea(it) != 0f }
                .map { if (signedArea(it) > 0) it.reversed() else it },
            bottom = ground + (minHeight * heightScale).toFloat(),
            top = ground + (height * heightScale).toFloat(),
            area = abs(area),
        )
    }

    private fun MeshAccumulator.building(building: Placed, corners: List<Vec2>, roof: IntArray) {
        // Seen from above the outline runs the other way round, so each triangle is turned over.
        val turned =
            IntArray(roof.size) {
                val corner = it % 3
                roof[it - corner + if (corner == 0) 0 else 3 - corner]
            }
        val top = corners.map { floatArrayOf(it.x, building.top, it.y) }
        face(top, turned, 0f, 1f, 0f, WALLS.scaled(bakedLight(0f, 1f, 0f)))
        // A courtyard's ring runs the other way, so its walls face into it.
        val edges = (listOf(building.outline) + building.holes).flatMap { ring -> ring.indices.map { ring to it } }
        for ((ring, i) in edges) {
            val p = ring[i]
            val q = ring[(i + 1) % ring.size]
            val length = sqrt((q.x - p.x) * (q.x - p.x) + (q.y - p.y) * (q.y - p.y))
            val nx = (q.y - p.y) / length
            val nz = -(q.x - p.x) / length
            quad(
                listOf(
                    floatArrayOf(q.x, building.bottom, q.y),
                    floatArrayOf(p.x, building.bottom, p.y),
                    floatArrayOf(p.x, building.top, p.y),
                    floatArrayOf(q.x, building.top, q.y),
                ),
                nx,
                0f,
                nz,
                WALLS.scaled(bakedLight(nx, 0f, nz)),
            )
        }
    }

    /**
     * A building drawn in parts is drawn by its parts alone. Its outline covers the whole footprint at the full height,
     * so drawn as well it hides them and a tower comes out as one column. An outline with a point of a part inside it,
     * and not in one of its courtyards, is left out.
     */
    fun withoutOutlinesOfParts(footprints: List<BuildingFootprint>): List<BuildingFootprint> {
        val points = footprints.filter { it.isPart }.mapNotNull { interiorPoint(it.outline) }
        if (points.isEmpty()) return footprints
        return footprints.filter { footprint ->
            footprint.isPart ||
                points.none { point ->
                    contains(footprint.outline, point) && footprint.holes.none { contains(it, point) }
                }
        }
    }

    /**
     * A point inside the outline even when it is concave, where the average of the corners can fall outside: the middle
     * of the largest triangle filling it. Worked in offsets from the first corner so Float keeps sub-meter precision.
     */
    fun interiorPoint(outline: List<LatLon>): LatLon? {
        val origin = outline.firstOrNull() ?: return null
        val offsets =
            outline(
                outline.map {
                    Vec2(
                        ((it.longitude - origin.longitude) * DEGREE_SCALE).toFloat(),
                        ((it.latitude - origin.latitude) * DEGREE_SCALE).toFloat(),
                    )
                },
            )
        val corners = if (signedArea(offsets) < 0) offsets.reversed() else offsets
        val roof = triangulate(corners)
        fun corner(triangle: Int, k: Int) = corners[roof[triangle + k]]
        val largest = (roof.indices step 3).maxByOrNull { abs(cross(corner(it, 0), corner(it, 1), corner(it, 2))) }
        return largest?.let {
            val a = corner(it, 0)
            val b = corner(it, 1)
            val c = corner(it, 2)
            LatLon(
                origin.latitude + (a.y + b.y + c.y) / 3 / DEGREE_SCALE,
                origin.longitude + (a.x + b.x + c.x) / 3 / DEGREE_SCALE,
            )
        }
    }

    /** Whether the point is inside the outline, by counting the edges a line east from it crosses. */
    fun contains(outline: List<LatLon>, point: LatLon): Boolean {
        var inside = false
        var j = outline.size - 1
        for (i in outline.indices) {
            val a = outline[i]
            val b = outline[j]
            val straddles = (a.latitude > point.latitude) != (b.latitude > point.latitude)
            if (straddles) {
                val along = (point.latitude - a.latitude) / (b.latitude - a.latitude)
                if (point.longitude < (b.longitude - a.longitude) * along + a.longitude) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Drops repeated corners, including a last corner that closes the ring onto the first. */
    fun outline(points: List<Vec2>): List<Vec2> {
        val result = ArrayList<Vec2>()
        for (p in points) if (result.isEmpty() || distance(result.last(), p) > SAME_POINT) result.add(p)
        while (result.size > 1 && distance(result.first(), result.last()) <= SAME_POINT) {
            result.removeAt(result.size - 1)
        }
        return result
    }

    /** Twice the area, positive when the corners run in order of increasing angle, x toward y. */
    fun signedArea(outline: List<Vec2>): Float {
        var sum = 0f
        for (i in outline.indices) {
            val p = outline[i]
            val q = outline[(i + 1) % outline.size]
            sum += p.x * q.y - q.x * p.y
        }
        return sum
    }

    /**
     * Triangles filling an outline whose corners run in order of increasing angle, by cutting off one corner at a time,
     * so L-shaped and other concave roofs fill correctly. Each triangle is in the outline's own order. What is left of
     * a tangled outline that cannot be cut is filled as a fan.
     */
    fun triangulate(outline: List<Vec2>): IntArray {
        val remaining = outline.indices.toMutableList()
        val result = ArrayList<Int>()
        var cut = true
        while (remaining.size > 3 && cut) {
            val ear = remaining.indices.firstOrNull { isEar(outline, remaining, it) }
            cut = ear != null
            if (ear != null) {
                result.add(remaining[(ear + remaining.size - 1) % remaining.size])
                result.add(remaining[ear])
                result.add(remaining[(ear + 1) % remaining.size])
                remaining.removeAt(ear)
            }
        }
        for (k in 1 until remaining.size - 1) {
            result.add(remaining[0])
            result.add(remaining[k])
            result.add(remaining[k + 1])
        }
        return result.toIntArray()
    }

    /** Whether the corner at [k] of [remaining] turns the right way and holds no other corner. */
    private fun isEar(outline: List<Vec2>, remaining: List<Int>, k: Int): Boolean {
        val ia = remaining[(k + remaining.size - 1) % remaining.size]
        val ib = remaining[k]
        val ic = remaining[(k + 1) % remaining.size]
        val a = outline[ia]
        val b = outline[ib]
        val c = outline[ic]
        return cross(a, b, c) > 0 &&
            remaining.none { j ->
                val p = outline[j]
                // A corner a courtyard's cut repeats is the ear's own corner, not one inside it.
                val own = j == ia || j == ib || j == ic || p == a || p == b || p == c
                !own && cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
            }
    }
}

/**
 * Joins courtyards into an outline so one run of corners goes round both: out along a cut to the courtyard, round it,
 * and back along the same cut.
 */
private object Courtyards {
    private const val CUTS_TRIED = 8

    /** [outer] with each of [holes] cut in. A courtyard no cut can reach without crossing an edge is roofed over. */
    fun cut(outer: List<Vec2>, holes: List<List<Vec2>>): List<Vec2> {
        var ring = outer
        for (hole in holes) ring = joined(ring, hole, holes) ?: ring
        return ring
    }

    private fun joined(ring: List<Vec2>, hole: List<Vec2>, holes: List<List<Vec2>>): List<Vec2>? {
        // Each of the courtyard's corners with the outline's corner nearest it, the shortest few of those tried.
        val (i, j) =
            hole.indices
                .map { j -> ring.indices.minBy { distance(ring[it], hole[j]) } to j }
                .sortedBy { (i, j) -> distance(ring[i], hole[j]) }
                .take(CUTS_TRIED)
                .firstOrNull { (i, j) -> (holes + listOf(ring)).none { crosses(ring[i], hole[j], it) } } ?: return null
        val round = List(hole.size + 1) { hole[(j + it) % hole.size] }
        return ring.subList(0, i + 1) + round + ring.subList(i, ring.size)
    }

    /** Whether the cut from [a] to [b] crosses an edge of [ring]. Edges that only touch an end of it do not count. */
    private fun crosses(a: Vec2, b: Vec2, ring: List<Vec2>): Boolean = ring.indices.any {
        val c = ring[it]
        val d = ring[(it + 1) % ring.size]
        cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0
    }
}

private fun cross(a: Vec2, b: Vec2, c: Vec2): Float = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

private fun distance(a: Vec2, b: Vec2): Float = hypot(a.x - b.x, a.y - b.y)
