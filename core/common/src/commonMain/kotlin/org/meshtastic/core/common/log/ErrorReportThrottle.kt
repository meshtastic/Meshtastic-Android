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
package org.meshtastic.core.common.log

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * Caps how often one repeating error signature is reported to Crashlytics and Datadog.
 *
 * A retry loop whose failure is deterministic reports forever: a single MQTT subscribe rejection produced 121,003
 * Datadog RUM error events across 72 users in fourteen days, roughly 1,680 each, which is enough to bury every other
 * signature in the same window. Rate is the signal here, not volume — the first few reports of a signature carry all
 * the diagnostic value the hundredth does.
 *
 * Suppressed events are counted, never silently discarded: the next report that gets through carries the number dropped
 * since the previous one, so a throttled signature still shows its true scale.
 */
class ErrorReportThrottle(
    private val limit: Int = DEFAULT_LIMIT,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxKeys: Int = DEFAULT_MAX_KEYS,
    private val nowMs: () -> Long,
) {
    private class Bucket(var windowStart: Long, var reported: Int, var suppressed: Int)

    private val buckets = mutableMapOf<String, Bucket>()
    private val lock = SynchronizedObject()

    /**
     * Records one occurrence of [key] and decides whether it should be reported.
     *
     * Returns `null` when the event must be suppressed, otherwise the number of occurrences suppressed since this
     * signature was last reported — `0` in the ordinary case.
     */
    fun acquire(key: String): Int? = synchronized(lock) {
        val now = nowMs()
        evictIfCrowded(now)

        val bucket = buckets.getOrPut(key) { Bucket(windowStart = now, reported = 0, suppressed = 0) }

        if (now - bucket.windowStart >= windowMs) {
            bucket.windowStart = now
            bucket.reported = 0
        }

        if (bucket.reported >= limit) {
            bucket.suppressed++
            return null
        }

        bucket.reported++
        val suppressed = bucket.suppressed
        bucket.suppressed = 0
        suppressed
    }

    /**
     * Keeps the key set bounded. Signatures embed variable data (addresses, durations), so cardinality is not bounded
     * by the number of log call sites. Expired buckets go first; if that is not enough the map is dropped wholesale,
     * which costs at most one extra window of reports for the live signatures.
     */
    private fun evictIfCrowded(now: Long) {
        if (buckets.size < maxKeys) return
        buckets.entries.removeAll { now - it.value.windowStart >= windowMs }
        if (buckets.size >= maxKeys) buckets.clear()
    }

    companion object {
        const val DEFAULT_LIMIT: Int = 10
        const val DEFAULT_WINDOW_MS: Long = 60_000L
        const val DEFAULT_MAX_KEYS: Int = 256

        /**
         * Collapses a log line into a throttling signature.
         *
         * The message prefix is what distinguishes one call site from another; the tail usually carries the variable
         * part (a device address, an elapsed time) that would otherwise make every occurrence unique.
         */
        fun signature(tag: String, message: String): String = "$tag|${message.take(SIGNATURE_MESSAGE_CHARS)}"

        private const val SIGNATURE_MESSAGE_CHARS = 64
    }
}
