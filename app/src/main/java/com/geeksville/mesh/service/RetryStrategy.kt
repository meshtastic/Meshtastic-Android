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
        // Exponential backoff intervals: 1m, 5m, 30m, 2h, 12h
        private val BACKOFF_INTERVALS = longArrayOf(
            1 * 60_000L,      // 1 minute
            5 * 60_000L,      // 5 minutes
            30 * 60_000L,     // 30 minutes
            2 * 60 * 60_000L, // 2 hours
            12 * 60 * 60_000L // 12 hours
        )
        
        // Maximum age for queued messages: 24 hours
        const val MAX_MESSAGE_AGE_MILLIS = 24 * 60 * 60 * 1000L
        
        // Maximum number of backoff retries before switching to connection-only mode
        const val MAX_BACKOFF_RETRIES = 5
    }
    
    /**
     * Check if message should be retried based on:
     * - Time since last attempt (exponential backoff)
     * - Network connectivity status
     * - Target node availability
     * - Message age and retry count limits
     */
    fun shouldRetry(queuedMessage: QueuedMessage, currentTime: Long): Boolean {
        // Check if message isn't too old
        if (currentTime - queuedMessage.queuedTime > MAX_MESSAGE_AGE_MILLIS) {
            return false
        }
        
        // Check if message hasn't exceeded max retries
        if (queuedMessage.attemptCount >= queuedMessage.maxRetries) {
            return false
        }
        
        // If we've exhausted backoff retries, only retry on connection events
        if (queuedMessage.attemptCount >= MAX_BACKOFF_RETRIES) {
            // Message remains queued but only retries when connection is detected
            // This is handled by shouldRetryImmediately()
            return false
        }
        
        // Check backoff timing for active retry attempts
        val timeSinceLastAttempt = currentTime - queuedMessage.lastAttemptTime
        val backoffTime = calculateBackoff(queuedMessage.attemptCount)
        
        return timeSinceLastAttempt >= backoffTime
    }
    
    /**
     * Check if message should be retried immediately when a node is detected,
     * bypassing normal backoff timing. This is used when we detect a node
     * has come back online via telemetry, position updates, etc.
     */
    fun shouldRetryImmediately(queuedMessage: QueuedMessage, currentTime: Long): Boolean {
        // Check if message isn't too old
        if (currentTime - queuedMessage.queuedTime > MAX_MESSAGE_AGE_MILLIS) {
            return false
        }
        
        // Check if message hasn't exceeded max retries
        if (queuedMessage.attemptCount >= queuedMessage.maxRetries) {
            return false
        }
        
        // For immediate retry, we ignore backoff timing and retry any message
        // that hasn't exceeded its retry limit and isn't too old
        return true
    }
    
    /**
     * Calculate exponential backoff time based on attempt count.
     * Follows pattern: 1m, 5m, 30m, 2h, 12h
     */
    fun calculateBackoff(attemptCount: Int): Long {
        if (attemptCount == 0) return 0L // First attempt has no backoff
        
        // Use predefined intervals, capping at the last interval
        val intervalIndex = minOf(attemptCount - 1, BACKOFF_INTERVALS.size - 1)
        return BACKOFF_INTERVALS[intervalIndex]
    }
    
    /**
     * Determine if we should trigger immediate retry for all queued messages
     * when network conditions improve significantly.
     * This includes messages that have exhausted their backoff retries.
     */
    fun shouldTriggerImmediateRetry(
        connectionStateChanged: Boolean,
        nodeDetected: Boolean
    ): Boolean {
        return connectionStateChanged || nodeDetected
    }
    
    /**
     * Check if a message is in connection-only retry mode
     * (has exhausted backoff retries but is still within 24h limit)
     */
    fun isInConnectionOnlyMode(queuedMessage: QueuedMessage, currentTime: Long): Boolean {
        val isWithinTimeLimit = currentTime - queuedMessage.queuedTime <= MAX_MESSAGE_AGE_MILLIS
        val hasExhaustedBackoffRetries = queuedMessage.attemptCount >= MAX_BACKOFF_RETRIES
        val hasNotExceededMaxRetries = queuedMessage.attemptCount < queuedMessage.maxRetries
        
        return isWithinTimeLimit && hasExhaustedBackoffRetries && hasNotExceededMaxRetries
    }
    
    /**
     * Get the next retry time for a queued message
     */
    fun getNextRetryTime(queuedMessage: QueuedMessage): Long {
        // If in connection-only mode, return a far future time (effectively never)
        if (queuedMessage.attemptCount >= MAX_BACKOFF_RETRIES) {
            return Long.MAX_VALUE
        }
        
        val lastAttempt = maxOf(queuedMessage.lastAttemptTime, queuedMessage.queuedTime)
        val backoffTime = calculateBackoff(queuedMessage.attemptCount)
        return lastAttempt + backoffTime
    }
    
    /**
     * Get a human-readable description of the current retry status
     */
    fun getRetryStatusDescription(queuedMessage: QueuedMessage, currentTime: Long): String {
        if (currentTime - queuedMessage.queuedTime > MAX_MESSAGE_AGE_MILLIS) {
            return "Message expired (24h limit reached)"
        }
        
        if (queuedMessage.attemptCount >= queuedMessage.maxRetries) {
            return "Maximum retries exceeded"
        }
        
        if (isInConnectionOnlyMode(queuedMessage, currentTime)) {
            return "Waiting for target device connection (backoff retries exhausted)"
        }
        
        val nextRetryTime = getNextRetryTime(queuedMessage)
        if (nextRetryTime == Long.MAX_VALUE) {
            return "Waiting for connection"
        }
        
        val timeUntilRetry = nextRetryTime - currentTime
        if (timeUntilRetry <= 0) {
            return "Ready for retry"
        }
        
        return "Next retry in ${formatDuration(timeUntilRetry)}"
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
} 