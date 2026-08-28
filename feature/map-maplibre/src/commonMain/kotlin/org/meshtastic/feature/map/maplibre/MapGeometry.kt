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
package org.meshtastic.feature.map.maplibre

import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.MapBounds
import org.meshtastic.feature.map.MapPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** [MapBounds.aroundNodes] as the box MapLibre wants. */
fun nodesBoundingBox(nodes: List<Node>): BoundingBox? = MapBounds.aroundNodes(nodes)?.toBoundingBox()

/** [MapBounds.around] as the box MapLibre wants. */
fun positionsBoundingBox(positions: List<Position>): BoundingBox? =
    MapBounds.around(positions.map { MapPoint(latitude = it.latitude, longitude = it.longitude) })?.toBoundingBox()

/** This box in MapLibre's own type. The conversion is the only part of it that belongs to a renderer. */
private fun MapBounds.toBoundingBox(): BoundingBox = BoundingBox(
    southwest = Position(longitude = west, latitude = south),
    northeast = Position(longitude = east, latitude = north),
)

/**
 * The nodes clustering will leave standing on their own at [zoom].
 *
 * Both the chips and the precision circles are only wanted for these. Clustering decides the split inside the source at
 * render time, and there is no API that reports it back, so this applies the same rule supercluster does: a point joins
 * a cluster when at least [minPoints] points fall within [radiusPx] of each other in tile pixels. Its own indexes are
 * built per whole zoom level, which is why the caller should pass a floored zoom — it also keeps this off the recompose
 * path for the fractional zoom of a pinch.
 *
 * Distances are measured in Web Mercator world pixels, the space clustering itself works in, rather than in degrees — a
 * degree of longitude is a very different distance at the top of the map than at the equator.
 */
fun unclusteredNodes(nodes: List<Node>, zoom: Int, radiusPx: Int, minPoints: Int, maxClusterZoom: Int): List<Node> {
    // Past its ceiling the source stops clustering entirely, so everything stands alone.
    if (zoom > maxClusterZoom || nodes.size < minPoints) return nodes

    val worldSize = TILE_SIZE * 2.0.pow(zoom)
    val pixels = nodes.map { it.worldPixel(worldSize) }
    val cells = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    pixels.forEachIndexed { index, pixel -> cells.getOrPut(pixel.cell(radiusPx)) { mutableListOf() }.add(index) }

    // Supercluster assigns greedily rather than counting neighbours: it walks the points, and the first one to claim a
    // neighbourhood takes it. Counting instead called a node clustered whenever enough others were nearby, even where
    // those others had already been taken by someone else and the node would in fact be drawn on its own — which left
    // it with a bare dot and no chip.
    val taken = BooleanArray(nodes.size)
    val alone = mutableListOf<Node>()
    pixels.indices.forEach { index ->
        if (taken[index]) return@forEach
        val neighbours = neighboursWithin(index, pixels, cells, radiusPx, taken)
        if (neighbours.size >= minPoints) {
            neighbours.forEach { taken[it] = true }
        } else {
            alone += nodes[index]
        }
    }
    return alone
}

/**
 * [nodes] ordered by how alone each one is: fewest neighbours within [radiusPx] first.
 *
 * This is the order to rasterize chips in. A node the source draws on its own has, by definition, few neighbours, so
 * the ones that need a chip most sort to the front — and the ones just past the cluster threshold, where
 * [unclusteredNodes] is most likely to have guessed wrong, come next rather than last. That makes the image budget run
 * out on nodes buried mid-cluster, which are never drawn anyway.
 */
fun nodesByIsolation(nodes: List<Node>, zoom: Int, radiusPx: Int): List<Node> {
    val worldSize = TILE_SIZE * 2.0.pow(zoom)
    val pixels = nodes.map { it.worldPixel(worldSize) }
    val cells = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
    pixels.forEachIndexed { index, pixel -> cells.getOrPut(pixel.cell(radiusPx)) { mutableListOf() }.add(index) }

    val none = BooleanArray(nodes.size)
    return nodes.indices
        .sortedBy { index -> neighboursWithin(index, pixels, cells, radiusPx, none).size }
        .map { index -> nodes[index] }
}

