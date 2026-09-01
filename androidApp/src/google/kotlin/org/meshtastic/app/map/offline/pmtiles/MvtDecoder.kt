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
package org.meshtastic.app.map.offline.pmtiles

/** A point in a feature's own tile-local coordinate space, `0 until layer.extent` on each axis. */
internal data class TileCoord(val x: Int, val y: Int)

/**
 * Decodes an MVT feature's `geometry` command stream (spec section 4.3) into tile-local point rings.
 *
 * For [org.meshtastic.app.map.offline.pmtiles.VectorTile.GEOM_POINT] the single returned ring is every point of a
 * Point/MultiPoint feature. For `GEOM_LINESTRING` each ring is one polyline. For `GEOM_POLYGON` each ring is one closed
 * ring (exterior or a hole; MVT gives no explicit hole flag — winding order is what distinguishes them, and this
 * decoder doesn't need to tell them apart since [com.google.maps.android.compose.Polygon] draws every ring it's given
 * as either the outer boundary or a hole in exactly that order).
 */
internal object MvtDecoder {

    private const val COMMAND_MOVE_TO = 1
    private const val COMMAND_LINE_TO = 2
    private const val COMMAND_CLOSE_PATH = 7
    private const val COMMAND_ID_BITS = 3
    private const val COMMAND_ID_MASK = 0x7

    fun decodeGeometry(type: Int, commands: List<Int>): List<List<TileCoord>> = if (type == VectorTile.GEOM_POINT) {
        val points =
            collectDeltaSteps(commands)
                .runningFold(TileCoord(0, 0)) { cursor, step -> TileCoord(cursor.x + step.dx, cursor.y + step.dy) }
                .drop(1) // the runningFold seed, not a decoded point
        if (points.isEmpty()) emptyList() else listOf(points)
    } else {
        decodeRings(commands)
    }

    private fun decodeRings(commands: List<Int>): List<List<TileCoord>> {
        val rings = mutableListOf<MutableList<TileCoord>>()
        var cursor = TileCoord(0, 0)

        @Suppress("detekt:DoubleMutabilityForCollection")
        var current: MutableList<TileCoord>? = null

        for (step in collectDeltaSteps(commands)) {
            when (step.command) {
                COMMAND_MOVE_TO -> {
                    cursor = TileCoord(cursor.x + step.dx, cursor.y + step.dy)
                    current = mutableListOf(cursor).also { rings += it }
                }

                COMMAND_LINE_TO -> {
                    cursor = TileCoord(cursor.x + step.dx, cursor.y + step.dy)
                    current?.add(cursor)
                }

                COMMAND_CLOSE_PATH ->
                    // ClosePath draws an implicit edge back to the ring's start; materialize it so every ring this
                    // decoder returns is already a closed polygon, whether or not the caller re-closes it itself.
                    current?.let { ring -> if (ring.size > 1) ring.add(ring.first()) }
            }
        }
        return rings
    }

    /** One MoveTo/LineTo parameter pair, already zigzag-decoded; ClosePath carries no delta (dx = dy = 0). */
    private data class DeltaStep(val command: Int, val dx: Int, val dy: Int)

    /** Expands the packed `(id | count<<3), [zigzag dx, zigzag dy] * count` stream into one entry per delta. */
    private fun collectDeltaSteps(commands: List<Int>): List<DeltaStep> {
        val steps = mutableListOf<DeltaStep>()
        var i = 0
        while (i < commands.size) {
            val commandInteger = commands[i]
            i++
            val id = commandInteger and COMMAND_ID_MASK
            val count = commandInteger ushr COMMAND_ID_BITS

            if (id == COMMAND_CLOSE_PATH) {
                // count is always 1 for ClosePath from every real encoder, but the spec allows more; honor it.
                repeat(count) { steps += DeltaStep(id, dx = 0, dy = 0) }
                continue
            }

            repeat(count) {
                if (i + 1 >= commands.size) return@repeat // Truncated stream from a malformed tile; drop the rest.
                val dx = zigZagDecode(commands[i])
                val dy = zigZagDecode(commands[i + 1])
                i += 2
                steps += DeltaStep(id, dx, dy)
            }
        }
        return steps
    }

    private fun zigZagDecode(value: Int): Int = (value ushr 1) xor -(value and 1)
}
