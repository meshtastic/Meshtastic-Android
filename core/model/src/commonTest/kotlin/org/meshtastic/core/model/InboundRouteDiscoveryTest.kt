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
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RouteDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InboundRouteDiscoveryTest {

    private fun packet(wantResponse: Boolean, requestId: Int = 0) = MeshPacket.Builder()
        .also { p ->
            p.from = TRACER
            p.to = US
            p.decoded =
                Data.Builder()
                    .also { d ->
                        d.portnum = PortNum.TRACEROUTE_APP
                        d.want_response = wantResponse
                        d.request_id = requestId
                        d.payload =
                            RouteDiscovery.ADAPTER.encode(
                                RouteDiscovery.Builder()
                                    .also { rd ->
                                        rd.route = listOf(RELAY, NodeAddress.NODENUM_BROADCAST)
                                        rd.snr_towards = listOf(SNR, SNR, SNR)
                                    }
                                    .build(),
                            )
                                .toByteString()
                    }
                    .build()
        }
        .build()

    @Test
    fun `a request to us yields its originator then the relays then us`() {
        val inbound = assertNotNull(packet(wantResponse = true).inboundRouteDiscovery)

        assertEquals(listOf(TRACER, RELAY, NodeAddress.NODENUM_BROADCAST, US), inbound.route)
        assertEquals(listOf(SNR, SNR, SNR), inbound.snr_towards)
        assertEquals(emptyList(), inbound.route_back)
    }

    @Test
    fun `a request is still not a full route discovery`() {
        assertNull(packet(wantResponse = true).fullRouteDiscovery)
    }

    @Test
    fun `a response has no inbound route`() {
        assertNull(packet(wantResponse = false, requestId = 7).inboundRouteDiscovery)
    }

    private companion object {
        const val TRACER = 10
        const val RELAY = 20
        const val US = 30
        const val SNR = 12
    }
}
