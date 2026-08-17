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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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

    /**
     * The refresh must sit far above the policy's transient-disconnect threshold. That threshold only decides when to
     * tell higher layers a disconnect is more than a blip — it is reached roughly 47 s into an ordinary out-of-range or
     * powered-off gap, which is no evidence at all of a stale service table. Refreshing there tears down a link that
     * was about to succeed and costs the user a failed attempt plus a fresh round of backoff.
     */
    @Test
    fun `the default threshold is far above the reconnect policy transient-disconnect threshold`() {
        assertTrue(
            GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD > BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD,
            "a refresh at the transient-disconnect threshold would sabotage ordinary reconnects instead of repairing " +
                "a stale cache (cache=${GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD}, " +
                "transient=${BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD})",
        )
    }

    /**
     * Pins the intent behind the default rather than the number: the gate may only fire after *minutes* of unbroken
     * failure, because the stale cache it repairs follows a prolonged absence (issue #6685 — "go out of range / power
     * off"). Derived from the production settle delay and backoff ladder, so retuning either one cannot silently drag
     * the refresh back into blip territory.
     */
    @Test
    fun `the default threshold is only reachable after minutes of unbroken failure`() {
        val threshold = GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD
        val backoff =
            (1..threshold).fold(Duration.ZERO) { total, failures -> total + computeReconnectBackoff(failures) }
        // One settle delay precedes every attempt: the `threshold` attempts that fail, plus the one that refreshes.
        val elapsedBeforeRefresh = BleReconnectPolicy.DEFAULT_SETTLE_DELAY * (threshold + 1) + backoff

        assertTrue(
            elapsedBeforeRefresh >= 3.minutes,
            "the refresh must not be reachable inside an ordinary out-of-range gap (reached at $elapsedBeforeRefresh)",
        )
    }
}
