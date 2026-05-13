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
import org.meshtastic.proto.User

data class Reaction(
    val replyId: Int,
    val user: User,
    val emoji: String,
    val timestamp: Long,
    val snr: Float,
    val rssi: Int,
    val hopsAway: Int,
    val packetId: Int = 0,
    val status: MessageStatus = MessageStatus.UNKNOWN,
    val routingError: Int = 0,
    val relays: Int = 0,
    val relayNode: Int? = null,
    val to: String? = null,
    val channel: Int = 0,
    val sfppHash: ByteString? = null,
)
