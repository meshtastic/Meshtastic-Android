/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import co.touchlab.kermit.Logger
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.NeighborInfo
import org.meshtastic.proto.PortNum

val MeshPacket.neighborInfo: NeighborInfo?
    get() {
        val decoded = this.decoded
        return if (decoded != null && decoded.portnum == PortNum.NEIGHBORINFO_APP) {
            NeighborInfo.ADAPTER.decodeOrNull(decoded.payload, Logger)
        } else {
            null
        }
    }

fun NeighborInfo.getNeighborInfoResponse(getUser: (nodeNum: Int) -> String, header: String = "Neighbors:"): String =
    buildString {
        append(header)
        append("\n\n")
        if (neighbors.isEmpty()) {
            append("No neighbors reported.")
        } else {
            neighbors.forEach { n ->
                append("• ")
                append(getUser(n.node_id))
                append(" (SNR: ")
                append(n.snr)
                append(")\n")
            }
        }
    }

fun MeshPacket.getNeighborInfoResponse(getUser: (nodeNum: Int) -> String, header: String = "Neighbors:"): String? =
    neighborInfo?.getNeighborInfoResponse(getUser, header)
