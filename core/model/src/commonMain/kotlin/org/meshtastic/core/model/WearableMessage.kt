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

import kotlinx.serialization.Serializable

@Serializable
data class WearableMessage(
    val uuid: Long,
    val contactKey: String,
    val senderName: String,
    val senderShortName: String,
    val text: String,
    val isMe: Boolean,
    val timestamp: Long,
    val address: String? = null,
    val channelIndex: Int = 0,
    val status: MessageStatus = MessageStatus.RECEIVED,
)

@Serializable
data class WearableChannel(
    val index: Int,
    val name: String,
    val contactKey: String,
)

@Serializable
data class WearableReply(
    val address: String?,
    val channelIndex: Int,
    val text: String,
)
