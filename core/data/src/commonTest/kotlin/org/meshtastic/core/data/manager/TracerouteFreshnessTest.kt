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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.util.nowSeconds
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.FromRadioPacketHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.core.testing.FakeTracerouteSnapshotRepository
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A traceroute response names every relay on its route, but only its sender was heard. Drives a real
 * [MeshMessageProcessorImpl] and [NodeManagerImpl] with traceroute packets routed to a real [TracerouteHandlerImpl].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TracerouteFreshnessTest {

    private val testScope = TestScope(StandardTestDispatcher())
    private val handlerJob = SupervisorJob()
    private val handlerScope = CoroutineScope(Dispatchers.Default + handlerJob)

    @AfterTest
    fun tearDown() {
        handlerScope.cancel()
    }

    private val relayBefore = Node(num = RELAY, lastHeard = RELAY_LAST_HEARD, hopsAway = RELAY_HOPS, snr = RELAY_SNR)
    private val returnRelayBefore =
        Node(num = RETURN_RELAY, lastHeard = RELAY_LAST_HEARD, hopsAway = RELAY_HOPS, snr = RELAY_SNR)

    private fun tracerouteResponse() = MeshPacket.Builder()
        .also { wb ->
            wb.id = 99
            wb.from = RESPONDER
            wb.to = MY_NODE
            wb.hop_start = 3
            wb.hop_limit = 1
            wb.rx_time = nowSeconds.toInt()
            wb.decoded =
                Data.Builder()
                    .also { d ->
                        d.portnum = PortNum.TRACEROUTE_APP
                        d.request_id = 1234
                        d.payload =
                            RouteDiscovery.ADAPTER.encode(
                                RouteDiscovery.Builder()
                                    .also { rd ->
                                        rd.route = listOf(RELAY)
                                        rd.route_back = listOf(RETURN_RELAY)
                                        rd.snr_towards = listOf(40, 40)
                                        rd.snr_back = listOf(40, 40)
                                    }
                                    .build(),
                            )
                                .toByteString()
                    }
                    .build()
        }
        .build()

    @Test
    fun `a traceroute response refreshes only its sender and never the relays on its route`() = testScope.runTest {
        val radio = FakeRadioInterfaceService(backgroundScope)
        radio.setDeviceAddress("tcp:test")
        radio.connect()
        val session = checkNotNull(radio.activeSession.value)
        val nodeRepository = FakeNodeRepository()
        val nodeManager =
            NodeManagerImpl(
                nodeRepository,
                mock<MeshNotificationManager>(MockMode.autofill),
                radio,
                backgroundScope.asServiceScope(),
            )
                .apply {
                    setNodeDbReady(true)
                    setAllowNodeDbWrites(true)
                }
        nodeManager.updateNode(RELAY, channel = 0) { relayBefore }
        nodeManager.updateNode(RETURN_RELAY, channel = 0) { returnRelayBefore }
        nodeManager.updateNode(RESPONDER, channel = 0) { it.copy(lastHeard = RELAY_LAST_HEARD, hopsAway = 5) }

        val tracerouteHandler =
            TracerouteHandlerImpl(
                serviceStateWriter = FakeServiceRepository(),
                tracerouteSnapshotRepository = FakeTracerouteSnapshotRepository(),
                nodeRepository = nodeRepository,
                radioInterfaceService = radio,
                meshLogRepository = lazy { FakeMeshLogRepository() },
                scope = handlerScope.asServiceScope(),
            )
                .apply { renderResponse = { "route" } }
        val dataHandler = mock<MeshDataHandler>(MockMode.autofill)
        every { dataHandler.handleReceivedData(any(), any(), any(), any(), any()) } calls
            { args ->
                tracerouteHandler.handleTraceroute(
                    packet = args.arg<MeshPacket>(0),
                    logUuid = args.arg<String?>(3),
                    logInsertJob = args.arg<Job?>(4),
                    session = args.arg<RadioSessionContext>(2),
                )
            }
        val processor =
            MeshMessageProcessorImpl(
                nodeManager = nodeManager,
                serviceStateWriter = FakeServiceRepository(),
                meshLogRepository = lazy { mock<MeshLogRepository>(MockMode.autofill) },
                dataHandler = lazy { dataHandler },
                fromRadioDispatcher = mock<FromRadioPacketHandler>(MockMode.autofill),
                radioInterfaceService = radio,
                scope = backgroundScope.asServiceScope(),
            )

        val frame = FromRadio.Builder().also { wb -> wb.packet = tracerouteResponse() }.build().encode()
        processor.handleFromRadio(ReceivedRadioFrame(frame.toByteString(), session), MY_NODE)
        runCurrent()
        withContext(Dispatchers.Default) { withTimeout(TIMEOUT_MS) { handlerJob.children.toList().joinAll() } }
        runCurrent()

        val nodes = nodeManager.nodeDBbyNodeNum
        for (before in listOf(relayBefore, returnRelayBefore)) {
            val after = nodes.getValue(before.num)
            assertEquals(before.lastHeard, after.lastHeard, "lastHeard of relay ${before.num}")
            assertEquals(before.hopsAway, after.hopsAway, "hopsAway of relay ${before.num}")
            assertEquals(before.snr, after.snr, "snr of relay ${before.num}")
        }
        val responder = nodes.getValue(RESPONDER)
        assertNotEquals(RELAY_LAST_HEARD, responder.lastHeard, "the sender itself was heard")
        assertEquals(2, responder.hopsAway)
    }

    private companion object {
        const val MY_NODE = 1
        const val RESPONDER = 2
        const val RELAY = 3
        const val RETURN_RELAY = 4
        const val RELAY_LAST_HEARD = 1_000
        const val RELAY_HOPS = 1
        const val RELAY_SNR = 6.5f
        const val TIMEOUT_MS = 10_000L
    }
}
