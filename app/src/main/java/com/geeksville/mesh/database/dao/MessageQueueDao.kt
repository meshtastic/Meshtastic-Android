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

package com.geeksville.mesh.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.geeksville.mesh.database.entity.QueuedMessage
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing queued messages that failed to send and need retry.
 * Supports the intelligent message queue system for automatic retry when
 * network conditions improve or target nodes become reachable.
 */
@Dao
interface MessageQueueDao {
    
    /**
     * Get all queued messages ordered by queued time (FIFO processing)
     */
    @Query("SELECT * FROM message_queue ORDER BY queued_time ASC")
    fun getAllQueued(): Flow<List<QueuedMessage>>
    
    /**
     * Get all queued messages ordered by queued time (FIFO processing)
     */
    @Query("SELECT * FROM message_queue ORDER BY queued_time ASC")
    suspend fun getAllQueuedList(): List<QueuedMessage>
    
    /**
     * Get queued messages for a specific destination
     */
    @Query("SELECT * FROM message_queue WHERE destination_id = :destId ORDER BY queued_time ASC")
    suspend fun getQueuedForDestination(destId: String): List<QueuedMessage>
    
    /**
     * Get queued messages that are ready for retry based on time constraints
     */
    @Query("""
        SELECT * FROM message_queue 
        WHERE attempt_count < max_retries 
        AND (last_attempt_time = 0 OR :currentTime - last_attempt_time >= :minBackoffTime)
        ORDER BY queued_time ASC
    """)
    suspend fun getReadyForRetry(currentTime: Long, minBackoffTime: Long): List<QueuedMessage>
    
    /**
     * Insert a new queued message
     */
    @Insert
    suspend fun insert(queuedMessage: QueuedMessage)
    
    /**
     * Update an existing queued message (e.g., after retry attempt)
     */
    @Update
    suspend fun update(queuedMessage: QueuedMessage)
    
    /**
     * Delete a specific queued message
     */
    @Delete
    suspend fun delete(queuedMessage: QueuedMessage)
    
    /**
     * Delete a queued message by UUID (after successful delivery)
     */
    @Query("DELETE FROM message_queue WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: Long)
    
    /**
     * Clean up expired messages that have exceeded max retries
     */
    @Query("DELETE FROM message_queue WHERE attempt_count >= max_retries")
    suspend fun cleanupExpiredMessages()
    
    /**
     * Clean up old messages that have been queued for too long
     */
    @Query("DELETE FROM message_queue WHERE :currentTime - queued_time > :maxAgeMillis")
    suspend fun cleanupOldMessages(currentTime: Long, maxAgeMillis: Long)
    
    /**
     * Get count of queued messages for UI display
     */
    @Query("SELECT COUNT(*) FROM message_queue")
    suspend fun getQueuedCount(): Int
    
    /**
     * Get count of queued messages for a specific destination
     */
    @Query("SELECT COUNT(*) FROM message_queue WHERE destination_id = :destId")
    suspend fun getQueuedCountForDestination(destId: String): Int
    
    /**
     * Clean up messages older than the specified time
     */
    @Query("DELETE FROM message_queue WHERE queued_time < :cutoffTime")
    suspend fun cleanupOldMessages(cutoffTime: Long)

    /**
     * Delete a message by its original packet ID
     */
    @Query("DELETE FROM message_queue WHERE original_packet_id = :packetId")
    suspend fun deleteByPacketId(packetId: String): Int
} 