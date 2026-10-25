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
package org.meshtastic.feature.node.domain.usecase

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeFirmwareReleaseRepository
import org.meshtastic.core.testing.FakeLocaleUnitsProvider
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.feature.node.detail.NodeRequestActions
import org.meshtastic.feature.node.metrics.TracerouteKind
import org.meshtastic.feature.node.metrics.resolveTraceroutePoints
import org.meshtastic.feature.node.model.LogsType
import org.meshtastic.feature.node.model.MetricsState
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Drives [CommonGetNodeDetailsUseCase] over the MeshLog rows the app stores for a node's traceroutes. */
class TracerouteHistoryUseCaseTest {

    private val fakeNodeRepository = FakeNodeRepository().apply { setOurNode(Node(num = OUR_NODE)) }

    // The fake maps every node to the local log id; the real repository does that only for our own node.
    private val nodeRepository =
        object : NodeRepository by fakeNodeRepository {
            override fun effectiveLogNodeId(nodeNum: Int): Flow<Int> =
                flowOf(if (nodeNum == OUR_NODE) MeshLog.NODE_NUM_LOCAL else nodeNum)
        }
    private val meshLogRepository = FakeMeshLogRepository()
    private val nodeRequestActions =
        mock<NodeRequestActions>(MockMode.autofill).also {
            every { it.lastTracerouteTime } returns MutableStateFlow(null)
            every { it.lastRequestNeighborTimes } returns MutableStateFlow(emptyMap())
        }

    private val useCase =
        CommonGetNodeDetailsUseCase(
            nodeRepository = nodeRepository,
            meshLogRepository = meshLogRepository,
            radioConfigRepository = FakeRadioConfigRepository(),
            deviceHardwareRepository = FakeDeviceHardwareRepository(),
            deviceLinkRepository = mock(MockMode.autofill),
            deviceModelRepository = mock(MockMode.autofill),
            firmwareReleaseRepository = FakeFirmwareReleaseRepository(),
            nodeRequestActions = nodeRequestActions,
            localeUnitsProvider = FakeLocaleUnitsProvider(),
        )

    /** The first state for [nodeNum] whose logs have landed; earlier ones carry the flows' empty placeholders. */
    private suspend fun stateFor(nodeNum: Int, withRequests: Boolean = false) = useCase(nodeNum).first {
        val metrics = it.metricsState
        metrics.node?.num == nodeNum &&
            metrics.tracerouteResults.isNotEmpty() &&
            (!withRequests || metrics.tracerouteRequests.isNotEmpty())
    }

    private suspend fun metricsFor(nodeNum: Int, withRequests: Boolean = false): MetricsState =
        stateFor(nodeNum, withRequests).metricsState

    private fun MetricsState.history() =
        resolveTraceroutePoints(tracerouteRequests, tracerouteResults, tracerouteObserved, tracerouteTracedBy)

    @Test
    fun `an unrequested response appears in the responder history as observed with no round trip`() = runTest {
        meshLogRepository.setLogs(listOf(response(from = NODE_X, requestId = 4242, receivedDate = 5_000L)))

        val metrics = metricsFor(NODE_X)
        val entry = metrics.history().single()

        assertEquals(TracerouteKind.OBSERVED, entry.kind)
        assertNull(entry.roundTripSeconds)
        assertEquals(1, entry.forwardHops)
        assertTrue(metrics.hasTracerouteLogs())
    }

    @Test
    fun `a requested response stays requested with its round trip`() = runTest {
        meshLogRepository.setLogs(
            listOf(
                request(id = 4242, to = NODE_X, receivedDate = 1_000L),
                response(from = NODE_X, requestId = 4242, receivedDate = 3_500L),
            ),
        )

        val entry = metricsFor(NODE_X, withRequests = true).history().single()

        assertEquals(TracerouteKind.REQUESTED, entry.kind)
        assertEquals(2.5, assertNotNull(entry.roundTripSeconds), 0.01)
    }

    @Test
    fun `a trace from the node to us appears in its history as traced by`() = runTest {
        meshLogRepository.setLogs(listOf(inboundRequest(from = NODE_X, to = OUR_NODE, receivedDate = 2_000L)))

        val state = stateFor(NODE_X)
        val entry = state.metricsState.history().single()

        assertEquals(TracerouteKind.TRACED_BY, entry.kind)
        assertEquals(listOf(NODE_X, RELAY, OUR_NODE), assertNotNull(entry.route).route)
        assertEquals(emptyList(), assertNotNull(entry.route).route_back)
        assertNull(entry.roundTripSeconds)
        assertTrue(LogsType.TRACEROUTE in state.availableLogs)
    }

