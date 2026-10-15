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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.MeshLog
import org.meshtastic.core.repository.RadioSessionContext
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracerouteHandlerImplTest {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private val radio = FakeRadioInterfaceService(scope)
    private val serviceRepository = FakeServiceRepository()
    private val meshLogRepository = FakeMeshLogRepository()
    private val snapshotRepository = FakeTracerouteSnapshotRepository()
    private val session: RadioSessionContext

    init {
        radio.setDeviceAddress("tcp:test")
        radio.connect()
        session = checkNotNull(radio.activeSession.value)
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private fun newHandler() = TracerouteHandlerImpl(
        serviceStateWriter = serviceRepository,
        tracerouteSnapshotRepository = snapshotRepository,
        nodeRepository = FakeNodeRepository(),
        radioInterfaceService = radio,
        meshLogRepository = lazy { meshLogRepository },
        scope = scope.asServiceScope(),
    )
        .apply { renderResponse = { "route" } }

    private suspend fun TracerouteHandlerImpl.handleAndSettle(packet: MeshPacket) {
        handleTraceroute(packet, logUuid = "response-log", logInsertJob = null, session = session)
        withContext(Dispatchers.Default) { withTimeout(TIMEOUT_MS) { job.children.toList().joinAll() } }
    }

    private fun response(requestId: Int, from: Int = REMOTE) = MeshPacket.Builder()
        .also { wb ->
            wb.from = from
            wb.to = LOCAL
            wb.hop_start = 3
            wb.decoded =
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
                                        rd.snr_towards = listOf(SNR, SNR)
                                        rd.snr_back = listOf(SNR, SNR)
                                    }
                                    .build(),
                            )
                                .toByteString()
                    }
                    .build()
        }
        .build()

    private fun requestLog(id: Int) = MeshLog(
        uuid = "request-$id",
        message_type = "Packet",
        received_date = 1_000L,
        raw_message = "",
        fromNum = MeshLog.NODE_NUM_LOCAL,
        portNum = PortNum.TRACEROUTE_APP.value,
        fromRadio =
        FromRadio.Builder()
            .also { wb ->
                wb.packet =
                    MeshPacket.Builder()
                        .also { p ->
                            p.id = id
                            p.to = REMOTE
                            p.decoded =
                                Data.Builder()
                                    .also { d ->
                                        d.portnum = PortNum.TRACEROUTE_APP
                                        d.want_response = true
                                        d.dest = REMOTE
                                    }
                                    .build()
                        }
                        .build()
            }
            .build(),
    )

    @Test
    fun `a response to a request this process sent raises the result alert`() = runTest {
        val handler = newHandler()
        handler.recordStartTime(REQUEST_ID, REMOTE)

        handler.handleAndSettle(response(REQUEST_ID))

        val alert = assertNotNull(serviceRepository.tracerouteResponse.value)
        assertEquals(REQUEST_ID, alert.requestId)
    }

    @Test
    fun `a response from a node other than the destination raises no alert and keeps the timer`() = runTest {
        val handler = newHandler()
        handler.recordStartTime(REQUEST_ID, REMOTE)

        handler.handleAndSettle(response(REQUEST_ID, from = RELAY))
        assertNull(serviceRepository.tracerouteResponse.value)

        handler.handleAndSettle(response(REQUEST_ID))
        val alert = assertNotNull(serviceRepository.tracerouteResponse.value)
        assertTrue(alert.message.contains("Duration: "), "the intended request's duration was lost: ${alert.message}")
    }

    @Test
    fun `a response nobody in this app requested raises no alert`() = runTest {
        val handler = newHandler()

        handler.handleAndSettle(response(REQUEST_ID))

        assertNull(serviceRepository.tracerouteResponse.value)
    }

    @Test
    fun `an observed response still records the map snapshot`() = runTest {
        val handler = newHandler()

        handler.handleAndSettle(response(REQUEST_ID))

        assertEquals(REQUEST_ID, snapshotRepository.lastRequestId("response-log"))
    }

    @Test
    fun `after a restart a response whose request row exists still raises the alert`() = runTest {
        // The request was sent and logged by a previous process, so this handler's timer never saw it.
        meshLogRepository.setLogs(listOf(requestLog(REQUEST_ID)))
        val handler = newHandler()

        handler.handleAndSettle(response(REQUEST_ID))

        assertEquals(REQUEST_ID, serviceRepository.tracerouteResponse.value?.requestId)
    }

    @Test
    fun `after a restart a response matching no request row raises no alert`() = runTest {
        meshLogRepository.setLogs(listOf(requestLog(OTHER_REQUEST_ID)))
        val handler = newHandler()

        handler.handleAndSettle(response(REQUEST_ID))

        assertNull(serviceRepository.tracerouteResponse.value)
    }

    private companion object {
        const val LOCAL = 1
        const val REMOTE = 2
        const val RELAY = 3
        const val SNR = 20
        const val REQUEST_ID = 4242
        const val OTHER_REQUEST_ID = 77
        const val TIMEOUT_MS = 10_000L
    }
}
