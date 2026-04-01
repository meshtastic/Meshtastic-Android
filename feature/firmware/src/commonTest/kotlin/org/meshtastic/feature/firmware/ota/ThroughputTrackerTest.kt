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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class ThroughputTrackerTest {

    class FakeTimeSource : TimeSource {
        var currentTime = 0L

        override fun markNow(): TimeMark = object : TimeMark {
            override fun elapsedNow() = currentTime.milliseconds

            override fun plus(duration: kotlin.time.Duration) = throw NotImplementedError()

            override fun minus(duration: kotlin.time.Duration) = throw NotImplementedError()
        }

        fun advanceBy(ms: Long) {
            currentTime += ms
        }
    }

    @Test
    fun testThroughputCalculation() {
        val fakeTimeSource = FakeTimeSource()
        val tracker = ThroughputTracker(windowSize = 10, timeSource = fakeTimeSource)

        assertEquals(0, tracker.bytesPerSecond())

        tracker.record(0)
        fakeTimeSource.advanceBy(1000) // 1 second later

        tracker.record(1024) // Sent 1024 bytes
        assertEquals(1024, tracker.bytesPerSecond())

        fakeTimeSource.advanceBy(1000)
        tracker.record(2048) // Sent another 1024 bytes
        assertEquals(1024, tracker.bytesPerSecond())

        fakeTimeSource.advanceBy(500)
        tracker.record(3072) // Sent 1024 bytes in 500ms

        // Total duration from oldest to newest:
        // oldest: 0ms, 0 bytes
        // newest: 2500ms, 3072 bytes
        // duration = 2500, delta = 3072. bytes/sec = (3072*1000)/2500 = 1228
        assertEquals(1228, tracker.bytesPerSecond())
    }
}
