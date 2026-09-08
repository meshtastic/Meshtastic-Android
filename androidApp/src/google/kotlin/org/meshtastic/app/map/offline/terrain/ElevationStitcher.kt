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
package org.meshtastic.app.map.offline.terrain

import org.meshtastic.feature.map.terrain.ElevationTile
import org.meshtastic.feature.map.terrain.Hillshade

/**
 * Assembles the margin-padded [ElevationTile] [Hillshade.shade] needs from a center tile plus whichever of its 8
 * neighbors are available — the counterpart to the sibling iOS app's `TerrainStore.elevationTile(margin:)`.
 *
 * Every padding pixel is pulled from the neighbor tile whose edge actually borders it (e.g. the padded tile's top-left
 * corner comes from the NW-diagonal neighbor's own bottom-right pixel). Where that neighbor wasn't downloaded — most
 * commonly at the edge of a downloaded region, where there is no tile beyond it at all — this falls back to [center]'s
 * own [ElevationTile.elevationAt], which already clamps out-of-range coordinates to the tile's own edge. That is the
 * same graceful-degradation spirit the task calls for: clamp, don't crash or fabricate data.
 *
 * Pure and I/O-free by design so it's unit-testable without decoding real Terrarium bytes: [HillshadeTileProvider] owns
 * fetching and decoding the neighbor tiles this takes.
 */
internal object ElevationStitcher {

    /** [center]'s own 8 neighbor tiles, keyed by `(dx, dy)` in `{-1,0,1}²` — absent means "not downloaded". */
    fun buildPadded(center: ElevationTile, neighbors: Map<Pair<Int, Int>, ElevationTile>): ElevationTile {
        val margin = Hillshade.MARGIN
        val paddedWidth = center.width + 2 * margin
        val paddedHeight = center.height + 2 * margin
        val elevations = FloatArray(paddedWidth * paddedHeight)
        for (py in -margin..center.height) {
            for (px in -margin..center.width) {
                elevations[(py + margin) * paddedWidth + (px + margin)] = elevationAt(center, neighbors, px, py)
            }
        }
        return ElevationTile(paddedWidth, paddedHeight, elevations)
    }

    /** [px]/[py] are in [center]'s own local coordinate space, extended past its edges into padding territory. */
    private fun elevationAt(
        center: ElevationTile,
        neighbors: Map<Pair<Int, Int>, ElevationTile>,
        px: Int,
        py: Int,
    ): Float {
        if (px in 0 until center.width && py in 0 until center.height) return center.elevationAt(px, py)

        val dx =
            if (px < 0) {
                -1
            } else if (px >= center.width) {
                1
            } else {
                0
            }
        val dy =
            if (py < 0) {
                -1
            } else if (py >= center.height) {
                1
            } else {
                0
            }
        val neighbor = neighbors[dx to dy]
        return if (neighbor == null) {
            center.elevationAt(px, py) // No neighbor there: clamps to center's own edge.
        } else {
            // dx/dy == 0 means px/py is already within center's own range (the interior check above would have
            // returned), so the `else` branch below is only ever reached with an in-range coordinate on that axis.
            val localX =
                when (dx) {
                    -1 -> neighbor.width - 1
                    1 -> 0
                    else -> px
                }
            val localY =
                when (dy) {
                    -1 -> neighbor.height - 1
                    1 -> 0
                    else -> py
                }
            neighbor.elevationAt(localX, localY)
        }
    }
}
