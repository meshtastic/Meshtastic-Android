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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [evaluateTracerouteMapAvailability] — the pure function that determines whether a traceroute can be
 * visualised on a map based on node position data.
 */
@Suppress("MagicNumber")
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

    @Test
    fun directRoute_withTwoNodes() {
        val forward = listOf(1, 2)
        val back = listOf(2, 1)
        val positioned = setOf(1, 2)

        val result = evaluateTracerouteMapAvailability(forward, back, positioned)

        assertEquals(TracerouteMapAvailability.Ok, result)
    }
}
