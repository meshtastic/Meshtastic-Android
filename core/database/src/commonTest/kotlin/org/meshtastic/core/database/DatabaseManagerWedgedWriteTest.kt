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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the bounded-execution contract of [DatabaseManager.withDb] when a callback never returns — the shape a leaked
 * Room 3.x connection-pool permit takes, because the pool logs and retries acquisition forever instead of throwing.
 *
 * One wedged callback must not stall the manager: the caller fails on its own deadline, the wedged block is abandoned
 * rather than torn apart, the active pool is replaced, and later calls run on the replacement pool's fresh lane.
 */
class DatabaseManagerWedgedWriteTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun wedgedWithDbCallbackDoesNotStallLaterCalls() = runTest(testDispatcher) {
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS
        manager.switchActiveDatabase("addrA")
        val wedgedPool = manager.currentDb.value
        val wedgeStarted = CompletableDeferred<Unit>()
        val releaseWedge = CompletableDeferred<Unit>()

        val wedged = async {
            runCatching {
                manager.withDb {
                    wedgeStarted.complete(Unit)
                    releaseWedge.await()
                    Unit
                }
            }
        }
        wedgeStarted.await()

        advanceTimeBy(DatabaseManager.WITH_DB_TIMEOUT_MS + 1)
        val failure = wedged.await().exceptionOrNull()
        assertIs<DatabaseOperationTimeoutException>(failure, "a wedged callback must surface as a bounded failure")

        val replacement = manager.currentDb.value
        assertTrue(replacement !== wedgedPool, "wedge recovery must publish a replacement pool")

        // The lane is per pool, so this call is admitted against the replacement and never queues behind the wedge.
        var laterDb: MeshtasticDatabase? = null
        val later =
            manager.withDb { db ->
                laterDb = db
                LATER_RESULT
            }
        assertEquals(LATER_RESULT, later, "a later withDb call must complete while the earlier one is still wedged")
        assertTrue(laterDb === replacement, "later calls must run on the replacement pool")

