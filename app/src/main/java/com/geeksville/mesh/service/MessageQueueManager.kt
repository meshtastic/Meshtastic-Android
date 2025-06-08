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

import android.content.SharedPreferences
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.Portnums
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.dao.MessageQueueDao
import com.geeksville.mesh.database.entity.QueuedMessage
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Random

/**
 * Manages the message queue for failed deliveries.
 * Provides intelligent retry logic when network conditions improve or target nodes become reachable.
 * 
 * Part of the MVP message queue feature as outlined in MessageQueue.md.
 */
@Singleton
class MessageQueueManager @Inject constructor(
    private val queueDao: MessageQueueDao,
    private val preferences: SharedPreferences
) : Logging {
    
    private val retryStrategy = RetryStrategy()
    
    companion object {
        const val PREF_MESSAGE_QUEUE_ENABLED = "message_queue_enabled"
        private const val MAX_QUEUE_SIZE = 100 // Prevent queue overflow
    }
    
    /**
     * Check if message queuing is enabled in settings
     */
    private fun isQueueEnabled(): Boolean {
        return preferences.getBoolean(PREF_MESSAGE_QUEUE_ENABLED, false)
    }
    
    /**
     * Check if a message should be enqueued without actually enqueueing it.
     * Used to determine the appropriate status before setting ERROR.
     */
    suspend fun shouldEnqueueMessage(dataPacket: DataPacket, routingError: Int): Boolean {
        if (!isQueueEnabled()) {
            return false
        }
        
        // Only queue text messages for MVP
        if (dataPacket.text == null) {
            return false
        }
        
        // Check if queue has space (don't queue if full and can't be cleaned)
        val currentQueueSize = queueDao.getQueuedCount()
        if (currentQueueSize >= MAX_QUEUE_SIZE) {
            // We could potentially clean up, but for this check we'll be conservative
            // and assume the queue is full. The actual enqueue will try cleanup.
            return false
        }
        
        return true
    }
    
    /**
     * Add a failed message to the queue for automatic retry.
     * Only queues if feature is enabled and it's a text message.
     */
    suspend fun enqueueMessage(dataPacket: DataPacket, routingError: Int): Boolean {
        if (!isQueueEnabled()) {
            debug("Message queue disabled, not enqueueing")
            return false
        }
        
        // Only queue text messages for MVP
        if (dataPacket.text == null) {
            debug("Not enqueueing non-text message")
            return false
        }
        
        // Check queue size to prevent overflow
        val currentQueueSize = queueDao.getQueuedCount()
        if (currentQueueSize >= MAX_QUEUE_SIZE) {
            warn("Queue is full ($currentQueueSize), cleaning up old messages")
            cleanupQueue()
            
            // Check again after cleanup
            if (queueDao.getQueuedCount() >= MAX_QUEUE_SIZE) {
                errormsg("Queue still full after cleanup, dropping message")
                return false
            }
        }
        
        val queuedMessage = QueuedMessage(
            uuid = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE, // Ensure positive
            originalPacketId = dataPacket.id,
            destinationId = dataPacket.to ?: "",
            messageText = dataPacket.text!!,
            channelIndex = dataPacket.channel,
            queuedTime = System.currentTimeMillis(),
            attemptCount = 0,
            lastAttemptTime = 0,
            routingError = routingError
        )
        
        try {
            queueDao.insert(queuedMessage)
            info("Enqueued message ${dataPacket.id} for retry to ${dataPacket.to}")
            return true
        } catch (ex: Exception) {
            errormsg("Failed to enqueue message", ex)
            return false
        }
    }

    /**
     * Process all queued messages that are ready for retry
     */
    suspend fun processAllQueued(): List<QueuedMessage> {
        if (!isQueueEnabled()) return emptyList()
        
        try {
            val allQueued = queueDao.getAllQueuedList()
            val currentTime = System.currentTimeMillis()
            val readyForRetry = mutableListOf<QueuedMessage>()
            
            for (queuedMessage in allQueued) {
                if (retryStrategy.shouldRetry(queuedMessage, currentTime)) {
                    readyForRetry.add(queuedMessage)
                }
            }
            
            debug("Found ${readyForRetry.size} messages ready for retry out of ${allQueued.size} total queued")
            return readyForRetry
        } catch (ex: Exception) {
            errormsg("Error processing all queued messages", ex)
            return emptyList()
        }
    }
    
    /**
     * Process all queued messages immediately when connection is re-established,
     * bypassing normal backoff timing
     */
    suspend fun processAllQueuedImmediately(): List<QueuedMessage> {
        if (!isQueueEnabled()) return emptyList()
        
        try {
            val allQueued = queueDao.getAllQueuedList()
            val currentTime = System.currentTimeMillis()
            val readyForRetry = mutableListOf<QueuedMessage>()
            
            for (queuedMessage in allQueued) {
                if (retryStrategy.shouldRetryImmediately(queuedMessage, currentTime)) {
                    readyForRetry.add(queuedMessage)
                }
            }
            
            debug("Found ${readyForRetry.size} messages ready for immediate retry out of ${allQueued.size} total queued")
            return readyForRetry
        } catch (ex: Exception) {
            errormsg("Error processing all queued messages for immediate retry", ex)
            return emptyList()
        }
    }
    
    /**
     * Process queued messages for a specific destination
     */
    suspend fun processQueueForDestination(destinationId: String): List<QueuedMessage> {
        if (!isQueueEnabled()) return emptyList()
        
        try {
            val queuedForDest = queueDao.getQueuedForDestination(destinationId)
            val currentTime = System.currentTimeMillis()
            val readyForRetry = mutableListOf<QueuedMessage>()
            
            for (queuedMessage in queuedForDest) {
                // Use immediate retry logic when processing for a specific destination
                // This bypasses backoff timing since we detected the node is reachable
                if (retryStrategy.shouldRetryImmediately(queuedMessage, currentTime)) {
                    readyForRetry.add(queuedMessage)
                }
            }
            
            debug("Found ${readyForRetry.size} messages ready for immediate retry for destination $destinationId")
            return readyForRetry
        } catch (ex: Exception) {
            errormsg("Error processing queued messages for destination $destinationId", ex)
            return emptyList()
        }
    }
    
    /**
     * Update message after a retry attempt
     */
    suspend fun updateAfterRetry(queuedMessage: QueuedMessage, wasSuccessful: Boolean) {
        try {
            if (wasSuccessful) {
                // Remove from queue on successful delivery
                queueDao.deleteByUuid(queuedMessage.uuid)
                info("Removed successfully delivered message ${queuedMessage.originalPacketId} from queue")
            } else {
                // Update attempt count and last attempt time
                val updatedMessage = queuedMessage.copy(
                    attemptCount = queuedMessage.attemptCount + 1,
                    lastAttemptTime = System.currentTimeMillis()
                )
                
                if (updatedMessage.attemptCount >= updatedMessage.maxRetries) {
                    // Remove from queue after max retries
                    queueDao.deleteByUuid(queuedMessage.uuid)
                    warn("Removed message ${queuedMessage.originalPacketId} from queue after ${updatedMessage.attemptCount} failed attempts")
                } else {
                    // Update the queue entry
                    queueDao.update(updatedMessage)
                    debug("Updated retry count for message ${queuedMessage.originalPacketId}: ${updatedMessage.attemptCount}/${updatedMessage.maxRetries}")
                }
            }
        } catch (ex: Exception) {
            errormsg("Error updating queued message ${queuedMessage.uuid} after retry", ex)
        }
    }
    
    /**
     * Remove message from queue after successful delivery
     */
    suspend fun removeFromQueue(uuid: Long) {
        try {
            queueDao.deleteByUuid(uuid)
            debug("Removed message $uuid from queue")
        } catch (ex: Exception) {
            errormsg("Failed to remove message from queue", ex)
        }
    }

    /**
     * Remove a message from queue by its original packet ID
     */
    suspend fun removeFromQueue(packetId: Int): Boolean {
        return try {
            val deletedCount = queueDao.deleteByPacketId(packetId)
            if (deletedCount > 0) {
                debug("Removed message with packet ID $packetId from queue")
                true
            } else {
                debug("No message found with packet ID $packetId in queue")
                false
            }
        } catch (ex: Exception) {
            errormsg("Failed to remove message with packet ID $packetId from queue", ex)
            false
        }
    }
    
    /**
     * Get queued messages for UI display
     */
    fun getQueuedMessages(): Flow<List<QueuedMessage>> {
        return queueDao.getAllQueued()
    }
    
    /**
     * Get count of queued messages
     */
    suspend fun getQueuedCount(): Int {
        return queueDao.getQueuedCount()
    }
    
    /**
     * Clean up old and expired messages from the queue
     */
    suspend fun cleanupQueue() {
        try {
            // Remove messages that have exceeded max retry attempts
            queueDao.cleanupExpiredMessages()
            
            // Remove messages older than 7 days
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            queueDao.cleanupOldMessages(sevenDaysAgo)
            
            // Ensure queue doesn't exceed 100 messages (keep newest)
            val allMessages = queueDao.getAllQueuedList()
            if (allMessages.size > 100) {
                val messagesToDelete = allMessages.take(allMessages.size - 100)
                for (message in messagesToDelete) {
                    queueDao.deleteByUuid(message.uuid)
                }
                info("Cleaned up ${messagesToDelete.size} old messages to maintain queue size limit")
            }
            
            debug("Queue cleanup completed")
        } catch (ex: Exception) {
            errormsg("Error during queue cleanup", ex)
        }
    }
    
    /**
     * Get queue statistics for monitoring
     */
    suspend fun getQueueStats(): QueueStats {
        return try {
            val allMessages = queueDao.getAllQueuedList()
            val currentTime = System.currentTimeMillis()
            
            QueueStats(
                totalQueued = allMessages.size,
                readyForRetry = allMessages.count { retryStrategy.shouldRetry(it, currentTime) },
                averageRetryCount = if (allMessages.isEmpty()) 0.0 else allMessages.map { it.attemptCount }.average(),
                oldestMessageAge = allMessages.minOfOrNull { currentTime - it.queuedTime } ?: 0
            )
        } catch (ex: Exception) {
            errormsg("Error getting queue stats", ex)
            QueueStats(0, 0, 0.0, 0)
        }
    }

    /**
     * Queue statistics data class
     */
    data class QueueStats(
        val totalQueued: Int,
        val readyForRetry: Int,
        val averageRetryCount: Double,
        val oldestMessageAge: Long
    )
    
    /**
     * Create a DataPacket for retry from a QueuedMessage
     */
    suspend fun createDataPacketForRetry(queuedMessage: QueuedMessage): DataPacket {
        // Create a retry packet with a new ID to avoid conflicts
        // The original message will be updated when the retry succeeds
        return DataPacket(
            to = queuedMessage.destinationId,
            bytes = queuedMessage.messageText.encodeToByteArray(),
            dataType = Portnums.PortNum.TEXT_MESSAGE_APP_VALUE,
            id = generatePacketId(), // Generate new ID for retry
            channel = queuedMessage.channelIndex
        )
    }
    
    /**
     * Generate a new packet ID for retry attempts
     */
    private fun generatePacketId(): Int {
        return Random().nextInt()
    }
} 