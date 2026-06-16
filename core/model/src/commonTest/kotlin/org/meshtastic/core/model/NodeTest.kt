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
package org.meshtastic.core.model

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.util.onlineTimeThreshold
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NodeTest {

    @Test
    fun isOnline_usesStrictThresholdBoundary() {
        val threshold = onlineTimeThreshold()

        assertFalse(Node(num = 1, lastHeard = threshold).isOnline(threshold))
        assertTrue(Node(num = 1, lastHeard = threshold + 1).isOnline(threshold))
    }

    @Test
    fun distance_returnsMetersForKnownCoordinates() {
        val a = nodeWithPosition(num = 1, latitudeI = 450000000, longitudeI = -930000000)
        val b = nodeWithPosition(num = 2, latitudeI = 450000000, longitudeI = -920000000)

        val distance = a.distance(b)

        assertNotNull(distance)
        assertTrue(distance in 78000..79000, "Distance was $distance")
    }

    @Test
    fun bearing_returnsCardinalDirections() {
        val northOrigin = nodeWithPosition(num = 1, latitudeI = 100000000, longitudeI = 100000000)
        val northTarget = nodeWithPosition(num = 2, latitudeI = 200000000, longitudeI = 100000000)
        val southTarget = nodeWithPosition(num = 3, latitudeI = -100000000, longitudeI = 100000000)
        val eastOrigin = nodeWithPosition(num = 4, latitudeI = 1, longitudeI = 200000000)
        val eastTarget = nodeWithPosition(num = 5, latitudeI = 1, longitudeI = 300000000)
        val westTarget = nodeWithPosition(num = 6, latitudeI = 1, longitudeI = 100000000)

        assertEquals(0, northOrigin.bearing(northTarget))
        assertEquals(180, northOrigin.bearing(southTarget))
        assertTrue((eastOrigin.bearing(eastTarget) ?: -1) in 89..90)
        assertTrue((eastOrigin.bearing(westTarget) ?: -1) in 269..270)
    }

    @Test
    fun colors_returnsForegroundAndBackgroundValues() {
        val colors = Node(num = 0x123456).colors

        assertNotNull(colors.first)
        assertNotNull(colors.second)
    }

    @Test
    fun createFallback_createsUnknownUserWithDerivedNames() {
        val node = Node.createFallback(nodeNum = 0x12345678, fallbackNamePrefix = "Unknown")

        assertEquals(0x12345678, node.num)
        assertEquals("!12345678", node.user.id)
        assertEquals("Unknown 5678", node.user.long_name)
        assertEquals("5678", node.user.short_name)
        assertEquals(HardwareModel.UNSET, node.user.hw_model)
        assertTrue(node.isUnknownUser)
    }

    @Test
    fun getRelayNode_filtersCandidatesAndChoosesFewestHops() {
        val chosen = Node(num = 0x000001AA, lastHeard = 100, hopsAway = 2)
        val farther = Node(num = 0x000002AA, lastHeard = 100, hopsAway = 5)
        val unheard = Node(num = 0x000003AA, lastHeard = 0, hopsAway = 1)
        val ourNode = Node(num = 0x000004AA, lastHeard = 100, hopsAway = 0)
        val otherSuffix = Node(num = 0x000005BB, lastHeard = 100, hopsAway = 1)

        val relayNode =
            Node.getRelayNode(
                relayNodeId = 0x0000FFAA.toInt(),
                nodes = listOf(chosen, farther, unheard, ourNode, otherSuffix),
                ourNodeNum = ourNode.num,
            )

        assertEquals(chosen, relayNode)
    }

    @Test
    fun isUnknownUser_falseWhenHardwareModelIsKnown() {
        val node = Node(num = 1, user = User(hw_model = HardwareModel.TLORA_V2))

        assertFalse(node.isUnknownUser)
    }

    @Test
    fun validPosition_returnsPositionOnlyForValidCoordinates() {
        val validPosition = Position(latitude_i = 377749000, longitude_i = -1224194000)
        val validNode = Node(num = 1, position = validPosition)
        val zeroNode = Node(num = 2, position = Position(latitude_i = 0, longitude_i = 0))
        val outOfRangeNode = Node(num = 3, position = Position(latitude_i = 910000000, longitude_i = -1224194000))

        assertEquals(validPosition, validNode.validPosition)
        assertEquals(null, zeroNode.validPosition)
        assertEquals(null, outOfRangeNode.validPosition)
    }

    @Test
    fun hasPKC_usesUserPublicKeyWhenNodeKeyIsMissing() {
        val key = ByteArray(32) { (it + 1).toByte() }.toByteString()
        val node = Node(num = 1, user = User(public_key = key))

        assertTrue(node.hasPKC)
        assertFalse(node.mismatchKey)
    }

    @Test
    fun mismatchKey_trueForErrorByteString() {
        val node = Node(num = 1, publicKey = Node.ERROR_BYTE_STRING)

        assertTrue(node.hasPKC)
        assertTrue(node.mismatchKey)
    }

    private fun nodeWithPosition(num: Int, latitudeI: Int, longitudeI: Int): Node =
        Node(num = num, position = Position(latitude_i = latitudeI, longitude_i = longitudeI))
}