/** The indices of every point still up for grabs within [radiusPx] of [index], including [index] itself. */
private fun neighboursWithin(
    index: Int,
    pixels: List<Pair<Double, Double>>,
    cells: Map<Pair<Int, Int>, List<Int>>,
    radiusPx: Int,
    taken: BooleanArray,
): List<Int> {
    val point = pixels[index]
    val (cellX, cellY) = point.cell(radiusPx)
    // Only the ring of cells a point within the radius could possibly fall in.
    return CELL_RING.flatMap { (dx, dy) -> cells[cellX + dx to cellY + dy].orEmpty() }
        .filter { candidate -> !taken[candidate] && pixels[candidate].isWithin(radiusPx, point) }
}

/** The nine cells whose contents can be within one cell's width of a point. */
private val CELL_RING = (-1..1).flatMap { dx -> (-1..1).map { dy -> dx to dy } }

private fun Pair<Double, Double>.isWithin(radiusPx: Int, other: Pair<Double, Double>): Boolean {
    val distanceX = first - other.first
    val distanceY = second - other.second
    return distanceX * distanceX + distanceY * distanceY <= radiusPx.toDouble() * radiusPx
}

/** Which grid cell of side [radiusPx] this pixel falls in. */
private fun Pair<Double, Double>.cell(radiusPx: Int): Pair<Int, Int> =
    floor(first / radiusPx).toInt() to floor(second / radiusPx).toInt()

/** This node's position in Web Mercator pixels for a world [worldSize] pixels across. */
private fun Node.worldPixel(worldSize: Double): Pair<Double, Double> {
    val x = (longitude + HALF_TURN) / FULL_TURN * worldSize
    val latitudeRadians = latitude * PI / STRAIGHT_ANGLE
    val mercatorY = ln(tan(latitudeRadians) + 1.0 / cos(latitudeRadians))
    val y = (1.0 - mercatorY / PI) / 2.0 * worldSize
    return x to y
}

/** MapLibre's tile size in pixels, which is the space clustering measures its radius in. */
private const val TILE_SIZE = 512.0
private const val HALF_TURN = 180.0
private const val FULL_TURN = 360.0
private const val STRAIGHT_ANGLE = 180.0

/**
 * The nodes inside [bounds], or all of them when there are none to compare against.
 *
 * Used to decide which nodes are worth rasterizing a chip for. A mesh can hold thousands of nodes while a phone screen
 * shows a few dozen, and drawing an image for every one of them spends the whole image budget on nodes nobody is
 * looking at — at DEF CON scale that meant the nodes actually on screen fell back to plain dots.
 *
 * [bounds] should be padded (see [padded]) so panning a little does not change the answer.
 */
fun nodesInView(nodes: List<Node>, bounds: BoundingBox?): List<Node> {
    if (bounds == null) return nodes
    return nodes.filter { node ->
        node.latitude >= bounds.south &&
            node.latitude <= bounds.north &&
            node.longitude >= bounds.west &&
            node.longitude <= bounds.east
    }
}

/**
 * This box grown by [fraction] of its own span on every side.
 *
 * So that a small pan keeps the same nodes in view, and the set only changes — and chips are only redrawn — once the
 * camera has moved a real distance.
 */
fun BoundingBox.padded(fraction: Double): BoundingBox {
    val latitudePad = (north - south) * fraction
    val longitudePad = (east - west) * fraction
    return BoundingBox(
        southwest = Position(longitude = west - longitudePad, latitude = south - latitudePad),
        northeast = Position(longitude = east + longitudePad, latitude = north + latitudePad),
    )
}
