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
package org.meshtastic.feature.map.maplibre.geojson

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class ClusterMembersTest {

    private fun leaf(properties: JsonObject?) =
        Feature(geometry = Point(Position(longitude = -93.6, latitude = 41.6)), properties = properties)

    @Test
    fun `reads node number and both names off the leaf features`() {
        val leaves =
            FeatureCollection(
                leaf(
                    buildJsonObject {
                        put(NodeFeatureKeys.NODE_NUM, 1234)
                        put(NodeFeatureKeys.LONG_NAME, "Des Moines Relay")
                        put(NodeFeatureKeys.SHORT_NAME, "DSM1")
                    },
                ),
            )

        assertEquals(listOf(ClusterMember(1234, "Des Moines Relay", "DSM1")), leaves.toClusterMembers())
    }

    @Test
    fun `drops leaves with no node number rather than inventing one`() {
        val leaves =
            FeatureCollection(
                leaf(buildJsonObject { put(NodeFeatureKeys.LONG_NAME, "nameless") }),
                leaf(null),
                leaf(buildJsonObject { put(NodeFeatureKeys.NODE_NUM, 7) }),
            )

        assertEquals(listOf(ClusterMember(7, "", "")), leaves.toClusterMembers())
    }
}
