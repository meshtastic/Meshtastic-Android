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
package org.meshtastic.feature.car.service

import org.koin.core.annotation.Factory

/**
 * Loads up to MAX_BATCH_SIZE unread messages on car session start for immediate display and MessagingStyle notification
 * posting.
 */
@Factory
class BatchMessageLoader {

    data class BatchResult(val messages: List<UnreadMessage>, val totalUnread: Int)

    data class UnreadMessage(
        val contactKey: String,
        val senderName: String,
        val text: String,
        val timestamp: Long,
        val channelIndex: Int,
    )

    fun loadUnreadBatch(allMessages: List<UnreadMessage>): BatchResult {
        val sorted = allMessages.sortedByDescending { it.timestamp }
        val batch = sorted.take(MAX_BATCH_SIZE)
        return BatchResult(messages = batch, totalUnread = allMessages.size)
    }

    companion object {
        const val MAX_BATCH_SIZE = 50
    }
}
