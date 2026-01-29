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

import org.meshtastic.proto.MeshProtos

val MeshProtos.MeshPacket.neighborInfo: MeshProtos.NeighborInfo?
    get() =
        if (hasDecoded() && decoded.portnumValue == 71) { // NEIGHBORINFO_APP_VALUE = 71
            runCatching { MeshProtos.NeighborInfo.parseFrom(decoded.payload) }.getOrNull()
        } else {
            null
        }

fun MeshProtos.NeighborInfo.getNeighborInfoResponse(
    getUser: (nodeNum: Int) -> String,
    header: String = "Neighbors:",
): String = buildString {
    append(header)
    append("\n\n")
    if (neighborsList.isEmpty()) {
        append("No neighbors reported.")
    } else {
        neighborsList.forEach { n ->
            append("• ")
            append(getUser(n.nodeId))
            append(" (SNR: ")
            append(n.snr)
            append(")\n")
        }
    }
}

fun MeshProtos.MeshPacket.getNeighborInfoResponse(
    getUser: (nodeNum: Int) -> String,
    header: String = "Neighbors:",
): String? = neighborInfo?.getNeighborInfoResponse(getUser, header)
