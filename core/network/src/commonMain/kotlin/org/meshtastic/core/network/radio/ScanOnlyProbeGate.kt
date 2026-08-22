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

/**
 * Decides when a bonded device's reconnect attempt should be a cheap scan-only probe instead of the full bonded-handle
 * connect.
 *
 * Background: while a bonded radio stays away (out of range or powered off), every reconnect iteration pays the full
 * bonded-fallback price — a fresh GATT open against Android's stale-GATT window plus up to [CONNECTION_TIMEOUT] — only
 * to fail again seconds later. Field logs show that pattern repeating every ~83 s for as long as the radio is gone.
 *
 * Probing is not free either: autoConnect from a bonded handle can succeed without any advertisement at all, so
 * skipping it on early failures would trade away legitimate reconnect chances during an ordinary out-of-range blip.
 * Like [GattCacheInvalidationGate], this gate therefore arms only after the failure streak has run for *minutes* — once
 * the retry ladder has saturated at its 60 s cap and demonstrably stopped working — and disarms automatically when the
 * streak ends ([BleReconnectPolicy] resets its counter on any stable connection).
 *
 * Scan visibility is not equivalent to connectability on Android: API 26–30 can return no scan results when system
 * Location is off, and API 31+ can lose `BLUETOOTH_SCAN` while a bonded `autoConnect` remains viable. To avoid making
 * scan-only mode a permanent trap in either case, every [BONDED_FALLBACK_INTERVAL]th long-streak attempt deliberately
 * yields to the normal bonded fallback.
 *
 * The gate is stateless; its answer is derived entirely from [consecutiveFailures].
 *
 * @param failureThreshold consecutive reconnect failures required before attempts become scan-only probes
 */
internal class ScanOnlyProbeGate(val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD) {

    init {
        require(failureThreshold > 0) { "failureThreshold must be positive, was $failureThreshold" }
    }

    /**
     * Returns true when the attempt about to run should probe by scan alone rather than connect from the bonded handle.
     *
     * A [consecutiveFailures] of zero can never reach [failureThreshold] (which is always positive), so a healthy
     * connection never probes. Once armed, every [BONDED_FALLBACK_INTERVAL]th long-streak attempt returns false so a
     * bonded `autoConnect` still gets periodic recovery opportunities when Android scanning is unavailable.
     *
     * @param consecutiveFailures failures observed *before* the attempt in progress, i.e.
     *   [BleReconnectPolicy.consecutiveFailures] read from inside the attempt
     */
    fun shouldProbeInsteadOfBondedConnect(consecutiveFailures: Int): Boolean {
        if (consecutiveFailures < failureThreshold) return false

        val longStreakAttempt = consecutiveFailures - failureThreshold + 1
        return longStreakAttempt % BONDED_FALLBACK_INTERVAL != 0
    }

    companion object {
        /**
         * Consecutive failures before reconnect attempts switch to cheap scan-only probes.
         *
         * Deliberately far above [BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD] (3), which only marks a disconnect as
         * "more than a blip" for the UI. Three failures are reached about 47 s into an ordinary out-of-range gap, where
         * autoConnect still deserves its chance. Six is the first count at which [computeReconnectBackoff] has been
         * saturated at its 60 s cap for two consecutive cycles — the same "minutes of unbroken failure" bar
         * [GattCacheInvalidationGate.DEFAULT_FAILURE_THRESHOLD] uses for stale-cache recovery, so both escalations come
         * online together on the seventh attempt.
         */
        const val DEFAULT_FAILURE_THRESHOLD = 6

        /** Long-streak attempts between deliberate retries of the normal bonded-handle connection path. */
        const val BONDED_FALLBACK_INTERVAL = 5
    }
}
