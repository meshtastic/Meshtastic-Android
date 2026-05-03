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

import org.meshtastic.core.common.util.CommonParcelable
import org.meshtastic.core.common.util.CommonParcelize

@CommonParcelize
data class Contact(
    val contactKey: String,
    val shortName: String,
    val longName: String,
    val lastMessageTime: Long?,
    val lastMessageText: String?,
    val unreadCount: Int,
    val messageCount: Int,
    val isMuted: Boolean,
    val isUnmessageable: Boolean,
    val nodeColors: Pair<Int, Int>? = null,
) : CommonParcelable

data class ContactSettings(
    val contactKey: String,
    val muteUntil: Long = 0L,
    val lastReadMessageUuid: Long? = null,
    val lastReadMessageTimestamp: Long? = null,
    val filteringDisabled: Boolean = false,
    val isMuted: Boolean = false,
)
