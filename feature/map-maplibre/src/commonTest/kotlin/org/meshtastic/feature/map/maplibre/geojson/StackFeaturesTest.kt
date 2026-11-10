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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.meshtastic.core.model.Node
import org.meshtastic.feature.map.stackCoincident
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StackFeaturesTest {

    private fun node(num: Int, latitudeI: Int) = Node(
        num = num,
        position =
        Position.Builder()
            .also { wb ->
                wb.latitude_i = latitudeI
                wb.longitude_i = -739508224
            }
            .build(),
        user =
        User.Builder().also { wb -> wb.long_name = "Node $num" }.also { wb -> wb.short_name = "N$num" }.build(),
    )

    private fun features(vararg nodes: Node) =
        stacksToFeatureCollection(stackCoincident(nodes.toList(), myNodeNum = null)).features.map {
            checkNotNull(it.properties)
        }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    @Test
    fun `nodes at one spot become one feature carrying the count and every member`() {
        val stacked = features(node(1, 406585344), node(2, 406585344), node(3, 406585344)).single()

        assertEquals("3", stacked.text(NodeFeatureKeys.STACK_COUNT))
        assertEquals(
            listOf(
                ClusterMember(1, "Node 1", "N1"),
                ClusterMember(2, "Node 2", "N2"),
                ClusterMember(3, "Node 3", "N3"),
            ),
            stacked.stackMembers(),
        )
    }

    @Test
    fun `a node alone at its spot carries no stack and lists only itself`() {
        val alone = features(node(1, 406585344), node(2, 407109632)).first()

        assertNull(alone[NodeFeatureKeys.STACK_COUNT])
        assertEquals(listOf(ClusterMember(1, "Node 1", "N1")), alone.stackMembers())
    }
}
