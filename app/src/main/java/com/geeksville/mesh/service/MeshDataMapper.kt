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
package com.geeksville.mesh.service

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshPacket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshDataMapper @Inject constructor(private val nodeManager: MeshNodeManager) {
    fun toNodeID(n: Int): String = if (n == DataPacket.NODENUM_BROADCAST) {
        DataPacket.ID_BROADCAST
    } else {
        nodeManager.nodeDBbyNodeNum[n]?.user?.id ?: DataPacket.nodeNumToDefaultId(n)
    }

    fun toDataPacket(packet: MeshPacket): DataPacket? {
        val decoded = packet.decoded ?: return null
        return DataPacket(
            from = toNodeID(packet.from),
            to = toNodeID(packet.to),
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
