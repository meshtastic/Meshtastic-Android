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
package org.meshtastic.core.repository

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NodeRestartTrackerTest {

    @Test
    fun `expectRestart opens the window`() = runTest {
        val tracker = NodeRestartTracker(backgroundScope)

        tracker.expectRestart()

        assertTrue(tracker.restartExpected.value)
    }

    @Test
    fun `onConnected closes the window`() = runTest {
        val tracker = NodeRestartTracker(backgroundScope)
        tracker.expectRestart()

        tracker.onConnected()

        assertFalse(tracker.restartExpected.value)
    }

    @Test
    fun `window expires if the node never comes back`() = runTest {
        val tracker = NodeRestartTracker(backgroundScope)
        tracker.expectRestart(window = 10.seconds)

        advanceTimeBy(9.seconds)
        runCurrent()
        assertTrue(tracker.restartExpected.value)

        advanceTimeBy(2.seconds)
        runCurrent()
        assertFalse(tracker.restartExpected.value)
    }

    @Test
    fun `a later expectRestart extends an open window`() = runTest {
        val tracker = NodeRestartTracker(backgroundScope)
        tracker.expectRestart(window = 10.seconds)

        advanceTimeBy(8.seconds)
        runCurrent()
        tracker.expectRestart(window = 10.seconds)

        advanceTimeBy(8.seconds)
        runCurrent()
        assertTrue(tracker.restartExpected.value)
    }
}
