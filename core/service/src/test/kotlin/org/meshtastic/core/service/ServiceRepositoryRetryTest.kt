/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.service

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unit tests for ServiceRepository retry management functionality. */
class ServiceRepositoryRetryTest {

    private lateinit var serviceRepository: ServiceRepository

    @Before
    fun setUp() {
        serviceRepository = ServiceRepository()
    }

    @Test
    fun `requestRetry returns true when user confirms`() = runTest {
        val testEvent =
            RetryEvent.MessageRetry(packetId = 123, text = "Test message", attemptNumber = 1, maxAttempts = 3)

        // Start retry request in background
        val retryDeferred = async { serviceRepository.requestRetry(testEvent, timeoutMs = 5000) }

        // Wait for non-null event to be set
        val emittedEvent = serviceRepository.retryEvents.first { it != null }
        assertEquals(testEvent, emittedEvent)

        // Simulate user clicking "Retry Now"
        serviceRepository.respondToRetry(testEvent.packetId, shouldRetry = true)

        // Verify result
        val result = retryDeferred.await()
        assertTrue("Expected retry to proceed", result)
    }

    @Test
    fun `requestRetry returns false when user cancels`() = runTest {
        val testEvent = RetryEvent.ReactionRetry(packetId = 456, emoji = "👍", attemptNumber = 2, maxAttempts = 3)

        // Start retry request in background
        val retryDeferred = async { serviceRepository.requestRetry(testEvent, timeoutMs = 5000) }

        // Wait for non-null event to be set
        val emittedEvent = serviceRepository.retryEvents.first { it != null }
        assertEquals(testEvent, emittedEvent)

        // Simulate user clicking "Cancel Retry"
        serviceRepository.respondToRetry(testEvent.packetId, shouldRetry = false)

        // Verify result
        val result = retryDeferred.await()
        assertFalse("Expected retry to be cancelled", result)
    }

    @Test
    fun `requestRetry returns true on timeout when user does not respond`() = runTest {
        val testEvent =
            RetryEvent.MessageRetry(packetId = 789, text = "Timeout test", attemptNumber = 1, maxAttempts = 3)

        // Start retry request with short timeout
        val result = serviceRepository.requestRetry(testEvent, timeoutMs = 100)

        // Should auto-retry on timeout
        assertTrue("Expected auto-retry on timeout", result)
    }

    @Test
    fun `multiple simultaneous retry requests handled independently`() = runTest {
        val event1 = RetryEvent.MessageRetry(packetId = 100, text = "Message 1", attemptNumber = 1, maxAttempts = 3)
        val event2 = RetryEvent.MessageRetry(packetId = 200, text = "Message 2", attemptNumber = 1, maxAttempts = 3)

        // Start two retry requests simultaneously
        val retry1 = async { serviceRepository.requestRetry(event1, timeoutMs = 5000) }
        val retry2 = async { serviceRepository.requestRetry(event2, timeoutMs = 5000) }

        // Give time for events to be emitted
        delay(50)

        // Respond differently to each
        serviceRepository.respondToRetry(event1.packetId, shouldRetry = true)
        serviceRepository.respondToRetry(event2.packetId, shouldRetry = false)

        // Verify results
        val result1 = retry1.await()
        val result2 = retry2.await()

        assertTrue("First retry should proceed", result1)
        assertFalse("Second retry should be cancelled", result2)
    }

    @Test
    fun `cancelPendingRetries completes all pending requests with false`() = runTest {
        val event1 = RetryEvent.MessageRetry(packetId = 111, text = "Message 1", attemptNumber = 1, maxAttempts = 3)
        val event2 = RetryEvent.MessageRetry(packetId = 222, text = "Message 2", attemptNumber = 1, maxAttempts = 3)

        // Start two retry requests
        val retry1 = async { serviceRepository.requestRetry(event1, timeoutMs = 10000) }
        val retry2 = async { serviceRepository.requestRetry(event2, timeoutMs = 10000) }

        // Give time for requests to register
        delay(50)

        // Cancel all pending retries
        serviceRepository.cancelPendingRetries()

        // Verify both completed with false
        val result1 = retry1.await()
        val result2 = retry2.await()

        assertFalse("First retry should be cancelled", result1)
        assertFalse("Second retry should be cancelled", result2)
    }

    @Test
    fun `retryEvents are cleared after user responds`() = runTest {
        val testEvent =
            RetryEvent.MessageRetry(packetId = 333, text = "Clear test", attemptNumber = 1, maxAttempts = 3)

        // Start retry request
        val retryDeferred = async { serviceRepository.requestRetry(testEvent, timeoutMs = 5000) }

        // Wait for event to be set
        val emittedEvent = serviceRepository.retryEvents.first { it != null }
        assertEquals("Should receive event", testEvent, emittedEvent)

        // Respond to the retry
        serviceRepository.respondToRetry(testEvent.packetId, shouldRetry = true)

        // Wait for response to complete
        retryDeferred.await()

        // Verify event is cleared
        assertEquals("Event should be cleared after responding", null, serviceRepository.retryEvents.value)
    }

    @Test
    fun `respondToRetry does nothing for unknown packetId`() = runTest {
        // This should not throw or cause issues
        serviceRepository.respondToRetry(999, shouldRetry = true)
        // Test passes if no exception thrown
    }
}
