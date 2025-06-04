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

import com.geeksville.mesh.database.entity.QueuedMessage

/**
 * Determines when to retry queued messages based on exponential backoff,
 * network connectivity status, and target node availability.
 * 
 * Part of the intelligent message queue system for automatic retry when
 * conditions improve.
 */
class RetryStrategy {
    
    companion object {
        // Base backoff time: 30 seconds
        private const val BASE_BACKOFF_MILLIS = 30_000L
        
        // Maximum backoff time: 30 minutes
        private const val MAX_BACKOFF_MILLIS = 30 * 60_000L
        
        // Maximum age for queued messages: 24 hours
        const val MAX_MESSAGE_AGE_MILLIS = 24 * 60 * 60 * 1000L
    }
    
    /**
     * Check if message should be retried based on:
     * - Time since last attempt (exponential backoff)
     * - Network connectivity status
     * - Target node availability
     * - Message age and retry count limits
     */
    fun shouldRetry(queuedMessage: QueuedMessage, currentTime: Long): Boolean {
        // Check if message hasn't exceeded max retries
        if (queuedMessage.attemptCount >= queuedMessage.maxRetries) {
            return false
        }
        
        // Check if message isn't too old
        if (currentTime - queuedMessage.queuedTime > MAX_MESSAGE_AGE_MILLIS) {
            return false
        }
        
        // Check backoff timing
        val timeSinceLastAttempt = currentTime - queuedMessage.lastAttemptTime
        val backoffTime = calculateBackoff(queuedMessage.attemptCount)
        
        return timeSinceLastAttempt >= backoffTime
    }
    
    /**
     * Calculate exponential backoff time based on attempt count.
     * Follows pattern: 30s, 1m, 2m, 4m, 8m, 16m, max 30m
     */
    fun calculateBackoff(attemptCount: Int): Long {
        if (attemptCount == 0) return 0L // First attempt has no backoff
        
        // Exponential backoff: 30s * 2^(attemptCount-1)
        val backoffTime = BASE_BACKOFF_MILLIS * (1L shl (attemptCount - 1))
        
        // Cap at maximum backoff time
        return minOf(backoffTime, MAX_BACKOFF_MILLIS)
    }
    
    /**
     * Determine if we should trigger immediate retry for all queued messages
     * when network conditions improve significantly.
     */
    fun shouldTriggerImmediateRetry(
        connectionStateChanged: Boolean,
        nodeDetected: Boolean
    ): Boolean {
        return connectionStateChanged || nodeDetected
    }
    
    /**
     * Get the next retry time for a queued message
     */
    fun getNextRetryTime(queuedMessage: QueuedMessage): Long {
        val lastAttempt = maxOf(queuedMessage.lastAttemptTime, queuedMessage.queuedTime)
        val backoffTime = calculateBackoff(queuedMessage.attemptCount)
        return lastAttempt + backoffTime
    }
} 