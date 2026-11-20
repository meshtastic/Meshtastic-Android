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
package org.meshtastic.core.network.transport

/**
 * Decides whether to reset the reconnect backoff based on session data and uptime.
 *
 * Only sessions that lasted at least [thresholdMs] with actual data exchange are considered stable enough to warrant a
 * backoff reset. Short sessions — e.g., an ESP32 dumping config then closing the socket — keep the growing backoff so
 * the radio has time to recover between attempts.
 */
internal fun shouldResetBackoff(hadData: Boolean, sessionUptimeMs: Long, thresholdMs: Long): Boolean =
    hadData && sessionUptimeMs >= thresholdMs

/**
 * The reconnect schedule the network transports share, [TcpTransport] and the WebSocket radio transport: the delay
 * doubles from [minMillis] up to [maxMillis], and goes back to [minMillis] only after a session that passes
 * [shouldResetBackoff]. Not thread-safe; one reconnect loop owns an instance.
 */
internal class ReconnectBackoff(
    private val minMillis: Long = MIN_BACKOFF_MILLIS,
    private val maxMillis: Long = MAX_BACKOFF_MILLIS,
    private val shortSessionThresholdMs: Long = SHORT_SESSION_THRESHOLD_MS,
) {
    /** The attempt the next [nextDelayMillis] leads to, counted from 1 since the last reset. */
    var attempt: Int = 1
        private set

    /** The delay [nextDelayMillis] would return with no floor. */
    var currentDelayMillis: Long = minMillis
        private set

    /** Records how a session ended and returns whether that reset the schedule. */
    fun onSessionEnded(hadData: Boolean, sessionUptimeMs: Long): Boolean {
        val reset = shouldResetBackoff(hadData, sessionUptimeMs, shortSessionThresholdMs)
        if (reset) {
            attempt = 1
            currentDelayMillis = minMillis
        }
        return reset
    }

    /** The delay before the next attempt, at least [floorMillis]; the delay after it doubles, up to [maxMillis]. */
    fun nextDelayMillis(floorMillis: Long = 0): Long {
        val delay = maxOf(currentDelayMillis, floorMillis)
        attempt++
        currentDelayMillis = minOf(delay * 2, maxMillis)
        return delay
    }

    companion object {
        const val MIN_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 5 * 60 * 1_000L

        /**
         * Minimum session duration for backoff to reset. Sessions shorter than this that ended in peer-EOF are treated
         * as transient firmware-side disconnects (e.g., ESP32 light sleep closing the TCP PhoneAPI session after a
         * config dump) and do NOT reset the backoff — the growing delay gives the radio time to recover between
         * attempts instead of hammering it at 1 Hz.
         */
        const val SHORT_SESSION_THRESHOLD_MS = 30_000L
    }
}
