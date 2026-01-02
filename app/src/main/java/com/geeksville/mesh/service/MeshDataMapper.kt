/*
 * Copyright (c) 2025 Meshtastic LLC
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

import org.meshtastic.core.model.DataPacket
import org.meshtastic.proto.MeshProtos.MeshPacket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeshDataMapper @Inject constructor(private val nodeManager: MeshNodeManager) {
    fun toNodeID(n: Int): String = if (n == DataPacket.NODENUM_BROADCAST) {
        DataPacket.ID_BROADCAST
    } else {
        nodeManager.nodeDBbyNodeNum[n]?.user?.id ?: DataPacket.nodeNumToDefaultId(n)
    }

    fun toDataPacket(packet: MeshPacket): DataPacket? = if (!packet.hasDecoded()) {
        null
    } else {
        val data = packet.decoded
        DataPacket(
            from = toNodeID(packet.from),
            to = toNodeID(packet.to),
            time = packet.rxTime * 1000L,
            id = packet.id,
            dataType = data.portnumValue,
            bytes = data.payload.toByteArray(),
            hopLimit = packet.hopLimit,
            channel = if (packet.pkiEncrypted) DataPacket.PKC_CHANNEL_INDEX else packet.channel,
            wantAck = packet.wantAck,
            hopStart = packet.hopStart,
            snr = packet.rxSnr,
            rssi = packet.rxRssi,
            replyId = data.replyId,
            relayNode = packet.relayNode,
            viaMqtt = packet.viaMqtt,
        )
    }
}
