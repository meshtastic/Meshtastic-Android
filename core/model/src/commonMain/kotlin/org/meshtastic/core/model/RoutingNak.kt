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

import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing

/** A routing NAK from node [from] carrying `Routing.Error` value [routingError] for our packet [requestId]. */
data class RoutingNak(val requestId: Int, val from: Int, val routingError: Int)

/** The routing NAK this packet carries, or null for any other packet, including a plain ACK. */
fun MeshPacket.routingNakOrNull(): RoutingNak? {
    val data = decoded?.takeIf { it.portnum == PortNum.ROUTING_APP && it.request_id != 0 }
    val error = data?.let { Routing.ADAPTER.decodeOrNull(it.payload)?.error_reason }
    return if (data == null || error == null || error == Routing.Error.NONE) {
        null
    } else {
        RoutingNak(requestId = data.request_id, from = from, routingError = error.value)
    }
}
