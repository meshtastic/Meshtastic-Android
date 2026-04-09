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
package org.meshtastic.feature.node.metrics

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.util.TimeConstants.MS_PER_SEC
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [resolveTraceroutePoints] — the pure function that pairs traceroute requests with their responses and
 * computes hop counts and round-trip duration.
 *
 * Wire format note: The [RouteDiscovery] proto on the wire contains only **intermediate** hops (not endpoints).
 * [MeshPacket.fullRouteDiscovery] prepends the destination and appends the source to produce the full route. For
 * `route_back` to be wrapped with endpoints, `hop_start > 0` and `snr_back` must be non-empty.
 */
@Suppress("MagicNumber")
class TracerouteChartTest {

    companion object {
        /** Node number for the local (requesting) node. */
        private const val LOCAL_NODE = 1

        /** Node number for the remote (destination) node. */
        private const val REMOTE_NODE = 2

        /** Dummy SNR value used to satisfy the snr_back requirement. */
        private const val DUMMY_SNR = 10
    }

    /**
     * Creates a traceroute **request** MeshLog.
     *
     * @param id Packet ID used to correlate request with response.
     * @param receivedDateMillis Timestamp in milliseconds.
     */
    private fun makeRequest(id: Int, receivedDateMillis: Long): MeshLog = MeshLog(
        uuid = "req-$id",
        message_type = "TRACEROUTE",
        received_date = receivedDateMillis,
        raw_message = "",
        fromRadio =
        FromRadio(
            packet =
            MeshPacket(
                id = id,
                from = LOCAL_NODE,
                to = REMOTE_NODE,
                decoded = Data(portnum = PortNum.TRACEROUTE_APP, want_response = true),
            ),
        ),
    )

