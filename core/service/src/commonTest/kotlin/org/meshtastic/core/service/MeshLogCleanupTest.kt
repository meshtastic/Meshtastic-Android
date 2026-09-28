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

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.MeshLogRetention
import org.meshtastic.core.testing.FakeMeshLogPrefs
import org.meshtastic.core.testing.FakeMeshLogRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class MeshLogCleanupTest {
    private val repository = FakeMeshLogRepository()
    private val prefs =
        FakeMeshLogPrefs().apply {
            setLoggingEnabled(true)
            setRetentionDays(7)
        }
    private val cleanup = MeshLogCleanup(repository, prefs)

    @Test
    fun testRunHourlyFirstPrunesShortlyAfterStartThenEveryHour() = runTest {
        backgroundScope.launch { cleanup.runHourly() }

        advance(MeshLogCleanup.FIRST_RUN_DELAY - 1.milliseconds)
        assertEquals(0, repository.deleteLogsOlderThanCalls)
        advance(1.milliseconds)
        assertEquals(1, repository.deleteLogsOlderThanCalls)
        assertEquals(7, repository.lastDeletedOlderThan)

        advance(MeshLogCleanup.INTERVAL - 1.milliseconds)
        assertEquals(1, repository.deleteLogsOlderThanCalls)
        advance(1.milliseconds)
        assertEquals(2, repository.deleteLogsOlderThanCalls)
    }

    @Test
    fun testRunHourlyKeepsItsScheduleAfterAFailedPass() = runTest {
        repository.beforeDeleteLogsOlderThan = { if (repository.deleteLogsOlderThanCalls == 1) error("disk I/O error") }
        backgroundScope.launch { cleanup.runHourly() }

        advance(MeshLogCleanup.FIRST_RUN_DELAY)
        assertEquals(1, repository.deleteLogsOlderThanCalls)
        assertNull(repository.lastDeletedOlderThan)

        advance(MeshLogCleanup.INTERVAL)
        assertEquals(2, repository.deleteLogsOlderThanCalls)
        assertEquals(7, repository.lastDeletedOlderThan)
    }

    @Test
    fun testRunHourlyStopsWhenItsScopeIsCancelled() = runTest {
        val schedule = backgroundScope.launch { cleanup.runHourly() }
        advance(MeshLogCleanup.FIRST_RUN_DELAY)
        assertEquals(1, repository.deleteLogsOlderThanCalls)

        schedule.cancel()
        advance(MeshLogCleanup.INTERVAL * 3)

        assertEquals(1, repository.deleteLogsOlderThanCalls)
    }

    @Test
    fun testRunOnceSkipsWhenLoggingIsDisabled() = runTest {
        prefs.setLoggingEnabled(false)

        cleanup.runOnce()

        assertEquals(0, repository.deleteLogsOlderThanCalls)
    }

    @Test
    fun testRunOnceSkipsWhenRetentionKeepsLogsForever() = runTest {
        prefs.setRetentionDays(MeshLogRetention.KEEP_FOREVER)

        cleanup.runOnce()

        assertEquals(0, repository.deleteLogsOlderThanCalls)
    }

    @Test
    fun testRunOnceKeepsTheOneHourSentinel() = runTest {
        prefs.setRetentionDays(MeshLogRetention.ONE_HOUR)

        cleanup.runOnce()

        assertEquals(MeshLogRetention.ONE_HOUR, repository.lastDeletedOlderThan)
    }

    private fun TestScope.advance(by: Duration) {
        advanceTimeBy(by)
        runCurrent()
    }
}
