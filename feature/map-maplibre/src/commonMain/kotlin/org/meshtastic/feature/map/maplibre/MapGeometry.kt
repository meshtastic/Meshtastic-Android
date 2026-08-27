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
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.LastHeardFilter

/**
 * Applies the map's filter chips to the node list.
 *
 * Pure so it can be tested without a renderer: the filter rules are the part users notice when they go wrong, and they
 * should not need a GPU to verify.
 */
fun filterNodesForMap(nodes: List<Node>, filterState: BaseMapViewModel.MapFilterState, nowSeconds: Long): List<Node> =
    nodes
        .filter { node -> node.validPosition != null }
        .filter { node -> !filterState.onlyFavorites || node.isFavorite }
        .filter { node ->
            val window = filterState.lastHeardFilter.seconds
            window == LastHeardFilter.Any.seconds || (nowSeconds - node.lastHeard) <= window
        }

/**
 * Bounding box covering every supplied node, or null when no node has a fix.
 *
 * Returning null rather than a degenerate box at (0, 0) is deliberate — the OSMdroid map's habit of flying through the
 * Atlantic on startup came from treating "no data yet" as a real location.
 */
fun nodesBoundingBox(nodes: List<Node>): BoundingBox? = positionsBoundingBox(
    nodes.filter { it.validPosition != null }.map { Position(longitude = it.longitude, latitude = it.latitude) },
)

/**
 * Bounding box covering [positions], or null when there are none.
 *
 * A single point, or several stacked on one spot, yields a zero-area box the camera cannot fit to — a stationary node's
 * whole position track is exactly that — so a degenerate box is padded out to something framable.
 */
fun positionsBoundingBox(positions: List<Position>): BoundingBox? {
    if (positions.isEmpty()) return null

    var south = positions.first().latitude
    var north = south
    var west = positions.first().longitude
    var east = west

    positions.forEach { position ->
        south = minOf(south, position.latitude)
        north = maxOf(north, position.latitude)
        west = minOf(west, position.longitude)
        east = maxOf(east, position.longitude)
    }

    if (south == north && west == east) {
        south -= SINGLE_POINT_PAD_DEG
        north += SINGLE_POINT_PAD_DEG
        west -= SINGLE_POINT_PAD_DEG
        east += SINGLE_POINT_PAD_DEG
    }

    return BoundingBox(
        southwest = Position(longitude = west, latitude = south),
        northeast = Position(longitude = east, latitude = north),
    )
}

private const val SINGLE_POINT_PAD_DEG = 0.01

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
