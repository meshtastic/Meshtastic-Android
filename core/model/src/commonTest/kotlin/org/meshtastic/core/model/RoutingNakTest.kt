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

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoutingNakTest {
    private fun packet(portnum: PortNum, requestId: Int, payload: ByteString): MeshPacket {
        val data =
            Data.Builder()
                .also { wb ->
                    wb.portnum = portnum
                    wb.request_id = requestId
                    wb.payload = payload
                }
                .build()
        return MeshPacket.Builder()
            .also { wb ->
                wb.from = 77
                wb.decoded = data
            }
            .build()
    }

    private fun routing(error: Routing.Error) =
        Routing.Builder().also { wb -> wb.error_reason = error }.build().encode().toByteString()

    @Test
    fun `a routing error is read with its request and sender`() {
        val nak = packet(PortNum.ROUTING_APP, 9, routing(Routing.Error.NO_RESPONSE)).routingNakOrNull()

        assertEquals(RoutingNak(requestId = 9, from = 77, routingError = Routing.Error.NO_RESPONSE.value), nak)
    }

    @Test
    fun `an ack is not a nak`() {
        assertNull(packet(PortNum.ROUTING_APP, 9, routing(Routing.Error.NONE)).routingNakOrNull())
    }

    @Test
    fun `a packet answering no request is not a nak`() {
        assertNull(packet(PortNum.ROUTING_APP, 0, routing(Routing.Error.NO_RESPONSE)).routingNakOrNull())
    }

    @Test
    fun `another port is not a nak`() {
        assertNull(packet(PortNum.TEXT_MESSAGE_APP, 9, routing(Routing.Error.NO_RESPONSE)).routingNakOrNull())
    }
}
