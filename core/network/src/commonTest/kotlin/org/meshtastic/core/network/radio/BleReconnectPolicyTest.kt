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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        assertEquals(customBackoff, (action as BleReconnectPolicy.Action.Retry).backoff)
    }
}
