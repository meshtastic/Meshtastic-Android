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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BleReconnectPolicyTest {

    @Test
    fun `stable disconnect resets failures and returns Continue`() {
        val policy = BleReconnectPolicy()
        // Simulate one prior failure
        policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
        assertEquals(1, policy.consecutiveFailures)

        // Now a stable disconnect should reset
        val action =
            policy.processOutcome(BleReconnectPolicy.Outcome.Disconnected(wasStable = true, wasIntentional = false))
        assertEquals(BleReconnectPolicy.Action.Continue, action)
        assertEquals(0, policy.consecutiveFailures)
    }

    @Test
    fun `intentional disconnect resets failures and returns Continue`() {
        val policy = BleReconnectPolicy()
        policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))

        val action =
            policy.processOutcome(BleReconnectPolicy.Outcome.Disconnected(wasStable = false, wasIntentional = true))
        assertEquals(BleReconnectPolicy.Action.Continue, action)
        assertEquals(0, policy.consecutiveFailures)
    }

    @Test
    fun `unstable disconnect increments failures`() {
        val policy = BleReconnectPolicy()
        val action =
            policy.processOutcome(BleReconnectPolicy.Outcome.Disconnected(wasStable = false, wasIntentional = false))
        assertEquals(1, policy.consecutiveFailures)
        assertTrue(action is BleReconnectPolicy.Action.Retry)
    }

    @Test
    fun `failure at threshold signals transient disconnect`() {
        val policy = BleReconnectPolicy(failureThreshold = 3)
        // Accumulate failures up to threshold
        repeat(2) { policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test"))) }
        val action = policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
        assertEquals(3, policy.consecutiveFailures)
        assertTrue(action is BleReconnectPolicy.Action.SignalTransient)
    }

    @Test
    fun `failure at max gives up permanently`() {
        val policy = BleReconnectPolicy(maxFailures = 3)
        repeat(2) { policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test"))) }
        val action = policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
        assertEquals(BleReconnectPolicy.Action.GiveUp, action)
    }

    @Test
    fun `backoff increases with consecutive failures`() {
        val policy = BleReconnectPolicy()
        val backoffs =
            (1..5).map { i ->
                val action = policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
                when (action) {
                    is BleReconnectPolicy.Action.Retry -> action.backoff
                    is BleReconnectPolicy.Action.SignalTransient -> action.backoff
                    else -> error("Unexpected action: $action")
                }
            }
        // Verify backoffs are non-decreasing
        for (i in 0 until backoffs.size - 1) {
            assertTrue(backoffs[i] <= backoffs[i + 1], "Expected ${backoffs[i]} <= ${backoffs[i + 1]}")
        }
    }

    @Test
    fun `custom backoff strategy is used`() {
        val customBackoff = 42.seconds
        val policy = BleReconnectPolicy(backoffStrategy = { customBackoff })
        val action = policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
        assertTrue(action is BleReconnectPolicy.Action.Retry)
        assertEquals(customBackoff, action.backoff)
    }

    @Test
    fun `maxFailures equal to failureThreshold gives up without signalling transient`() {
        val policy = BleReconnectPolicy(maxFailures = 3, failureThreshold = 3)
        repeat(2) { policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test"))) }
        val action = policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
        // GiveUp takes priority over SignalTransient when both thresholds are the same
        assertEquals(BleReconnectPolicy.Action.GiveUp, action)
    }

    @Test
    fun `failure count resets after stable disconnect then re-increments`() {
        val policy = BleReconnectPolicy()
        // Accumulate two failures
        repeat(2) { policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test"))) }
        assertEquals(2, policy.consecutiveFailures)

        // Stable disconnect resets
        policy.processOutcome(BleReconnectPolicy.Outcome.Disconnected(wasStable = true, wasIntentional = false))
        assertEquals(0, policy.consecutiveFailures)

        // New failure starts from 1
        policy.processOutcome(BleReconnectPolicy.Outcome.Failed(RuntimeException("test")))
        assertEquals(1, policy.consecutiveFailures)
    }

    // region execute() loop tests

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute gives up after maxFailures and calls onPermanentDisconnect`() = runTest {
        val policy =
            BleReconnectPolicy(maxFailures = 3, settleDelay = 1.milliseconds, backoffStrategy = { 1.milliseconds })
        var permanentError: Throwable? = null
        var permanentCalled = false
        var transientCalled = false

        policy.execute(
            attempt = { BleReconnectPolicy.Outcome.Failed(RuntimeException("connection failed")) },
            onTransientDisconnect = { transientCalled = true },
            onPermanentDisconnect = { error ->
                permanentCalled = true
                permanentError = error
            },
        )

        assertTrue(permanentCalled, "onPermanentDisconnect should have been called")
        assertNotNull(permanentError, "error should be passed to onPermanentDisconnect")
        assertEquals("connection failed", permanentError?.message)
        assertEquals(3, policy.consecutiveFailures)
        // failureThreshold defaults to 3, same as maxFailures here, so GiveUp takes priority
        assertTrue(!transientCalled, "onTransientDisconnect should not be called when GiveUp fires first")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute calls onTransientDisconnect at threshold then continues retrying`() = runTest {
        var attemptCount = 0
        val policy =
            BleReconnectPolicy(
                maxFailures = 5,
                failureThreshold = 2,
                settleDelay = 1.milliseconds,
                backoffStrategy = { 1.milliseconds },
            )
        var transientCount = 0

        policy.execute(
            attempt = {
                attemptCount++
                BleReconnectPolicy.Outcome.Failed(RuntimeException("fail #$attemptCount"))
            },
            onTransientDisconnect = { transientCount++ },
            onPermanentDisconnect = {},
        )

        assertEquals(5, attemptCount, "should attempt exactly maxFailures times")
        // Transient is signalled for failures 2, 3, 4 (at or above threshold, below maxFailures)
        assertEquals(3, transientCount, "should signal transient for each failure at or above threshold")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute continues immediately after stable disconnect`() = runTest {
        var attemptCount = 0
        val policy =
            BleReconnectPolicy(maxFailures = 5, settleDelay = 1.milliseconds, backoffStrategy = { 1.milliseconds })

        policy.execute(
            attempt = {
                attemptCount++
                if (attemptCount <= 2) {
                    // First two attempts connect briefly and disconnect stably
                    BleReconnectPolicy.Outcome.Disconnected(wasStable = true, wasIntentional = false)
                } else {
                    // Then fail until maxFailures
                    BleReconnectPolicy.Outcome.Failed(RuntimeException("fail"))
                }
            },
            onTransientDisconnect = {},
            onPermanentDisconnect = {},
        )

        // 2 stable disconnects + 5 failures (counter resets after each stable, so needs 5 more to hit max)
        assertEquals(7, attemptCount)
        assertEquals(5, policy.consecutiveFailures)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute passes null error for unstable disconnect at threshold`() = runTest {
        val policy =
            BleReconnectPolicy(
                maxFailures = 5,
                failureThreshold = 2,
                settleDelay = 1.milliseconds,
                backoffStrategy = { 1.milliseconds },
            )
        val transientErrors = mutableListOf<Throwable?>()
        var attemptCount = 0

        policy.execute(
            attempt = {
                attemptCount++
                // Use unstable disconnects (not Failed) so lastError is null
                BleReconnectPolicy.Outcome.Disconnected(wasStable = false, wasIntentional = false)
            },
            onTransientDisconnect = { error -> transientErrors.add(error) },
            onPermanentDisconnect = {},
        )

        // Disconnected outcomes don't have errors, so all transient callbacks get null
        assertTrue(transientErrors.all { it == null }, "Disconnected outcomes should pass null error")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `execute stops when coroutine is cancelled`() = runTest {
        var attemptCount = 0
        val policy =
            BleReconnectPolicy(maxFailures = 100, settleDelay = 1.milliseconds, backoffStrategy = { 1.milliseconds })

        val job =
            backgroundScope.launch {
                policy.execute(
                    attempt = {
                        attemptCount++
                        // Always succeed stably — loop should run until cancelled
                        BleReconnectPolicy.Outcome.Disconnected(wasStable = true, wasIntentional = false)
                    },
                    onTransientDisconnect = {},
                    onPermanentDisconnect = {},
                )
            }

        // Let a few iterations run, then cancel
        advanceTimeBy(50)
        job.cancel()
        advanceUntilIdle()

        // Should have made some attempts but not reached maxFailures
        assertTrue(attemptCount > 0, "should have attempted at least once")
        assertTrue(attemptCount < 100, "should not have exhausted all failures — was cancelled")
    }

    // endregion
}
