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
 * Decides when the ordinary BLE reconnect loop should refresh the platform's cached GATT service table.
 *
 * Background: after a bonded radio goes out of range or power-cycles for a long time, Android can keep serving a stale
 * cached service table for it. Every reconnect then discovers the wrong (or no) Meshtastic characteristics and fails,
 * so [BleReconnectPolicy] retries forever and the UI never leaves "Not connected" — historically only an OS-level
 * unpair/re-pair cleared it.
 *
 * Refreshing is not free: it costs an extra disconnect → settle → reconnect round trip and throws away a valid cache,
 * so it must not fire on the ordinary out-of-range blip. A radio switched off for a minute, or a phone carried out of
 * range and back, is not evidence of a stale cache — tearing down a link that was about to succeed would turn an
 * ordinary reconnect into a failure plus a fresh round of backoff, which is worse than doing nothing at all.
 *
 * The gate therefore arms only once the failure streak has run for *minutes* (see [DEFAULT_FAILURE_THRESHOLD]) — the
 * prolonged absence issue #6685 actually describes — and fires **at most once per streak**: if a genuine refresh did
 * not fix the connection, repeating it every attempt only adds latency.
 *
 * Not thread-safe by design: it is owned by [BleRadioTransport]'s single reconnect-loop coroutine.
 *
 * @param failureThreshold consecutive reconnect failures required before a refresh is considered
 */
internal class GattCacheInvalidationGate(private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD) {

    init {
        require(failureThreshold > 0) { "failureThreshold must be positive, was $failureThreshold" }
    }

    /** True once a refresh actually took effect during the current failure streak. */
    private var invalidatedForCurrentStreak: Boolean = false

    /**
     * Returns true when the connection attempt now in progress should refresh the GATT cache.
     *
     * A [consecutiveFailures] of zero can never reach [failureThreshold] (which is always positive), so a healthy
     * connection never refreshes.
     *
     * @param consecutiveFailures failures observed *before* the attempt in progress, i.e.
     *   [BleReconnectPolicy.consecutiveFailures] read from inside the attempt
     */
    fun shouldInvalidateOnAttempt(consecutiveFailures: Int): Boolean =
        !invalidatedForCurrentStreak && consecutiveFailures >= failureThreshold

    /**
     * Records that a refresh actually took effect, consuming this streak's single allowance.
     *
     * Only call this when the platform reported success. A refresh that could not be performed at all (on Android the
     * reflection hop into the `BluetoothGatt` can miss after a Kable upgrade) is a no-op that costs nothing, so it must
     * not burn the allowance — otherwise one silent miss would disable the recovery for the rest of the streak.
     */
    fun onCacheInvalidated() {
        invalidatedForCurrentStreak = true
    }

    /**
     * Re-arms the gate because the failure streak ended, so a later streak earns its own refresh.
     *
     * Must be driven by the same outcomes that reset [BleReconnectPolicy.consecutiveFailures]. The counter cannot be
     * used to infer this: the caller only reads it on an attempt that reached a connected link, and the streak-ending
     * reset lands *after* that attempt returns. A radio that is out of range again fails its next attempt long before
     * any zero could be observed, which would leave the gate consumed forever and silently reintroduce issue #6685 the
     * second time a device disappears.
     */
    fun onFailureStreakEnded() {
        invalidatedForCurrentStreak = false
    }

    companion object {
        /**
         * Consecutive failures before a stale cache is suspected.
         *
         * Deliberately far above [BleReconnectPolicy.DEFAULT_FAILURE_THRESHOLD] (3), which only marks a disconnect as
         * "more than a blip" for the UI. Three failures are reached about 47 s into an ordinary out-of-range or
         * powered-off gap, so refreshing there would sabotage a normal reconnect rather than repair a stale cache.
         *
         * Six is the first count at which [computeReconnectBackoff] has been saturated at its 60 s cap for two
         * consecutive cycles: the retry ladder is exhausted and every further attempt is identical. With a
         * [BleReconnectPolicy.DEFAULT_SETTLE_DELAY] before each attempt, the refresh lands on the seventh attempt, 3
         * min 36 s into an unbroken streak (seven settle delays plus 5+10+20+40+60+60 s of backoff). That is minutes of
         * continuous failure, which is what issue #6685's "out of range / power off" report describes, rather than the
         * sub-minute blip the lower threshold catches.
         */
        const val DEFAULT_FAILURE_THRESHOLD = 6
    }
}
