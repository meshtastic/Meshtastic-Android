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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseManagerShutdownTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun closeWaitsForAdmittedWriterBeforeClosingPools() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val writerStarted = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val writerJob = launch {
            manager.withDb {
                writerStarted.complete(Unit)
                releaseWriter.await()
                null
            }
        }
        writerStarted.await()

        val closeJob = launch { manager.close() }
        runCurrent()

        assertFalse(closeJob.isCompleted, "close must wait for the admitted writer")
        assertTrue(manager.closedDatabases.isEmpty(), "no pool may close while an admitted writer is active")
        releaseWriter.complete(Unit)
        writerJob.join()
        closeJob.join()

        manager.builtDatabases.forEach { database ->
            assertEquals(1, manager.closedDatabases.count { it === database })
        }
    }

    @Test
    fun closeBoundsWedgedWriterDrainWithoutClosingReachablePools() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val writerStarted = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val writerJob = launch {
            manager.withDb {
                writerStarted.complete(Unit)
                releaseWriter.await()
                null
            }
        }
        writerStarted.await()
        val closeJob = launch { manager.close() }
        runCurrent()
        assertFalse(closeJob.isCompleted)

        advanceTimeBy(5_501)
        runCurrent()

        assertTrue(closeJob.isCompleted, "shutdown must return after the bounded writer-drain timeout")
        assertTrue(manager.closedDatabases.isEmpty(), "a writer may still resume, so no pool is safe to close")
        assertTrue(manager.deletedDatabaseNames.isEmpty(), "no reachable database file may be retired")
        assertFalse(manager.debugAcceptingWrites())

        releaseWriter.complete(Unit)
        writerJob.join()
        manager.close()
        manager.builtDatabases.forEach { database ->
            assertEquals(1, manager.closedDatabases.count { it === database })
        }
    }

    @Test
    fun closeBoundsNoncancellableManagerJobWithoutClosingOwnedPools() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val workStarted = CompletableDeferred<Unit>()
        val releaseWork = CompletableDeferred<Unit>()
        val blockedWork =
            manager.launchManagerWorkForTest {
                workStarted.complete(Unit)
                withContext(NonCancellable) { releaseWork.await() }
            }
        workStarted.await()

        val closeJob = launch { manager.close() }
        runCurrent()
        assertFalse(closeJob.isCompleted)
        advanceTimeBy(5_501)
        runCurrent()

        assertTrue(closeJob.isCompleted, "shutdown must return after its manager-job cancellation bound")
        assertFalse(manager.debugAcceptingWrites(), "shutdown must leave a terminal admission boundary")
        assertTrue(manager.closedDatabases.isEmpty(), "the blocked manager job may still reach its database")
        assertTrue(manager.deletedDatabaseNames.isEmpty(), "physical retirement must be skipped with live work")
        manager.close()
        assertTrue(
            manager.closedDatabases.isEmpty(),
            "retry must remain bounded while the manager job is still live",
        )

        releaseWork.complete(Unit)
        blockedWork.join()
        manager.close()
        manager.builtDatabases.forEach { database ->
            assertEquals(1, manager.closedDatabases.count { it === database })
        }
    }

    @Test
    fun closeUnregistersAcceptedLazyManagerJobCancelledBeforeBodyStarts() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val queuedDispatcher = StandardTestDispatcher(testScheduler)
        var bodyStarted = false
        val queuedJob = manager.launchManagerWorkForTest(queuedDispatcher) { bodyStarted = true }

        assertFalse(bodyStarted, "the queued dispatcher must hold the LAZY job before its body starts")

        manager.close()

        assertTrue(queuedJob.isCancelled, "shutdown must cancel the accepted manager job")
        assertFalse(bodyStarted, "cancelling before dispatch must not execute the job body")
        assertTrue(
            manager.closedDatabases.isNotEmpty(),
            "completion-based unregistration must let shutdown reclaim its database pools without timing out",
        )
    }

    @Test
    fun closingRejectsNewWritersSwitchesAndAssociations() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val writerStarted = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val admittedWriter = launch {
            manager.withDb {
                writerStarted.complete(Unit)
                releaseWriter.await()
                null
            }
        }
        writerStarted.await()
        val closeJob = launch { manager.close() }
        runCurrent()
        assertFalse(manager.debugAcceptingWrites())

        var rejectedWriterRan = false
        assertFailsWith<IllegalStateException> {
            manager.withDb {
                rejectedWriterRan = true
                null
            }
        }
        assertFalse(rejectedWriterRan)
        assertFailsWith<IllegalStateException> { manager.switchActiveDatabase("addrB") }
        assertFailsWith<IllegalStateException> {
            manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        }

        releaseWriter.complete(Unit)
        admittedWriter.join()
        closeJob.join()
    }

    @Test
    fun concurrentCloseCallsCleanEachPoolOnce() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val writerStarted = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        val admittedWriter = launch {
            manager.withDb {
                writerStarted.complete(Unit)
                releaseWriter.await()
                null
            }
        }
        writerStarted.await()

        val firstClose = launch { manager.close() }
        val secondClose = launch { manager.close() }
        runCurrent()
        assertFalse(firstClose.isCompleted)
        assertFalse(secondClose.isCompleted)
        releaseWriter.complete(Unit)
        admittedWriter.join()
        firstClose.join()
        secondClose.join()

        manager.builtDatabases.forEach { database ->
            assertEquals(1, manager.closedDatabases.count { it === database })
        }
    }

    @Test
    fun timeoutRecoveryCannotReopenWhileClosing() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val callbackStarted = CompletableDeferred<Unit>()
        val releaseFailure = CompletableDeferred<Unit>()
        val writerJob = async {
            try {
                manager.withDb<Unit> {
                    callbackStarted.complete(Unit)
                    releaseFailure.await()
                    throw roomPoolTimeout()
                }
                null
            } catch (failure: IllegalStateException) {
                failure
            }
        }
        callbackStarted.await()
        val buildsBeforeClose = manager.builtDatabases.size
        val closeJob = launch { manager.close() }
        runCurrent()
        assertFalse(manager.debugAcceptingWrites())

        releaseFailure.complete(Unit)
        val failure = writerJob.await()
        closeJob.join()

        assertNotNull(failure)
        assertTrue(failure.message.orEmpty().contains("Timed out attempting to acquire"))
        assertEquals(
            buildsBeforeClose,
            manager.builtDatabases.size,
            "closing must suppress timeout recovery reopen",
        )
    }

    @Test
    fun closeBeforeCurrentDbInitializationDoesNotBuildDefaultPool() = runTest(testDispatcher) {
        assertTrue(manager.builtDatabases.isEmpty())

        manager.close()

        assertTrue(manager.builtDatabases.isEmpty())
        assertFailsWith<IllegalStateException> { manager.currentDb.value }
        assertTrue(manager.builtDatabases.isEmpty(), "first access after close must not build the default pool")
    }

    @Test
    fun shutdownCannotMissDefaultPoolInitializationBeforePublication() = runTest(testDispatcher) {
        val buildStarted = CompletableDeferred<Unit>()
        val releaseBuild = CompletableDeferred<Unit>()
        manager.defaultBuildStarted = buildStarted
        manager.releaseDefaultBuild = releaseBuild

        val initialization =
            async(Dispatchers.Default) {
                try {
                    manager.currentDb.value
                    null
                } catch (failure: IllegalStateException) {
                    failure
                }
            }
        buildStarted.await()
        val closeJob = async(Dispatchers.Default) { manager.close() }
        while (manager.debugAcceptingWrites()) yield()

        releaseBuild.complete(Unit)

        assertNotNull(initialization.await(), "initialization racing shutdown must not publish the default pool")
        closeJob.await()
        val defaultDatabase = manager.builtDatabases.single()
        assertEquals(1, manager.closedDatabases.count { it === defaultDatabase })
        assertFailsWith<IllegalStateException> { manager.currentDb.value }
    }

    @Test
    fun timeoutRecoveryReopensTheLazyDefaultPool() = runTest(testDispatcher) {
        val original = manager.currentDb.value
        manager.switchActiveDatabase("addrA")
        manager.switchActiveDatabase(null)
        assertTrue(manager.currentDb.value === original, "the default pool should be cached before recovery")
        var invocationCount = 0

        val result = runCatching {
            manager.withDb<Unit> {
                invocationCount += 1
                throw roomPoolTimeout()
            }
        }

        assertNotNull(result.exceptionOrNull())
        assertEquals(1, invocationCount, "default-pool recovery must not replay the callback")
        val reopened = manager.currentDb.value
        assertTrue(reopened !== original, "the lazily registered default pool should be replaceable")

        var laterCallDb: MeshtasticDatabase? = null
        manager.withDb<Unit> { database -> laterCallDb = database }
        assertTrue(laterCallDb === reopened)

        manager.switchActiveDatabase("addrA")
        manager.switchActiveDatabase(null)
        assertTrue(
            manager.currentDb.value === reopened,
            "switching back to default must not republish the failed pool",
        )

        manager.close()
        assertEquals(1, manager.closedDatabases.count { it === original })
        assertEquals(1, manager.closedDatabases.count { it === reopened })
    }

    @Test
    fun flowPoolTimeoutReopensAndRelatchesWithoutRepeatingTheFailedPool() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val original = manager.currentDb.value
        var originalQueries = 0

        val value =
            manager
                .observeCurrentDb { database ->
                    if (database === original) {
                        flow<String> {
                            originalQueries += 1
                            throw roomPoolTimeout()
                        }
                    } else {
                        flowOf("recovered")
                    }
                }
                .first()

        assertEquals("recovered", value)
        assertEquals(1, originalQueries)
        assertTrue(manager.currentDb.value !== original)
    }

    /**
     * The decisive #6608 path: Room's pool logs an acquire timeout and retries forever instead of throwing, so a leaked
     * permit makes DAO flows hang silently. Recovery must be driven by the missing first emission, not an exception.
     */
    @Test
    fun silentlyStalledFlowIsRecoveredWithoutAnyThrownFailure() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val wedged = manager.currentDb.value
        val buildsBeforeRecovery = manager.builtDatabases.size

        val value =
            manager
                .observeCurrentDb { database ->
                    if (database === wedged) {
                        // Never emits and never fails: exactly what a leaked pool permit looks like to a caller.
                        flow<String> { awaitCancellation() }
                    } else {
                        flowOf("recovered")
                    }
                }
                .first()

        assertEquals("recovered", value)
        assertEquals(buildsBeforeRecovery + 1, manager.builtDatabases.size)
        assertTrue(manager.currentDb.value !== wedged)
    }

    /** A DAO flow that emits once and then stays legitimately quiet must not be treated as wedged. */
    @Test
    fun quietFlowAfterAFirstEmissionIsNotTreatedAsStalled() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val buildsBeforeCollection = manager.builtDatabases.size

        val collected = mutableListOf<String>()
        val collector = launch {
            manager
                .observeCurrentDb {
                    flow {
                        emit("first")
                        awaitCancellation()
                    }
                }
                .collect { collected += it }
        }
        advanceTimeBy(FLOW_FIRST_EMISSION_TIMEOUT_MS * 3)
        runCurrent()

        assertEquals(listOf("first"), collected)
        assertEquals(buildsBeforeCollection, manager.builtDatabases.size, "a quiet flow must not trigger recovery")
        collector.cancel()
    }

    @Test
    fun flowPoolRecoveryStopsAfterIntermittentFailuresFillTheRateWindow() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val buildsBeforeRecovery = manager.builtDatabases.size
        var successfulEmissions = 0

        val failure =
            assertFailsWith<IllegalStateException> {
                manager
                    .observeCurrentDb {
                        flow {
                            successfulEmissions += 1
                            emit(Unit)
                            throw roomPoolTimeout()
                        }
                    }
                    .first { false }
            }

        assertTrue(failure.message.orEmpty().contains("Timed out attempting to acquire"))
        assertEquals(MAX_FLOW_POOL_RECOVERIES_PER_WINDOW + 1, successfulEmissions)
        assertEquals(
            buildsBeforeRecovery + MAX_FLOW_POOL_RECOVERIES_PER_WINDOW,
            manager.builtDatabases.size,
            "successful emissions must not reset the Flow recovery rate window",
        )
    }

    /**
     * Regression for #6608: a permit leak that recurs "multiple times within minutes" exhausted the former
     * manager-lifetime cap, after which every later wedge was permanent. The budget is now a sliding rate window, so a
     * later storm recovers once the window clears.
     */
    @Test
    fun flowPoolRecoveryResumesAfterTheRateWindowClears() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        repeat(MAX_FLOW_POOL_RECOVERIES_PER_WINDOW + 1) {
            runCatching {
                manager
                    .observeCurrentDb {
                        flow {
                            emit(Unit)
                            throw roomPoolTimeout()
                        }
                    }
                    .first { false }
            }
        }
        val buildsAtWindowLimit = manager.builtDatabases.size
        val wedged = manager.currentDb.value

        manager.recoveryClockMillis += POOL_RECOVERY_WINDOW_MS

        val value =
            manager
                .observeCurrentDb { database ->
                    if (database === wedged) flow<String> { throw roomPoolTimeout() } else flowOf("recovered")
                }
                .first()

        assertEquals("recovered", value)
        assertEquals(buildsAtWindowLimit + 1, manager.builtDatabases.size)
    }

    /**
     * A replaced pool has no provable last user: DAO Flow collectors are untracked and Paging factories latch a raw
     * `currentDb.value`. Recovery must therefore never close a detached pool early — only [close] reclaims them —
     * otherwise a live Paging source or collector meets "Connection pool is closed".
     */
    @Test
    fun recurringFlowPoolRecoveryNeverClosesDetachedPoolsBeforeShutdown() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val recoveries = 8
        repeat(recoveries) {
            manager.recoveryClockMillis += POOL_RECOVERY_WINDOW_MS
            val wedged = manager.currentDb.value
            manager
                .observeCurrentDb { database ->
                    if (database === wedged) flow<String> { throw roomPoolTimeout() } else flowOf("ok")
                }
                .first()
        }

        assertEquals(
            recoveries,
            manager.debugDetachedPoolCount(),
            "every replaced pool must be retained until close",
        )
        assertTrue(manager.closedDatabases.isEmpty(), "recovery must never close a pool a consumer may still hold")

        manager.close()
        manager.builtDatabases.forEach { database ->
            assertEquals(1, manager.closedDatabases.count { it === database }, "close must reclaim every pool once")
        }
    }

    @Test
    fun flowPoolRecoveryStopsAfterConsecutiveReplacementPoolsFail() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val buildsBeforeRecovery = manager.builtDatabases.size
        var queryAttempts = 0

        val failure =
            assertFailsWith<IllegalStateException> {
                manager
                    .observeCurrentDb<Unit> {
                        flow {
                            queryAttempts += 1
                            throw roomPoolTimeout()
                        }
                    }
                    .first()
            }

        assertTrue(failure.message.orEmpty().contains("Timed out attempting to acquire"))
        assertEquals(MAX_CONSECUTIVE_FLOW_POOL_RECOVERIES + 1, queryAttempts)
        assertEquals(
            buildsBeforeRecovery + MAX_CONSECUTIVE_FLOW_POOL_RECOVERIES,
            manager.builtDatabases.size,
            "automatic recovery must bound the number of detached replacement pools",
        )
    }

    @Test
    fun flowPoolTimeoutRetainsItsOriginalFailureAfterShutdownClearsCurrentPublication() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val buildsBeforeFailure = manager.builtDatabases.size

        val failure =
            assertFailsWith<IllegalStateException> {
                manager
                    .observeCurrentDb<Unit> {
                        flow {
                            manager.close()
                            throw roomPoolTimeout()
                        }
                    }
                    .first()
            }

        assertTrue(failure.message.orEmpty().contains("Timed out attempting to acquire"))
        assertEquals(buildsBeforeFailure, manager.builtDatabases.size, "shutdown must suppress Flow pool recovery")
    }

    @Test
    fun flowRecoveryDoesNotSwallowUnrelatedDatabaseFailures() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val original = manager.currentDb.value
        val buildsBeforeFailure = manager.builtDatabases.size

        val failure =
            assertFailsWith<IllegalStateException> {
                manager.observeCurrentDb<String> { flow { throw IllegalStateException("query failed") } }.first()
            }

        assertEquals("query failed", failure.message)
        assertTrue(manager.currentDb.value === original)
        assertEquals(
            buildsBeforeFailure,
            manager.builtDatabases.size,
            "unrelated failures must not trigger recovery",
        )
    }

    @Test
    fun timeoutRecoveryFailureDoesNotMaskOriginalCallbackFailure() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        manager.failNextBuildWith = IllegalStateException("forced reopen failure")

        val failure = assertFailsWith<IllegalStateException> { manager.withDb<Unit> { throw roomPoolTimeout() } }

        assertTrue(failure.message.orEmpty().contains("Timed out attempting to acquire"))
        assertNull(manager.failNextBuildWith, "the reopen attempt should consume its injected failure")
    }

    @Test
    fun detachedReopenedPoolIsClosedDuringOrderlyShutdown() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val original = manager.currentDb.value
        var invocationCount = 0

        val firstResult = runCatching {
            manager.withDb<Unit> {
                invocationCount += 1
                throw roomPoolTimeout()
            }
        }

        assertNotNull(firstResult.exceptionOrNull())
        assertEquals(1, invocationCount, "pool recovery must not replay a callback that already started")
        val reopened = manager.currentDb.value
        assertTrue(reopened !== original)
        assertEquals(
            0,
            manager.closedDatabases.count { it === original },
            "reopen must not close the published pool",
        )

        var laterCallDb: MeshtasticDatabase? = null
        manager.withDb<Unit> { database -> laterCallDb = database }
        assertTrue(laterCallDb === reopened, "a later explicit call should use the recovered pool")

        manager.close()

        assertEquals(1, manager.closedDatabases.count { it === original })
        assertEquals(1, manager.closedDatabases.count { it === reopened })
    }

    /**
     * Shutdown racing final routing waits for association ownership, then observes and physically removes the newly
     * retired source exactly once. This covers the process-lifetime cleanup gap left by asynchronous retirement jobs.
     */
    @Test
    fun shutdownRacingMergeFinalizationDeletesRetiredSourceExactlyOnce() = runTest(testDispatcher) {
        setupTwoDatabases()
        val mergeCommitted = CompletableDeferred<Unit>()
        val releaseFinalization = CompletableDeferred<Unit>()
        manager.afterMergeCommitted = {
            mergeCommitted.complete(Unit)
            releaseFinalization.await()
        }

        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        mergeCommitted.await()
        val closeJob = launch { manager.close() }
        runCurrent()
        assertFalse(closeJob.isCompleted, "shutdown must wait for association finalization")

        releaseFinalization.complete(Unit)
        associateJob.join()
        closeJob.join()

        val retiredSource = buildDbName("addrB")
        assertEquals(1, manager.deletedDatabaseNames.count { it == retiredSource })
        manager.close()
        assertEquals(
            1,
            manager.deletedDatabaseNames.count { it == retiredSource },
            "second close must be idempotent",
        )
    }

    /**
     * [DatabaseManager.close] must be idempotent after logical retirement: calling close twice must not throw. The
     * first close physically cleans the logically-retired source; the second close is a safe no-op because the cache
     * and retirement set are already empty.
     */
    @Test
    fun closeIsIdempotentAndCleansRetiredSources() = runTest(testDispatcher) {
        setupTwoDatabases()
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        manager.close()
        manager.close()
    }

    /**
     * When shutdown races an in-flight association that is paused before its merge commits, [close] must wait for the
     * association to finalize (committing its retirement) before deciding ownership, then physically retire the merged
     * source exactly once. This exercises the other side of
     * [shutdownRacingMergeFinalizationDeletesRetiredSourceExactlyOnce], pausing at [TestDatabaseManager.beforeMerge]
     * rather than after commit.
     */
    @Test
    fun shutdownWaitsForPausedAssociationToFinalizeAndRetiresOnce() = runTest(testDispatcher) {
        setupTwoDatabases()
        val beforeMergeReached = CompletableDeferred<Unit>()
        val releaseMerge = CompletableDeferred<Unit>()
        manager.beforeMerge = { _, _, _ ->
            beforeMergeReached.complete(Unit)
            releaseMerge.await()
        }

        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        beforeMergeReached.await()
        val closeJob = launch { manager.close() }
        runCurrent()
        assertFalse(closeJob.isCompleted, "shutdown must wait for association finalization")

        releaseMerge.complete(Unit)
        associateJob.join()
        closeJob.join()

        val retiredSource = buildDbName("addrB")
        assertEquals(1, manager.deletedDatabaseNames.count { it == retiredSource })
        manager.close()
        assertEquals(
            1,
            manager.deletedDatabaseNames.count { it == retiredSource },
            "second close must be idempotent",
        )
    }

    /**
     * When shutdown begins before an association has been admitted, the CLOSING lifecycle state must exclude that
     * association: it cannot start, so no merge/retirement occurs and the active source keeps its ownership (it is
     * closed but never physically retired as a merged-away DB).
     */
    @Test
    fun shutdownRetainsOwnershipWhenAssociationIsBlockedFromStarting() = runTest(testDispatcher) {
        setupTwoDatabases()
        val releaseAssociate = CompletableDeferred<Unit>()
        val associationResult = CompletableDeferred<Result<Unit>>()
        val associateJob = launch {
            releaseAssociate.await()
            associationResult.complete(
                runCatching { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") },
            )
        }

        val closeJob = launch { manager.close() }
        closeJob.join()

        releaseAssociate.complete(Unit)
        associateJob.join()

        val activeSource = buildDbName("addrB")
        assertTrue(
            associationResult.await().exceptionOrNull() is IllegalStateException,
            "the association must be rejected after shutdown begins",
        )
        assertTrue(activeSource !in armableDs.data.first()[retiredDbNamesKey].orEmpty())
        assertEquals(
            0,
            manager.deletedDatabaseNames.count { it == activeSource },
            "a blocked (never-admitted) association must not retire the active source",
        )
    }
}
