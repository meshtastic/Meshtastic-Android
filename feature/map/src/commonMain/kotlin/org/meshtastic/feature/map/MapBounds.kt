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
package org.meshtastic.feature.map

import org.meshtastic.core.model.Node

/** A point on the map, in degrees. Deliberately not either renderer's type — see [MapBounds]. */
data class MapPoint(val latitude: Double, val longitude: Double)

/**
 * A latitude/longitude box, in degrees.
 *
 * Neither renderer's box: MapLibre has `spatialk`'s `BoundingBox` and the Google map has `LatLngBounds`, and each
 * converts this at its own edge. Keeping the maths here is the point — it is the same maths, and it lived only in the
 * MapLibre module, which the Google flavour cannot depend on, so the Google map hand-rolled `LatLngBounds.builder()` in
 * three places instead.
 */
data class MapBounds(val south: Double, val west: Double, val north: Double, val east: Double) {

    companion object {

        /**
         * A box covering [points], or null when there are none.
         *
         * Null rather than a box at (0, 0): treating "no data yet" as a real location is how the OSMdroid map used to
         * open in the Atlantic.
         *
         * A single point, or several stacked on one spot, yields a zero-area box the camera cannot fit to — a
         * stationary node's whole position track is exactly that — so a degenerate box is padded to something framable.
         */
        fun around(points: List<MapPoint>): MapBounds? {
            if (points.isEmpty()) return null

            var south = points.first().latitude
            var north = south
            var west = points.first().longitude
            var east = west

            points.forEach { point ->
                south = minOf(south, point.latitude)
                north = maxOf(north, point.latitude)
                west = minOf(west, point.longitude)
                east = maxOf(east, point.longitude)
            }

            if (south == north && west == east) {
                south -= SINGLE_POINT_PAD_DEG
                north += SINGLE_POINT_PAD_DEG
                west -= SINGLE_POINT_PAD_DEG
                east += SINGLE_POINT_PAD_DEG
            }

            return MapBounds(south = south, west = west, north = north, east = east)
        }

        /** A box covering every node that has a fix, or null when none does. */
        fun aroundNodes(nodes: List<Node>): MapBounds? =
            around(nodes.filter { it.validPosition != null }.map { MapPoint(it.latitude, it.longitude) })

        private const val SINGLE_POINT_PAD_DEG = 0.01
    }
}
