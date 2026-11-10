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
package org.meshtastic.core.data.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestTimerTest {

    @Test
    fun appendDuration_withoutStart_returnsTextUnchanged() {
        val timer = RequestTimer()

        assertEquals("base", timer.appendDuration(requestId = 1, text = "base", logLabel = "Test"))
    }

    @Test
    fun appendDuration_afterStart_appendsDurationLine() {
        val timer = RequestTimer()
        timer.start(requestId = 7)

        val result = timer.appendDuration(requestId = 7, text = "base", logLabel = "Test")

        assertTrue(result.startsWith("base\n\nDuration: "), "expected a duration suffix, got: $result")
        assertTrue(result.endsWith(" s"))
    }

    @Test
    fun appendDuration_consumesStartTime_soSecondCallIsUnchanged() {
        val timer = RequestTimer()
        timer.start(requestId = 7)

        timer.appendDuration(requestId = 7, text = "first", logLabel = "Test")
        // The start time is single-use; a second response for the same id gets no duration.
        assertEquals("second", timer.appendDuration(requestId = 7, text = "second", logLabel = "Test"))
    }

    @Test
    fun start_tracksRequestsIndependently() {
        val timer = RequestTimer()
        timer.start(requestId = 1)
        timer.start(requestId = 2)

        // Consuming one id must not affect the other.
        timer.appendDuration(requestId = 1, text = "a", logLabel = "Test")
        assertTrue(timer.appendDuration(requestId = 2, text = "b", logLabel = "Test").contains("Duration: "))
    }

    @Test
    fun appendDuration_fromAnotherNode_leavesTextAndStartTimeUntouched() {
        val timer = RequestTimer()
        timer.start(requestId = 9, destination = 100)

        assertEquals("base", timer.appendDuration(requestId = 9, text = "base", logLabel = "Test", from = 200))
        assertTrue(timer.has(requestId = 9, from = 100))
        assertTrue(
            timer.appendDuration(requestId = 9, text = "base", logLabel = "Test", from = 100).contains("Duration: "),
        )
    }

    @Test
    fun has_withMismatchedSender_isFalse() {
        val timer = RequestTimer()
        timer.start(requestId = 9, destination = 100)

        assertFalse(timer.has(requestId = 9, from = 200))
        assertTrue(timer.has(requestId = 9, from = 100))
    }

    @Test
    fun has_isTrueOnlyBetweenStartAndResponse() {
        val timer = RequestTimer()
        assertFalse(timer.has(requestId = 3))

        timer.start(requestId = 3)
        assertTrue(timer.has(requestId = 3))

        timer.appendDuration(requestId = 3, text = "a", logLabel = "Test")
        assertFalse(timer.has(requestId = 3))
    }
}
