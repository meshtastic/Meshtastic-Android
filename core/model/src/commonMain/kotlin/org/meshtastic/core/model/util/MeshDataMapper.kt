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
@file:Suppress("MagicNumber")

package org.meshtastic.core.model.util

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshPacket

/**
 * Utility class to map [MeshPacket] protobufs to [DataPacket] domain models.
 *
 * This class is platform-agnostic and can be used in shared logic.
 */
open class MeshDataMapper(private val nodeIdLookup: NodeIdLookup) {

    /** Maps a [MeshPacket] to a [DataPacket], or returns null if the packet has no decoded data. */
    open fun toDataPacket(packet: MeshPacket): DataPacket? {
        val decoded = packet.decoded ?: return null
        return DataPacket(
            from = nodeIdLookup.toNodeID(packet.from),
            to = nodeIdLookup.toNodeID(packet.to),
            time = packet.rx_time * 1000L,
            id = packet.id,
            dataType = decoded.portnum.value,
            bytes = decoded.payload.toByteArray().toByteString(),
            hopLimit = packet.hop_limit,
            channel = if (packet.pki_encrypted == true) DataPacket.PKC_CHANNEL_INDEX else packet.channel,
            wantAck = packet.want_ack == true,
            hopStart = packet.hop_start,
            snr = packet.rx_snr,
            rssi = packet.rx_rssi,
            replyId = decoded.reply_id,
            relayNode = packet.relay_node,
            viaMqtt = packet.via_mqtt == true,
            emoji = decoded.emoji,
            transportMechanism = packet.transport_mechanism.value,
        )
    }
}
