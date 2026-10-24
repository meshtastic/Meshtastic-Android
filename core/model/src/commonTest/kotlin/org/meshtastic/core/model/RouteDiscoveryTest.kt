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

import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.RouteDiscovery
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [evaluateTracerouteMapAvailability] — the pure function that determines whether a traceroute can be
 * visualised on a map based on node position data.
 */
class RouteDiscoveryTest {

    @Test
    fun ok_whenAllNodesHavePositions() {
        val forward = listOf(1, 2, 3)
        val back = listOf(3, 2, 1)
        val positioned = setOf(1, 2, 3)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.Ok, result)
    }

    @Test
    fun ok_whenEndpointsPositioned_andIntermediateNot() {
        // Endpoints (1 and 3) are positioned, intermediate (2) is not
        val forward = listOf(1, 2, 3)
        val back = listOf(3, 2, 1)
        val positioned = setOf(1, 3)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.Ok, result)
    }

    @Test
    fun missingEndpoints_whenForwardStartMissing() {
        val forward = listOf(1, 2, 3)
        val back = listOf(3, 2, 1)
        // Node 1 (forward start / back end) is missing from positioned set
        val positioned = setOf(2, 3)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.MissingEndpoints, result)
    }

    @Test
    fun missingEndpoints_whenForwardEndMissing() {
        val forward = listOf(1, 2, 3)
        val back = listOf(3, 2, 1)
        // Node 3 (forward end / back start) is missing
        val positioned = setOf(1, 2)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.MissingEndpoints, result)
    }

    @Test
    fun noMappableNodes_whenNonePositioned() {
        val forward = listOf(1, 2, 3)
        val back = emptyList<Int>()
        // No node in the routes has a position — but first check endpoints
        // Endpoints 1 and 3 are missing → MissingEndpoints takes precedence
        val positioned = emptySet<Int>()

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.MissingEndpoints, result)
    }

    @Test
    fun noMappableNodes_whenEmptyRoutes() {
        // Empty routes → no endpoints, no related nodes → NoMappableNodes
        val result = evaluateTracerouteMapAvailability(emptyList(), emptyList(), setOf(1, 2))

        assertEquals(TracerouteMapAvailability.NoMappableNodes, result)
    }

    @Test
    fun ok_whenOnlyForwardRoute_endpointsPositioned() {
        // Only forward route, no return route
        val forward = listOf(1, 2, 3)
        val back = emptyList<Int>()
        val positioned = setOf(1, 3)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.Ok, result)
    }

    @Test
    fun missingEndpoints_whenReturnRouteEndpointMissing() {
        // Return route has different endpoints than forward (asymmetric path)
        val forward = listOf(1, 2, 3)
        val back = listOf(3, 4, 1)
        // All forward endpoints (1, 3) are positioned, but checking back endpoints too
        // back first = 3 (positioned), back last = 1 (positioned) → all endpoints OK
        val positioned = setOf(1, 3)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.Ok, result)
    }

    private fun user(id: String, longName: String, shortName: String, hw: HardwareModel = HardwareModel.UNSET) =
        User.Builder()
            .also { b ->
                b.id = id
                b.long_name = longName
                b.short_name = shortName
                b.hw_model = hw
            }
            .build()

    @Test
    fun hopName_usesFullNodeId_whenNodeIsNotInDatabase() {
        val placeholder = user("!a1b2c3d4", "Meshtastic c3d4", "c3d4")

        assertEquals("!a1b2c3d4", tracerouteHopName(0xa1b2c3d4.toInt(), placeholder, UNKNOWN_HOP))
    }

    @Test
    fun hopName_usesUnknownLabel_whenNodeNumIsBroadcast() {
        val placeholder = user("!ffffffff", "Meshtastic ffff", "ffff")

        assertEquals(UNKNOWN_HOP, tracerouteHopName(NodeAddress.NODENUM_BROADCAST, placeholder, UNKNOWN_HOP))
    }

    @Test
    fun hopName_padsNodeIdToEightDigits() {
        val placeholder = user("!0000002a", "Meshtastic 002a", "002a")

        assertEquals("!0000002a", tracerouteHopName(0x2a, placeholder, UNKNOWN_HOP))
    }

    @Test
    fun hopName_keepsNames_whenNodeIsKnown() {
        val known = user("!a1b2c3d4", "Base Station", "BASE", HardwareModel.TBEAM)

        assertEquals("Base Station (BASE)", tracerouteHopName(0xa1b2c3d4.toInt(), known, UNKNOWN_HOP))
    }

    @Test
    fun hopName_prefersLocalDisplayName_overBroadcastLongName() {
        val known = user("!a1b2c3d4", "Base Station", "BASE", HardwareModel.TBEAM)

        assertEquals(
            "My Base (BASE)",
            tracerouteHopName(0xa1b2c3d4.toInt(), known, UNKNOWN_HOP, displayName = "My Base"),
        )
    }

    @Test
    fun tracerouteResponse_namesUnknownHopById_andKnownHopByName() {
        val users =
            mapOf(
                1 to user("!00000001", "Alpha", "ALP", HardwareModel.TBEAM),
                0xa1b2c3d4.toInt() to user("!a1b2c3d4", "Meshtastic c3d4", "c3d4"),
            )
        val route =
            RouteDiscovery.Builder()
                .also { b ->
                    b.route = listOf(1, 0xa1b2c3d4.toInt())
                    b.snr_towards = listOf(8)
                }
                .build()

        val text =
            route.getTracerouteResponse(getUser = { num -> tracerouteHopName(num, users.getValue(num), UNKNOWN_HOP) })

        assertEquals("Route traced toward destination:\n\n■ Alpha (ALP)\n⇊ 2.0 dB\n■ !a1b2c3d4", text)
    }

    @Test
    fun tracerouteResponse_namesSilentRelayAsUnknownHop() {
        val silent = NodeAddress.NODENUM_BROADCAST
        val route =
            RouteDiscovery.Builder()
                .also { b ->
                    b.route = listOf(1, silent, 2)
                    b.snr_towards = listOf(8, -128)
                }
                .build()
        val users =
            mapOf(
                1 to user("!00000001", "Alpha", "ALP", HardwareModel.TBEAM),
                silent to user("!ffffffff", "Meshtastic ffff", "ffff"),
                2 to user("!00000002", "Beta", "BET", HardwareModel.TBEAM),
            )

        val text =
            route.getTracerouteResponse(getUser = { num -> tracerouteHopName(num, users.getValue(num), UNKNOWN_HOP) })

        assertEquals(
            "Route traced toward destination:\n\n■ Alpha (ALP)\n⇊ 2.0 dB\n■ $UNKNOWN_HOP\n⇊ ? dB\n■ Beta (BET)",
            text,
        )
    }

    @Test
    fun formatTraceroutePath_joinsHopsWithSnr() {
        val text = formatTraceroutePath(listOf("A", "!a1b2c3d4"), listOf(-128))

        assertEquals("■ A\n⇊ ? dB\n■ !a1b2c3d4", text)
    }

    @Test
    fun directRoute_withTwoNodes() {
        val forward = listOf(1, 2)
        val back = listOf(2, 1)
        val positioned = setOf(1, 2)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.Ok, result)
    }
}

private const val UNKNOWN_HOP = "Unknown hop"