        // The abandoned block still owns its writer registration until it eventually returns.
        assertEquals(1 to 0, manager.debugWriterCounts())
        releaseWedge.complete(Unit)
        runCurrent()
        assertEquals(
            0 to 0,
            manager.debugWriterCounts(),
            "an abandoned block must still deregister when it returns",
        )
    }

    @Test
    fun abandonedWithDbCallbackIsNeverCancelledOrReplayed() = runTest(testDispatcher) {
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS
        manager.switchActiveDatabase("addrA")
        val wedgeStarted = CompletableDeferred<Unit>()
        val releaseWedge = CompletableDeferred<Unit>()
        var invocationCount = 0
        var observedCancellation = false
        var completedNormally = false

        val wedged = async {
            runCatching {
                manager.withDb {
                    invocationCount += 1
                    wedgeStarted.complete(Unit)
                    try {
                        releaseWedge.await()
                    } catch (cancellation: CancellationException) {
                        observedCancellation = true
                        throw cancellation
                    }
                    completedNormally = true
                    Unit
                }
            }
        }
        wedgeStarted.await()

        advanceTimeBy(DatabaseManager.WITH_DB_TIMEOUT_MS + 1)
        assertNotNull(wedged.await().exceptionOrNull())
        assertFalse(observedCancellation, "abandoning must not tear apart a started DB-critical block")
        assertFalse(completedNormally, "the block is still suspended inside its critical section")

        releaseWedge.complete(Unit)
        runCurrent()
        assertTrue(completedNormally, "the abandoned block must be allowed to finish its critical section")
        assertFalse(observedCancellation)
        assertEquals(1, invocationCount, "a started callback must never be replayed automatically")
        assertEquals(0 to 0, manager.debugWriterCounts())
    }

    /**
     * Guards against `withDb` growing a lane-scoped lock or an explicit queue: a suspended callback must not stall
     * other calls on the same pool, because any callback that awaits something would then block every other write.
     *
     * This asserts the absence of app-level serialization only. The fixture's lane is the shared test dispatcher, not
     * production's `limitedParallelism(1)` view, so lane parallelism itself is out of scope here — a single-parallelism
     * dispatcher releases its slot on suspension by construction.
     */
    @Test
    fun aSuspendedCallbackDoesNotSerializeOtherCallsOnTheSamePool() = runTest(testDispatcher) {
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS
        manager.switchActiveDatabase("addrA")
        val pool = manager.currentDb.value
        val parkedStarted = CompletableDeferred<Unit>()
        val releaseParked = CompletableDeferred<Unit>()

        val parked = async {
            runCatching {
                manager.withDb {
                    parkedStarted.complete(Unit)
                    releaseParked.await()
                    Unit
                }
            }
        }
        parkedStarted.await()

        var concurrentDb: MeshtasticDatabase? = null
        val concurrent =
            manager.withDb { db ->
                concurrentDb = db
                LATER_RESULT
            }
        assertEquals(LATER_RESULT, concurrent, "a concurrent call must not wait for a suspended callback")
        assertTrue(concurrentDb === pool, "both calls are admitted against the same active pool")
        assertTrue(manager.currentDb.value === pool, "no recovery was needed, so the pool must not be replaced")

        releaseParked.complete(Unit)
        assertTrue(parked.await().isSuccess)
        assertEquals(0 to 0, manager.debugWriterCounts())
    }

    /**
     * While the windowed replacement budget is spent, the wedged pool stays published. Admitting further work against
     * it would park one more block that can never finish, so under steady ingest the parked coroutines and their writer
     * registrations would grow without bound. Admission must fail immediately instead.
     */
    @Test
    fun writesFailFastWhileWedgeRecoveryBudgetIsSpent() = runTest(testDispatcher) {
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS
        manager.switchActiveDatabase("addrA")
        val releaseWedges = CompletableDeferred<Unit>()
        var startedCallbacks = 0

        // Spend the whole replacement budget, then wedge one more call against the pool recovery now refuses.
        repeat(MAX_WEDGE_POOL_RECOVERIES_PER_WINDOW + 1) {
            val wedgeStarted = CompletableDeferred<Unit>()
            async {
                runCatching {
                    manager.withDb {
                        startedCallbacks += 1
                        wedgeStarted.complete(Unit)
                        releaseWedges.await()
                        Unit
                    }
                }
            }
            wedgeStarted.await()
            advanceTimeBy(DatabaseManager.WITH_DB_TIMEOUT_MS + 1)
        }
        val wedgedPool = manager.currentDb.value
        val abandonedCallbacks = startedCallbacks

        val failure = runCatching { manager.withDb { Unit } }.exceptionOrNull()
        assertIs<DatabaseOperationTimeoutException>(failure, "admission must fail once recovery is exhausted")
        assertEquals(abandonedCallbacks, startedCallbacks, "no further callback may be parked on the wedged pool")
        assertEquals(
            abandonedCallbacks to 0,
            manager.debugWriterCounts(),
            "a rejected call must not add a writer registration",
        )
        assertTrue(manager.currentDb.value === wedgedPool)

        // Switching to another database publishes a healthy pool, so writes resume without a manager restart.
        manager.switchActiveDatabase("addrB")
        assertEquals(LATER_RESULT, manager.withDb { LATER_RESULT }, "a different pool must still accept writes")

        releaseWedges.complete(Unit)
        runCurrent()
        assertEquals(0 to 0, manager.debugWriterCounts())
    }

    /**
     * The #6608 property, on the write path: a spent budget must be a pause, not a life sentence.
     *
     * The quarantine expires with the recovery window, and the assertions follow the whole cycle rather than stopping
     * at re-admission — stopping there would pass even if recovery never completed, which is the thing being fixed.
     * Note that expiry re-admits against the still-wedged pool (recovery was refused, so nothing replaced it), so the
     * first write back pays one deadline and its abandonment is what publishes the replacement.
     */
    @Test
    fun writesRecoverAfterTheWedgeWindowClears() = runTest(testDispatcher) {
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS
        manager.switchActiveDatabase("addrA")
        val releaseWedges = CompletableDeferred<Unit>()

        repeat(MAX_WEDGE_POOL_RECOVERIES_PER_WINDOW + 1) {
            val wedgeStarted = CompletableDeferred<Unit>()
            async {
                runCatching {
                    manager.withDb {
                        wedgeStarted.complete(Unit)
                        releaseWedges.await()
                        Unit
                    }
                }
            }
            wedgeStarted.await()
            advanceTimeBy(DatabaseManager.WITH_DB_TIMEOUT_MS + 1)
        }
        val wedgedPool = manager.currentDb.value
        assertIs<DatabaseOperationTimeoutException>(
            runCatching { manager.withDb { Unit } }.exceptionOrNull(),
            "the budget is spent, so admission must fail fast while the quarantine holds",
        )

        manager.recoveryClockMillis += POOL_RECOVERY_WINDOW_MS

        // Re-admitted, but against the pool nothing has replaced yet: this write is the sacrificial one.
        val sacrificial = async { runCatching { manager.withDb { releaseWedges.await() } } }
        runCurrent()
        advanceTimeBy(DatabaseManager.WITH_DB_TIMEOUT_MS + 1)
        assertIs<DatabaseOperationTimeoutException>(
            sacrificial.await().exceptionOrNull(),
            "the first write after expiry pays the deadline on the still-wedged pool",
        )
        assertTrue(manager.currentDb.value !== wedgedPool, "abandoning it must publish a replacement pool")

        // And the write after that lands on the healthy replacement.
        assertEquals(
            LATER_RESULT,
            manager.withDb { LATER_RESULT },
            "writes must work again once the pool is replaced",
        )

        releaseWedges.complete(Unit)
        runCurrent()
    }

    private companion object {
        const val LATER_RESULT = 42
    }
}
