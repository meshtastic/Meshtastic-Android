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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DatabaseManagerWriterAdmissionTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun writerArrivingDuringDrainIsBlockedAndReleasedToDestOnCommit() = runTest(testDispatcher) {
        val (dbA, _) = setupTwoDatabases()
        manager.performRealMerge = false

        // Hold a pre-existing source writer so the drain stays pending and a new writer can arrive into the gate.
        val preGate = CompletableDeferred<Unit>()
        val preWriter = launch {
            manager.withDb {
                preGate.await()
                null
            }
        }
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }

        val newWriterStarted = CompletableDeferred<Unit>()
        val newWriterDb = CompletableDeferred<MeshtasticDatabase>()
        var newWriterInvocationCount = 0
        val newWriterJob = launch {
            manager.withDb { db ->
                newWriterInvocationCount += 1
                newWriterStarted.complete(Unit)
                newWriterDb.complete(db)
                null
            }
        }

        // The new writer must be suspended at the admission gate, not running its block.
        assertFalse(newWriterStarted.isCompleted, "new writer must block at the admission gate during the drain")
        assertFalse(newWriterDb.isCompleted, "new writer must not have captured a DB yet")

        // Release the pre-existing writer: drain completes, merge commits, gate releases onto dest.
        preGate.complete(Unit)
        newWriterJob.join()
        associateJob.join()
        preWriter.join()

        assertTrue(newWriterStarted.isCompleted, "new writer must be released after the merge commits")
        assertTrue(associateJob.isCompleted, "association must complete")
        assertEquals(1, newWriterInvocationCount, "blocked writer must run exactly once")
        assertEquals(dbA, newWriterDb.await(), "new writer released onto destination after successful merge")
        assertEquals(dbA, manager.currentDb.value, "destination is canonical after commit")
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
    }

    /** A successful merge releases the blocked writer onto destination (not source). */
    @Test
    fun successfulMergeReleasesBlockedWriterOntoDestination() = runTest(testDispatcher) {
        val (dbA, _) = setupTwoDatabases()
        manager.performRealMerge = false

        val preGate = CompletableDeferred<Unit>()
        val preWriter = launch {
            manager.withDb {
                preGate.await()
                null
            }
        }
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }

        val newWriterDb = CompletableDeferred<MeshtasticDatabase>()
        var newWriterInvocationCount = 0
        val newWriterJob = launch {
            manager.withDb { db ->
                newWriterInvocationCount += 1
                newWriterDb.complete(db)
                null
            }
        }

        assertFalse(newWriterDb.isCompleted, "new writer must block during the drain")
        preGate.complete(Unit)
        val usedDb = newWriterDb.await()
        assertEquals(dbA, usedDb, "blocked writer released onto destination after successful merge")
        assertEquals(dbA, manager.currentDb.value, "destination is canonical after commit")

        associateJob.join()
        preWriter.join()
        newWriterJob.join()
        assertTrue(associateJob.isCompleted, "association must complete")
        assertEquals(1, newWriterInvocationCount, "blocked writer must run exactly once")
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
    }

    /** A pre-commit merge failure releases the blocked writer onto source; the destination is never activated. */
    @Test
    fun preCommitMergeFailureReleasesBlockedWriterOntoSource() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()

        manager.failMergeWith = RuntimeException("simulated pre-commit merge failure")

        val preGate = CompletableDeferred<Unit>()
        val preWriter = launch {
            manager.withDb {
                preGate.await()
                null
            }
        }
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }

        val newWriterDb = CompletableDeferred<MeshtasticDatabase>()
        val newWriterJob = launch {
            manager.withDb { db ->
                newWriterDb.complete(db)
                null
            }
        }

        preGate.complete(Unit)
        val usedDb = newWriterDb.await()
        assertEquals(dbB, usedDb, "blocked writer released onto source after pre-commit merge failure")
        associateJob.join()
        assertEquals(dbB, manager.currentDb.value, "source remains active after merge failure")

        preWriter.join()
        newWriterJob.join()
    }

    /**
     * Cancelling the attempt while a writer is blocked at the gate releases the gate onto source and leaves no waiter
     * leaked.
     */
    @Test
    fun cancellationReleasesAdmissionGateOntoSource() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()

        val preGate = CompletableDeferred<Unit>()
        val preWriter = launch {
            manager.withDb {
                preGate.await()
                null
            }
        }
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }

        val newWriterDb = CompletableDeferred<MeshtasticDatabase>()
        val newWriterJob = launch {
            manager.withDb { db ->
                newWriterDb.complete(db)
                null
            }
        }

        associateJob.cancelAndJoin()
        val usedDb = newWriterDb.await()
        assertEquals(dbB, usedDb, "gate released onto source when the attempt is cancelled")
        assertEquals(dbB, manager.currentDb.value, "source active after cancellation")

        preGate.complete(Unit)
        preWriter.join()
        newWriterJob.join()
    }

    /**
     * After a full association cycle with an admission-gated writer, the writer tracker holds no live writers and no
     * pending drain waiters — counts and waiters are balanced.
     */
    @Test
    fun writerCountsAndDrainWaitersRemainBalanced() = runTest(testDispatcher) {
        val (destination, _) = setupTwoDatabases()
        manager.performRealMerge = false

        val preGate = CompletableDeferred<Unit>()
        val preWriter = launch {
            manager.withDb {
                preGate.await()
                null
            }
        }
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        var newWriterInvocationCount = 0
        val newWriterDb = CompletableDeferred<MeshtasticDatabase>()
        val newWriterJob = launch {
            manager.withDb { db ->
                newWriterInvocationCount += 1
                newWriterDb.complete(db)
                null
            }
        }

        preGate.complete(Unit)
        associateJob.join()
        preWriter.join()
        newWriterJob.join()

        assertTrue(associateJob.isCompleted, "association must complete")
        assertEquals(1, newWriterInvocationCount, "blocked writer must run exactly once")
        assertEquals(destination, newWriterDb.await(), "blocked writer must use destination")
        assertEquals(destination, manager.currentDb.value, "destination must be canonical")
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers, "no leaked writers after association")
        assertEquals(0, waiters, "no pending drain waiters after association")
    }

    @Test
    fun closedPoolFailureAfterSwitchDoesNotReplayCallback() =
        runTest(testDispatcher) { assertClosedPoolFailureIsNotReplayedAfterSwitch() }

    /** A gate that never completes fails admission after a bounded wait without running or registering the writer. */
    @Test
    fun writerAdmissionGateTimeoutIsBoundedAndBalanced() = runTest(testDispatcher) {
        setupTwoDatabases()
        val mergeStarted = CompletableDeferred<Unit>()
        val releaseMerge = CompletableDeferred<Unit>()
        manager.beforeMerge = { _, _, _ ->
            mergeStarted.complete(Unit)
            releaseMerge.await()
        }

        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        mergeStarted.await()
        var writerBlockRan = false
        val waitingWriter = async {
            try {
                manager.withDb {
                    writerBlockRan = true
                    null
                }
                null
            } catch (failure: IllegalStateException) {
                failure
            }
        }

        advanceTimeBy(30_001)
        val failure = waitingWriter.await()
        assertNotNull(failure)
        assertTrue(failure.message.orEmpty().contains("Timed out waiting 30000ms"))
        assertFalse(writerBlockRan, "a timed-out writer must never run its callback")
        val (writersDuringMerge, waitersDuringMerge) = manager.debugWriterCounts()
        assertEquals(0, writersDuringMerge, "gate wait must not register a writer")
        assertEquals(0, waitersDuringMerge)

        releaseMerge.complete(Unit)
        associateJob.join()
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
    }
}
