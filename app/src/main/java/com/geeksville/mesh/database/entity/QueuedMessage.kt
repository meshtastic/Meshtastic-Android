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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a message that failed to send and is queued for retry.
 * This supports the intelligent message queue system that automatically retries
 * message delivery when network conditions improve or target nodes become reachable.
 */
@Entity(tableName = "message_queue")
data class QueuedMessage(
    @PrimaryKey val uuid: Long,
    
    @ColumnInfo(name = "original_packet_id")
    val originalPacketId: Int,
    
    @ColumnInfo(name = "destination_id")
    val destinationId: String,
    
    @ColumnInfo(name = "message_text")
    val messageText: String,
    
    @ColumnInfo(name = "channel_index")
    val channelIndex: Int,
    
    @ColumnInfo(name = "queued_time")
    val queuedTime: Long,
    
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,
    
    @ColumnInfo(name = "last_attempt_time")
    val lastAttemptTime: Long = 0,
    
    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 5, // Avoid spamming the network with infinite retries
    
    @ColumnInfo(name = "routing_error")
    val routingError: Int = 0 // Store the original routing error that caused queuing
) 