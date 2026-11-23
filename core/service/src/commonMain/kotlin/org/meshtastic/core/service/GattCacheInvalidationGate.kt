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
package org.meshtastic.core.service

/**
 * Decides when a BLE reconnect should refresh Android's cached GATT service table.
 *
 * A bonded radio that stays out of range or powered off for a long time can come back with Android still serving a
 * stale service table, failing every reconnect until the user unpairs (issue #6685). A refresh costs a reconnect round
 * trip, so the gate arms only after a long failure streak and fires at most once per streak.
 *
 * Not thread-safe; the caller serializes access.
 *
 * @param failureThreshold consecutive failures required before a refresh is considered
 */
internal class GattCacheInvalidationGate(private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD) {

    init {
        require(failureThreshold > 0) { "failureThreshold must be positive, was $failureThreshold" }
    }

    private var invalidatedForCurrentStreak: Boolean = false

    /** True when the attempt now connecting, preceded by [consecutiveFailures] failures, should refresh the cache. */
    fun shouldInvalidateOnAttempt(consecutiveFailures: Int): Boolean =
        !invalidatedForCurrentStreak && consecutiveFailures >= failureThreshold

    /** Consumes this streak's single refresh. */
    fun onCacheInvalidated() {
        invalidatedForCurrentStreak = true
    }

    /** Re-arms the gate; must follow every outcome that resets the failure streak. */
    fun onFailureStreakEnded() {
        invalidatedForCurrentStreak = false
    }

    companion object {
        /** Consecutive failures before a stale GATT cache is suspected: far above an ordinary out-of-range blip. */
        const val DEFAULT_FAILURE_THRESHOLD = 6
    }
}
