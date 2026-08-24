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
package org.meshtastic.app.map

import org.meshtastic.core.model.Node
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class SitePlannerLaunchTest {

    @Test
    fun `node detail launch retains selected node while manual launch follows connected node`() {
        val connectedNode = positionedNode(num = 11, latitudeI = 10_000_000, longitudeI = 20_000_000)
        val selectedNode = positionedNode(num = 22, latitudeI = 30_000_000, longitudeI = 40_000_000)
        val updatedConnectedNode = positionedNode(num = 11, latitudeI = 50_000_000, longitudeI = 60_000_000)

        val routeLaunch =
            SitePlannerLaunch(
                initialParams = selectedNode.toSitePlannerParams(channelSet = null),
                selectedNode = selectedNode,
            )
        val manualLaunch = SitePlannerLaunch(initialParams = connectedNode.toSitePlannerParams(channelSet = null))

        assertEquals(selectedNode.latitude to selectedNode.longitude, routeLaunch.nodeLocation(connectedNode))
        assertEquals(
            updatedConnectedNode.latitude to updatedConnectedNode.longitude,
            manualLaunch.nodeLocation(updatedConnectedNode),
        )
    }

    private fun positionedNode(num: Int, latitudeI: Int, longitudeI: Int): Node =
        Node(num = num, position = Position(latitude_i = latitudeI, longitude_i = longitudeI))
}
