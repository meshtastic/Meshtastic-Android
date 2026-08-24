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
package org.meshtastic.core.database

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseManagerBackfillTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun activeBackfillDelaysAssociationUntilItFinishes() = runTest(testDispatcher) {
        val (destination, source) = setupTwoDatabases()
        val backfillStarted = CompletableDeferred<MeshtasticDatabase>()
        val releaseBackfill = CompletableDeferred<Unit>()
        manager.backfillStarted = backfillStarted
        manager.backfillRelease = releaseBackfill
        val mergeStarted = CompletableDeferred<Unit>()
        manager.beforeMerge = { _, _, _ -> mergeStarted.complete(Unit) }

        val backfillJob = launch { manager.backfillSearchIndexIfNeeded(source) }
        assertEquals(source, backfillStarted.await())
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        runCurrent()

        assertFalse(mergeStarted.isCompleted, "merge must wait for the admitted backfill writer")
        assertTrue(manager.debugWriterGateArmed())
        releaseBackfill.complete(Unit)
        backfillJob.join()
        associateJob.join()

        assertTrue(mergeStarted.isCompleted)
        assertEquals(destination, manager.currentDb.value)
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
    }

    /** A delayed job scheduled for an old DB skips work after admission selects a different active DB. */
    @Test
    fun staleScheduledBackfillSkipsWorkAndBalancesAdmission() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val staleDb = manager.currentDb.value
        manager.switchActiveDatabase("addrB")

        manager.backfillSearchIndexIfNeeded(staleDb)

        assertTrue(manager.backfillDatabases.isEmpty(), "stale scheduled DB must not be backfilled")
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
    }

    /** Cancelling a backfill suspended at admission leaves no registration behind and does not disturb the merge. */
    @Test
    fun cancellationWhileBackfillWaitsAtGateLeavesNoWriterLeak() = runTest(testDispatcher) {
        val (destination, source) = setupTwoDatabases()
        val mergeStarted = CompletableDeferred<Unit>()
        val releaseMerge = CompletableDeferred<Unit>()
        manager.beforeMerge = { _, _, _ ->
            mergeStarted.complete(Unit)
            releaseMerge.await()
        }
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        mergeStarted.await()

        val backfillJob = launch { manager.backfillSearchIndexIfNeeded(source) }
        runCurrent()
        backfillJob.cancelAndJoin()
        assertTrue(manager.backfillDatabases.isEmpty(), "cancelled gate waiter must not start backfill work")
        val (writersDuringMerge, waitersDuringMerge) = manager.debugWriterCounts()
        assertEquals(0, writersDuringMerge)
        assertEquals(0, waitersDuringMerge)

        releaseMerge.complete(Unit)
        associateJob.join()
        assertEquals(destination, manager.currentDb.value)
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
    }
}
