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
package org.meshtastic.feature.firmware.ota

import kotlin.time.TimeSource

/**
 * Sliding window throughput tracker to calculate current transfer speed in bytes per second. Adapted from kmp-ble's
 * DfuProgress throughput tracking.
 */
class ThroughputTracker(private val windowSize: Int = 10, private val timeSource: TimeSource = TimeSource.Monotonic) {
    private val timestamps = LongArray(windowSize)
    private val byteCounts = LongArray(windowSize)
    private var head = 0
    private var size = 0
    private val startMark = timeSource.markNow()

    /** Record that [bytesSent] total bytes have been sent at the current time. */
    fun record(bytesSent: Long) {
        val elapsed = startMark.elapsedNow().inWholeMilliseconds
        timestamps[head] = elapsed
        byteCounts[head] = bytesSent
        head = (head + 1) % windowSize
        if (size < windowSize) size++
    }

    /** Returns the current throughput in bytes per second based on the sliding window. */
    fun bytesPerSecond(): Long {
        if (size < 2) return 0

        val oldestIdx = if (size < windowSize) 0 else head
        val newestIdx = (head - 1 + windowSize) % windowSize

        val durationMs = timestamps[newestIdx] - timestamps[oldestIdx]
        if (durationMs <= 0) return 0

        val deltaBytes = byteCounts[newestIdx] - byteCounts[oldestIdx]
        return (deltaBytes * 1000) / durationMs
    }
}