    @Test
    fun `a trace from the node addressed to someone else is ignored`() = runTest {
        meshLogRepository.setLogs(listOf(inboundRequest(from = NODE_X, to = OTHER_NODE, receivedDate = 2_000L)))

        val metrics = metricsFor(NODE_X)

        assertEquals(emptyList(), metrics.history())
        assertFalse(metrics.hasTracerouteLogs())
    }

    @Test
    fun `our own history gathers the traces every node ran to us`() = runTest {
        meshLogRepository.setLogs(
            listOf(
                inboundRequest(from = NODE_X, to = OUR_NODE, receivedDate = 2_000L),
                inboundRequest(from = OTHER_NODE, to = OUR_NODE, receivedDate = 3_000L),
                inboundRequest(from = NODE_X, to = OTHER_NODE, receivedDate = 4_000L),
            ),
        )

        val state =
            useCase(OUR_NODE).first {
                it.metricsState.node?.num == OUR_NODE && it.metricsState.tracerouteTracedBy.isNotEmpty()
            }
        val history = state.metricsState.history()

        assertTrue(history.all { it.kind == TracerouteKind.TRACED_BY })
        assertEquals(listOf(OTHER_NODE, NODE_X), history.map { it.result?.fromRadio?.packet?.from })
        assertTrue(LogsType.TRACEROUTE in state.availableLogs)
    }

    private fun log(uuid: String, fromNum: Int, receivedDate: Long, packet: MeshPacket) = MeshLog(
        uuid = uuid,
        message_type = "Packet",
        received_date = receivedDate,
        raw_message = "",
        fromNum = fromNum,
        portNum = PortNum.TRACEROUTE_APP.value,
        fromRadio = FromRadio.Builder().also { it.packet = packet }.build(),
    )

    private fun request(id: Int, to: Int, receivedDate: Long) = log(
        uuid = "request-$id",
        fromNum = MeshLog.NODE_NUM_LOCAL,
        receivedDate = receivedDate,
        packet =
        MeshPacket.Builder()
            .also { p ->
                p.id = id
                p.to = to
                p.decoded =
                    Data.Builder()
                        .also { d ->
                            d.portnum = PortNum.TRACEROUTE_APP
                            d.want_response = true
                            d.dest = to
                        }
                        .build()
            }
            .build(),
    )

    private fun response(from: Int, requestId: Int, receivedDate: Long) = log(
        uuid = "response-$requestId",
        fromNum = from,
        receivedDate = receivedDate,
        packet =
        MeshPacket.Builder()
            .also { p ->
                p.from = from
                p.to = OUR_NODE
                p.hop_start = 3
                p.decoded =
                    Data.Builder()
                        .also { d ->
                            d.portnum = PortNum.TRACEROUTE_APP
                            d.request_id = requestId
                            d.payload =
                                RouteDiscovery.ADAPTER.encode(
                                    RouteDiscovery.Builder()
                                        .also { rd ->
                                            rd.route = listOf(RELAY)
                                            rd.route_back = listOf(RELAY)
                                            rd.snr_back = listOf(SNR, SNR)
                                        }
                                        .build(),
                                )
                                    .toByteString()
                        }
                        .build()
            }
            .build(),
    )

    private fun inboundRequest(from: Int, to: Int, receivedDate: Long) = log(
        uuid = "inbound-$receivedDate",
        fromNum = from,
        receivedDate = receivedDate,
        packet =
        MeshPacket.Builder()
            .also { p ->
                p.from = from
                p.to = to
                p.decoded =
                    Data.Builder()
                        .also { d ->
                            d.portnum = PortNum.TRACEROUTE_APP
                            d.want_response = true
                            d.dest = to
                            d.payload =
                                RouteDiscovery.ADAPTER.encode(
                                    RouteDiscovery.Builder()
                                        .also { rd ->
                                            rd.route = listOf(RELAY)
                                            rd.snr_towards = listOf(SNR, SNR)
                                        }
                                        .build(),
                                )
                                    .toByteString()
                        }
                        .build()
            }
            .build(),
    )

    private companion object {
        const val OUR_NODE = 1
        const val NODE_X = 2
        const val RELAY = 3
        const val OTHER_NODE = 9
        const val SNR = 24
    }
}
