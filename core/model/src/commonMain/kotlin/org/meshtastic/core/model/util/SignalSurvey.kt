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
package org.meshtastic.core.model.util

import org.meshtastic.proto.MeshPacket

/** Firmware queues packets while no client is connected, so an older reception was heard somewhere else. */
const val SURVEY_MAX_PACKET_AGE_SECONDS = 30

/** Whether the connected radio heard this packet over LoRa within the last [SURVEY_MAX_PACKET_AGE_SECONDS]. */
fun MeshPacket.isLiveLoraReception(nowSeconds: Long): Boolean {
    val heardAt = rxTimeOrNull() ?: return false
    val live = nowSeconds - heardAt <= SURVEY_MAX_PACKET_AGE_SECONDS
    return live && rx_rssi != null && via_mqtt != true && isLora()
}

/** Whether the sender itself was heard, rather than a relay's copy. */
fun MeshPacket.isDirectReception(): Boolean = hop_start == hop_limit
