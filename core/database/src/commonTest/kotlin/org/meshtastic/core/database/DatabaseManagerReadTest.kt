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

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseManagerReadTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun boundedReadUsesCapturedPublishedPoolWithoutWriterAdmissionOrReplay() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val original = manager.currentDb.value
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var invocationCount = 0

        val result = async {
            manager.withReadDb { database ->
                invocationCount += 1
                assertTrue(database === original)
                assertEquals(0 to 0, manager.debugWriterCounts())
                assertFalse(manager.debugWriterGateArmed())
                started.complete(Unit)
                release.await()
                database
            }
        }

        started.await()
        manager.switchActiveDatabase("addrB")
        val replacement = manager.currentDb.value
        assertTrue(replacement !== original)

        release.complete(Unit)
        assertTrue(result.await() === original)
        assertEquals(1, invocationCount)
        assertTrue(manager.currentDb.value === replacement)
        assertEquals(0 to 0, manager.debugWriterCounts())
        assertFalse(manager.debugWriterGateArmed())
    }

    @Test
    fun closeWaitsForBoundedReadBeforeClosingItsCapturedPool() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val captured = manager.currentDb.value
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val read = async {
            manager.withReadDb { database ->
                assertTrue(database === captured)
                started.complete(Unit)
                release.await()
                database.nodeInfoDao().getUnknownNodes()
            }
        }

        started.await()
        val closeJob = async { manager.close() }
        runCurrent()

        assertFalse(closeJob.isCompleted, "shutdown must wait for the admitted bounded read")
        assertFalse(
            captured in manager.closedDatabases,
            "the captured pool must stay open while the read is suspended",
        )
        assertFalse(manager.debugAcceptingWrites())
        assertFailsWith<IllegalStateException> { manager.withReadDb { emptyList<Nothing>() } }

        release.complete(Unit)
        assertTrue(read.await().isEmpty())
        closeJob.await()

        assertEquals(1, manager.closedDatabases.count { it === captured })
    }

    @Test
    fun evictionWaitsForBoundedReadOnSwitchedAwayPool() = runTest(testDispatcher) {
        val firstName = buildDbName("addrA")
        val secondName = buildDbName("addrB")
        manager.switchActiveDatabase("addrA")
        val captured = manager.currentDb.value
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val read = async {
            manager.withReadDb { database ->
                assertTrue(database === captured)
                started.complete(Unit)
                release.await()
                database
            }
        }

        started.await()
        assertEquals(1, manager.debugReaderCount(captured))
        manager.switchActiveDatabase("addrB")
        manager.existingDbNamesForTest = listOf(firstName, secondName)
        armableDs.edit { it[intPreferencesKey(DatabaseConstants.CACHE_LIMIT_KEY)] = 1 }
        manager.cacheLimit.first { it == 1 }
        manager.debugEnforceCacheLimit()

        assertFalse(captured in manager.closedDatabases, "eviction must defer while the captured pool has a reader")

        release.complete(Unit)
        assertTrue(read.await() === captured)
        runCurrent()

        assertEquals(0, manager.debugReaderCount(captured))
        assertEquals(1, manager.closedDatabases.count { it === captured })
        assertTrue(firstName in manager.deletedDatabaseNames)
    }

    @Test
    fun boundedReadDuringAssociationUsesSourceWithoutWaitingForWriterGate() = runTest(testDispatcher) {
        val (destination, source) = setupTwoDatabases()
        val mergeStarted = CompletableDeferred<Unit>()
        val releaseMerge = CompletableDeferred<Unit>()
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()

        manager.beforeMerge = { actualSource, actualDestination, _ ->
            assertTrue(actualSource === source)
            assertTrue(actualDestination === destination)
            assertTrue(manager.debugWriterGateArmed())
            mergeStarted.complete(Unit)
            releaseMerge.await()
        }

        val association = async { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }
        mergeStarted.await()

        val read = async {
            manager.withReadDb { database ->
                assertTrue(database === source)
                readStarted.complete(Unit)
                releaseRead.await()
                database.nodeInfoDao().getUnknownNodes()
            }
        }

        readStarted.await()
        assertEquals(1, manager.debugReaderCount(source))
        assertTrue(manager.debugWriterGateArmed())

        releaseMerge.complete(Unit)
        association.await()

        assertTrue(manager.currentDb.value === destination)
        assertFalse(source in manager.closedDatabases, "logical retirement must not close an admitted read pool")

        releaseRead.complete(Unit)
        assertTrue(read.await().isEmpty())
        assertEquals(0, manager.debugReaderCount(source))
    }

    @Test
    fun cancellingBoundedReadReleasesRefcountAndRetriesDeferredEviction() = runTest(testDispatcher) {
        val firstName = buildDbName("addrA")
        val secondName = buildDbName("addrB")
        manager.switchActiveDatabase("addrA")
        val captured = manager.currentDb.value
        val started = CompletableDeferred<Unit>()
        val neverRelease = CompletableDeferred<Unit>()

        val read = async {
            manager.withReadDb { database ->
                assertTrue(database === captured)
                started.complete(Unit)
                neverRelease.await()
            }
        }

        started.await()
        manager.switchActiveDatabase("addrB")
        manager.existingDbNamesForTest = listOf(firstName, secondName)
        armableDs.edit { it[intPreferencesKey(DatabaseConstants.CACHE_LIMIT_KEY)] = 1 }
        manager.cacheLimit.first { it == 1 }
        manager.debugEnforceCacheLimit()

        assertEquals(1, manager.debugReaderCount(captured))
        assertFalse(captured in manager.closedDatabases)

        read.cancelAndJoin()
        runCurrent()

        assertEquals(0, manager.debugReaderCount(captured))
        assertEquals(1, manager.closedDatabases.count { it === captured })
        assertTrue(firstName in manager.deletedDatabaseNames)
    }

    @Test
    fun concurrentBoundedReadersKeepPoolUntilFinalReaderReleases() = runTest(testDispatcher) {
        val firstName = buildDbName("addrA")
        val secondName = buildDbName("addrB")
        manager.switchActiveDatabase("addrA")
        val captured = manager.currentDb.value
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()

        val firstRead = async {
            manager.withReadDb { database ->
                assertTrue(database === captured)
                firstStarted.complete(Unit)
                releaseFirst.await()
                database
            }
        }
        val secondRead = async {
            manager.withReadDb { database ->
                assertTrue(database === captured)
                secondStarted.complete(Unit)
                releaseSecond.await()
                database
            }
        }

        firstStarted.await()
        secondStarted.await()
        assertEquals(2, manager.debugReaderCount(captured))

        manager.switchActiveDatabase("addrB")
        manager.existingDbNamesForTest = listOf(firstName, secondName)
        armableDs.edit { it[intPreferencesKey(DatabaseConstants.CACHE_LIMIT_KEY)] = 1 }
        manager.cacheLimit.first { it == 1 }
        manager.debugEnforceCacheLimit()

        releaseFirst.complete(Unit)
        assertTrue(firstRead.await() === captured)
        runCurrent()

        assertEquals(1, manager.debugReaderCount(captured))
        assertFalse(captured in manager.closedDatabases, "one remaining reader must keep the pool open")

        releaseSecond.complete(Unit)
        assertTrue(secondRead.await() === captured)
        runCurrent()

        assertEquals(0, manager.debugReaderCount(captured))
        assertEquals(1, manager.closedDatabases.count { it === captured })
        assertTrue(firstName in manager.deletedDatabaseNames)
    }

    @Test
    fun evictionSelectionPreventsVictimFromAcquiringNewReader() = runTest(testDispatcher) {
        val victimName = buildDbName("addrA")
        val activeName = buildDbName("addrB")
        manager.switchActiveDatabase("addrA")
        val victim = manager.currentDb.value
        manager.switchActiveDatabase("addrB")
        manager.existingDbNamesForTest = listOf(victimName, activeName)
        armableDs.edit { it[intPreferencesKey(DatabaseConstants.CACHE_LIMIT_KEY)] = 1 }
        manager.cacheLimit.first { it == 1 }

        val victimSelected = CompletableDeferred<Unit>()
        val releaseEviction = CompletableDeferred<Unit>()
        manager.beforeCloseCached = { dbName ->
            if (dbName == victimName) {
                victimSelected.complete(Unit)
                releaseEviction.await()
            }
        }

        val eviction = async { manager.debugEnforceCacheLimit() }
        victimSelected.await()

        val reacquire = async {
            manager.switchActiveDatabase("addrA")
            manager.withReadDb { it }
        }
        runCurrent()

        assertFalse(reacquire.isCompleted, "switching back to the selected victim must wait for eviction")
        assertEquals(0, manager.debugReaderCount(victim))

        releaseEviction.complete(Unit)
        eviction.await()
        val reopened = reacquire.await()

        assertEquals(1, manager.closedDatabases.count { it === victim })
        assertTrue(reopened !== victim, "a later read must use a newly opened pool, not the evicted victim")
        assertEquals(0, manager.debugReaderCount(victim))
    }

    @Test
    fun boundedReadPropagatesFailureWithoutReplay() = runTest(testDispatcher) {
        var invocationCount = 0

        val failure =
            assertFailsWith<IllegalStateException> {
                manager.withReadDb<Unit> {
                    invocationCount += 1
                    error("read failed")
                }
            }

        assertEquals("read failed", failure.message)
        assertEquals(1, invocationCount)
        assertEquals(0, manager.debugReaderCount())
        assertEquals(0 to 0, manager.debugWriterCounts())
        assertFalse(manager.debugWriterGateArmed())
    }
}
