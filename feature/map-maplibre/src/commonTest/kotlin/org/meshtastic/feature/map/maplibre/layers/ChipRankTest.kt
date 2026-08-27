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
package org.meshtastic.feature.map.maplibre.layers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.meshtastic.feature.map.maplibre.geojson.NodeFeatureKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rank that decides which of two stacked chips is drawn on top, and which one a tap lands on.
 *
 * It mirrors the Google flavor's `NodeClusterItem.zIndex`, so the two maps agree about who wins a stack.
 */
class ChipRankTest {

    private fun properties(self: Boolean = false, favorite: Boolean = false): JsonObject = buildJsonObject {
        put(NodeFeatureKeys.IS_SELF, self)
        put(NodeFeatureKeys.IS_FAVORITE, favorite)
    }

    @Test
    fun `this node and favorites outrank everything else`() {
        assertEquals(CHIP_RANK_PROMINENT, properties(self = true).chipRank())
        assertEquals(CHIP_RANK_PROMINENT, properties(favorite = true).chipRank())
        assertEquals(CHIP_RANK_ORDINARY, properties().chipRank())
        assertTrue(CHIP_RANK_PROMINENT > CHIP_RANK_ORDINARY)
    }

    @Test
    fun `properties that are absent rank as ordinary rather than throwing`() {
        // Query results are not guaranteed to carry every key, and a missing flag must not decide a tap by accident.
        assertEquals(CHIP_RANK_ORDINARY, null.chipRank())
        assertEquals(CHIP_RANK_ORDINARY, buildJsonObject {}.chipRank())
    }

    @Test
    fun `a favorite beats an ordinary node when both are under the same tap`() {
        val stacked = listOf(properties(), properties(favorite = true), properties())

        assertEquals(CHIP_RANK_PROMINENT, stacked.maxOf { it.chipRank() })
    }
}
