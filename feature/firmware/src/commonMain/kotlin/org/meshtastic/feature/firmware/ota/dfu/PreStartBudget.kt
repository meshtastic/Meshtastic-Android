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
package org.meshtastic.feature.firmware.ota.dfu

import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Bootloader 2.6.0 boots a valid application after 6 minutes in DFU with no activity, and the application is only
 * erased once START is accepted. Kept under that so retries stop before the board has left DFU.
 */
internal val PRE_START_BUDGET = 5.minutes

/**
 * Bounds the time from entering DFU mode until the bootloader accepts START. Once [markStarted] is called the budget no
 * longer applies, so a drop after START keeps the normal bounded restart.
 */
internal class PreStartBudget(private val budget: Duration, timeSource: TimeSource = TimeSource.Monotonic) {
    private val entered = timeSource.markNow()

    @Volatile private var started = false

    fun markStarted() {
        started = true
    }

    /** Throws [DfuException.StartTimedOut] once the budget is spent before START. */
    fun check() {
        if (!started && entered.elapsedNow() >= budget) throw DfuException.StartTimedOut(budget)
    }

    /** Runs [block], cut off at the remaining budget while START has not been accepted. */
    suspend fun bounded(block: suspend () -> Unit) {
        check()
        if (started) return block()
        withTimeoutOrNull(budget - entered.elapsedNow()) { block() } ?: throw DfuException.StartTimedOut(budget)
    }
}
