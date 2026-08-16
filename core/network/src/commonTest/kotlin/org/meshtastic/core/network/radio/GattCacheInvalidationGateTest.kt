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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GattCacheInvalidationGateTest {

    @Test
    fun `no invalidation while the failure streak is below the threshold`() {
        val gate = GattCacheInvalidationGate(failureThreshold = 3)

        assertFalse(gate.shouldInvalidateOnAttempt(0), "a healthy connection must never refresh the cache")
        assertFalse(gate.shouldInvalidateOnAttempt(-1), "a nonsensical count must never refresh the cache")
        assertFalse(gate.shouldInvalidateOnAttempt(1), "one failure is an ordinary out-of-range blip")
        assertFalse(gate.shouldInvalidateOnAttempt(2), "two failures are still below the threshold")
    }

    @Test
    fun `invalidation is requested once the threshold is reached`() {
        val gate = GattCacheInvalidationGate(failureThreshold = 3)

        assertTrue(gate.shouldInvalidateOnAttempt(3), "the threshold-th consecutive failure must arm the refresh")
    }

    @Test
    fun `the refresh fires at most once per failure streak`() {
        val gate = GattCacheInvalidationGate(failureThreshold = 3)

        assertTrue(gate.shouldInvalidateOnAttempt(3))
        gate.onCacheInvalidated()

        assertFalse(gate.shouldInvalidateOnAttempt(4), "a refresh that did not help must not repeat every attempt")
        assertFalse(gate.shouldInvalidateOnAttempt(9), "the allowance stays consumed for the rest of the streak")
    }

    /**
     * Discriminator for the "consume the allowance only on a real refresh" rule: on Android the reflection hop into
     * `BluetoothGatt` can miss, in which case nothing was refreshed and nothing may be consumed. Without the guard, one
     * silent miss would disable the recovery for the whole streak.
     */
    @Test
    fun `an attempted refresh that never happened leaves the allowance intact`() {
        val gate = GattCacheInvalidationGate(failureThreshold = 3)

        assertTrue(gate.shouldInvalidateOnAttempt(3))
        // No onCacheInvalidated(): the platform reported it could not refresh.

        assertTrue(gate.shouldInvalidateOnAttempt(4), "a no-op refresh must not burn the streak allowance")
    }

    @Test
    fun `the end of a failure streak re-arms the gate for the next one`() {
        val gate = GattCacheInvalidationGate(failureThreshold = 3)

        assertTrue(gate.shouldInvalidateOnAttempt(3))
        gate.onCacheInvalidated()
        assertFalse(gate.shouldInvalidateOnAttempt(5))

        gate.onFailureStreakEnded()

        assertFalse(gate.shouldInvalidateOnAttempt(2), "the new streak restarts below the threshold")
        assertTrue(gate.shouldInvalidateOnAttempt(3), "a fresh streak earns a fresh refresh")
    }

    @Test
    fun `a threshold of one refreshes on the first failure`() {
        val gate = GattCacheInvalidationGate(failureThreshold = 1)

        assertTrue(gate.shouldInvalidateOnAttempt(1))
    }

    @Test
    fun `a non-positive threshold is rejected`() {
        assertFailsWith<IllegalArgumentException> { GattCacheInvalidationGate(failureThreshold = 0) }
        assertFailsWith<IllegalArgumentException> { GattCacheInvalidationGate(failureThreshold = -1) }
    }

    @Test
    fun `the default threshold matches the reconnect policy transient-disconnect threshold`() {
        assertEquals(
            BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD,
            GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD,
            "the cache refresh should coincide with the point the policy already calls the disconnect non-transient",
        )
    }
}