    /**
     * Creates a traceroute **result** MeshLog that matches a request by [requestId].
     *
     * @param intermediateRoute Intermediate hops on the forward path (wire format, no endpoints).
     * @param intermediateRouteBack Intermediate hops on the return path (wire format, no endpoints). Pass `null` to
     *   omit route_back entirely (simulates no return route data).
     * @param hopStart Non-zero hop_start is required (along with snr_back) for fullRouteDiscovery to wrap route_back
     *   with endpoints. Defaults to 3.
     */
    private fun makeResult(
        requestId: Int,
        receivedDateMillis: Long,
        intermediateRoute: List<Int> = listOf(3),
        intermediateRouteBack: List<Int>? = listOf(3),
        hopStart: Int = 3,
    ): MeshLog {
        // snr_back must have one entry per node in route_back for fullRouteDiscovery to wrap it
        val snrBack = intermediateRouteBack?.map { DUMMY_SNR } ?: emptyList()
        val rd =
            RouteDiscovery(
                route = intermediateRoute,
                route_back = intermediateRouteBack ?: emptyList(),
                snr_back = snrBack,
            )
        return MeshLog(
            uuid = "res-$requestId",
            message_type = "TRACEROUTE",
            received_date = receivedDateMillis,
            raw_message = "",
            fromRadio =
            FromRadio(
                packet =
                MeshPacket(
                    from = REMOTE_NODE,
                    to = LOCAL_NODE,
                    hop_start = hopStart,
                    decoded =
                    Data(
                        portnum = PortNum.TRACEROUTE_APP,
                        request_id = requestId,
                        payload = RouteDiscovery.ADAPTER.encode(rd).toByteString(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun matchesRequestToResult() {
        val requestTime = 1000L * MS_PER_SEC
        val resultTime = 1005L * MS_PER_SEC
        val requests = listOf(makeRequest(id = 42, receivedDateMillis = requestTime))
        val results = listOf(makeResult(requestId = 42, receivedDateMillis = resultTime))

        val points = resolveTraceroutePoints(requests, results)

        assertEquals(1, points.size)
        val point = points.first()
        assertEquals(requests.first(), point.request)
        assertNotNull(point.result)
        // timeSeconds = received_date (millis) / MS_PER_SEC
        assertEquals(1000.0, point.timeSeconds)
    }

    @Test
    fun computesForwardHops() {
        val requests = listOf(makeRequest(id = 1, receivedDateMillis = 1000L * MS_PER_SEC))
        // 2 intermediate hops → fullRoute = [dest, hop1, hop2, src] → size 4 → hops = 2
        val results =
            listOf(
                makeResult(requestId = 1, receivedDateMillis = 1005L * MS_PER_SEC, intermediateRoute = listOf(10, 20)),
            )

        val point = resolveTraceroutePoints(requests, results).first()

        assertEquals(2, point.forwardHops)
    }

    @Test
    fun directRoute_yieldsZeroHops() {
        val requests = listOf(makeRequest(id = 1, receivedDateMillis = 1000L * MS_PER_SEC))
        // Direct route: no intermediate hops → fullRoute = [dest, src] → size 2 → hops = 0
        // route_back also empty intermediate → fullRouteBack = [src, dest] → size 2 → hops = 0
        val results =
            listOf(
                makeResult(
                    requestId = 1,
                    receivedDateMillis = 1002L * MS_PER_SEC,
                    intermediateRoute = emptyList(),
                    intermediateRouteBack = emptyList(),
                ),
            )

        val point = resolveTraceroutePoints(requests, results).first()

        assertEquals(0, point.forwardHops)
        // route_back with empty intermediateRouteBack: snr_back will be empty,
        // so fullRouteDiscovery won't wrap it → raw route_back is empty → returnHops = null
        assertNull(point.returnHops)
    }

    @Test
    fun computesRoundTripSeconds() {
        val requestTime = 2000L * MS_PER_SEC // 2_000_000 ms
        val resultTime = requestTime + 3500L // 3.5 seconds later in millis
        val requests = listOf(makeRequest(id = 1, receivedDateMillis = requestTime))
        val results = listOf(makeResult(requestId = 1, receivedDateMillis = resultTime))

        val point = resolveTraceroutePoints(requests, results).first()

        val rtt = assertNotNull(point.roundTripSeconds)
        assertEquals(3.5, rtt, 0.01)
    }

    @Test
    fun noMatchingResult_yieldsNulls() {
        val requests = listOf(makeRequest(id = 1, receivedDateMillis = 1000L * MS_PER_SEC))
        // Result has a different requestId, so it won't match
        val results = listOf(makeResult(requestId = 99, receivedDateMillis = 1005L * MS_PER_SEC))

        val point = resolveTraceroutePoints(requests, results).first()

        assertNull(point.result)
        assertNull(point.forwardHops)
        assertNull(point.returnHops)
        assertNull(point.roundTripSeconds)
    }

    @Test
    fun emptyInputs_returnsEmpty() {
        assertEquals(emptyList(), resolveTraceroutePoints(emptyList(), emptyList()))
    }

    @Test
    fun multipleRequests_preservesOrder() {
        val req1 = makeRequest(id = 1, receivedDateMillis = 3000L * MS_PER_SEC)
        val req2 = makeRequest(id = 2, receivedDateMillis = 4000L * MS_PER_SEC)
        val res1 = makeResult(requestId = 1, receivedDateMillis = 3005L * MS_PER_SEC)
        val res2 = makeResult(requestId = 2, receivedDateMillis = 4005L * MS_PER_SEC)

        val points = resolveTraceroutePoints(listOf(req1, req2), listOf(res1, res2))

        assertEquals(2, points.size)
        assertEquals(3000.0, points[0].timeSeconds)
        assertEquals(4000.0, points[1].timeSeconds)
    }

    @Test
    fun emptyRouteBack_yieldsNullReturnHops() {
        val requests = listOf(makeRequest(id = 1, receivedDateMillis = 1000L * MS_PER_SEC))
        // 1 intermediate hop forward, but null route_back → no return path data
        val results =
            listOf(
                makeResult(
                    requestId = 1,
                    receivedDateMillis = 1005L * MS_PER_SEC,
                    intermediateRoute = listOf(3),
                    intermediateRouteBack = null,
                ),
            )

        val point = resolveTraceroutePoints(requests, results).first()

        assertEquals(1, point.forwardHops)
        assertNull(point.returnHops)
    }

    @Test
    fun returnHops_computedWhenRouteBackAvailable() {
        val requests = listOf(makeRequest(id = 1, receivedDateMillis = 1000L * MS_PER_SEC))
        // 1 intermediate hop on return path, with hop_start and snr_back set
        // → fullRouteBack = [src, hop, dest] → size 3 → returnHops = 1
        val results =
            listOf(
                makeResult(
                    requestId = 1,
                    receivedDateMillis = 1005L * MS_PER_SEC,
                    intermediateRoute = listOf(3),
                    intermediateRouteBack = listOf(3),
                    hopStart = 3,
                ),
            )

        val point = resolveTraceroutePoints(requests, results).first()

        assertEquals(1, point.forwardHops)
        assertEquals(1, point.returnHops)
    }
}
