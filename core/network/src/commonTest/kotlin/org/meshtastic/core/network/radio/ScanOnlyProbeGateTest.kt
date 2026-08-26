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

class ScanOnlyProbeGateTest {

    @Test
    fun `no probing while the failure streak is below the threshold`() {
        val gate = ScanOnlyProbeGate(failureThreshold = 3)

        assertFalse(gate.shouldProbeInsteadOfBondedConnect(0), "a healthy connection must never probe")
        assertFalse(gate.shouldProbeInsteadOfBondedConnect(-1), "a nonsensical count must never probe")
        assertFalse(gate.shouldProbeInsteadOfBondedConnect(1), "one failure is an ordinary out-of-range blip")
        assertFalse(gate.shouldProbeInsteadOfBondedConnect(2), "two failures are still below the threshold")
    }

    @Test
    fun `probing starts at the threshold and periodically yields to bonded fallback`() {
        val gate = ScanOnlyProbeGate(failureThreshold = 3)

        assertTrue(gate.shouldProbeInsteadOfBondedConnect(3), "the threshold-th consecutive failure must arm probing")
        assertTrue(gate.shouldProbeInsteadOfBondedConnect(4))
        assertTrue(gate.shouldProbeInsteadOfBondedConnect(5))
        assertTrue(gate.shouldProbeInsteadOfBondedConnect(6))
        assertFalse(
            gate.shouldProbeInsteadOfBondedConnect(7),
            "the fifth long-streak attempt must yield to bonded autoConnect as a scan-unavailable escape",
        )
        assertTrue(gate.shouldProbeInsteadOfBondedConnect(8), "probing must resume after the periodic bonded fallback")
    }

    @Test
    fun `a stable reconnect policy outcome disarms probing`() {
        val gate = ScanOnlyProbeGate()
        val policy = BleReconnectPolicy()
        repeat(ScanOnlyProbeGate.DEFAULT_FAILURE_THRESHOLD) {
            policy.processOutcome(BleReconnectPolicy.Outcome.Failed(IllegalStateException("test failure")))
        }

        assertTrue(gate.shouldProbeInsteadOfBondedConnect(policy.consecutiveFailures))

        policy.processOutcome(BleReconnectPolicy.Outcome.Disconnected(wasStable = true, wasIntentional = false))

        assertFalse(gate.shouldProbeInsteadOfBondedConnect(policy.consecutiveFailures))
    }

    /**
     * Discriminator against the stale-GATT-window cost: a probe pass replaces a full bonded-handle connect (GATT open
     * plus up to the platform connection timeout) with a cheap scan miss, so it must only arm once the retry ladder has
     * demonstrably stopped working — not while early attempts are still likely to succeed.
     */
    @Test
    fun `the default threshold is far above the reconnect policy transient-disconnect threshold`() {
        assertTrue(
            ScanOnlyProbeGate.DEFAULT_FAILURE_THRESHOLD > BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD,
            "probing at the transient-disconnect threshold would skip legitimate autoConnect chances " +
                "(probe=${ScanOnlyProbeGate.DEFAULT_FAILURE_THRESHOLD}, " +
                "transient=${BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD})",
        )
    }

    /**
     * Pins the intent behind the default rather than the number: probing may only start after *minutes* of unbroken
     * failure, mirroring [GattCacheInvalidationGate]'s staleness bar. Lower-bound math assumes every pre-probe attempt
     * failed instantly after its scan window; slower failures only push the first probe later.
     */
    @Test
    fun `the default threshold is only reachable after minutes of unbroken failure`() {
        val threshold = ScanOnlyProbeGate.DEFAULT_FAILURE_THRESHOLD
        val backoff =
            (1..threshold).fold(Duration.ZERO) { total, failures -> total + computeReconnectBackoff(failures) }
        // One settle delay plus one missed scan window precede every failing attempt.
        val elapsedBeforeFirstProbe = (BleReconnectPolicy.DEFAULT_SETTLE_DELAY + SCAN_TIMEOUT) * threshold + backoff

        assertTrue(
            elapsedBeforeFirstProbe >= 3.minutes,
            "probing must not be reachable inside an ordinary out-of-range gap (reached at $elapsedBeforeFirstProbe)",
        )
        assertTrue(
            ScanOnlyProbeGate().shouldProbeInsteadOfBondedConnect(threshold),
            "the timing discriminator must correspond to an actually armed gate",
        )
    }

    @Test
    fun `a non-positive threshold is rejected`() {
        assertFailsWith<IllegalArgumentException> { ScanOnlyProbeGate(failureThreshold = 0) }
        assertFailsWith<IllegalArgumentException> { ScanOnlyProbeGate(failureThreshold = -1) }
    }
}
