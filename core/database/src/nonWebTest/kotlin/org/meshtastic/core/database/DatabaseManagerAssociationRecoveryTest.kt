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
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.entity.MyNodeEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseManagerAssociationRecoveryTest : DatabaseManagerTestFixture() {

    @BeforeTest fun setUp() = setUpFixture()

    @AfterTest fun tearDown() = tearDownFixture()

    @Test
    fun staleAddressIdentityCannotClaimTheActiveDatabase() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        val active = manager.currentDb.value

        // meshPrefs can publish addrB before switchActiveDatabase finishes while the previous node identity is
        // still
        // visible. The identity emission is bound to addrB and must not claim addrA's currently active database.
        manager.associateDevice(
            address = "addrB",
            nodeNum = 123,
            deviceId = "deadbeefdeadbeef",
            isSessionActive = { true },
        )

        assertTrue(manager.currentDb.value === active)
        val prefs = armableDs.data.first()
        assertNull(prefs[stringPreferencesKey("device_db_for:${deviceKeyHex("deadbeefdeadbeef")}")])
        assertNull(prefs[stringPreferencesKey("node_db_for:123")])
        assertNull(prefs[stringPreferencesKey("addr_db_for:ADDRB")])
    }

    // Association and recovery tests

    /**
     * 1 + 2 combined: A writer holding the source DB's withDb lane causes the drain to time out, aborting the merge and
     * restoring the source. After releasing the writer, a retry succeeds — the merge commits, dest becomes canonical,
     * and the active DB switches.
     */
    @Test
    fun drainTimeoutRestoresSourceAndRetrySucceeds() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()
        assertEquals(dbB, manager.currentDb.value, "addrB's DB should be active before association")

        // Hold a withDb writer on addrB's DB so drainWriters can't complete.
        val gate = CompletableDeferred<Unit>()
        val writerJob = launch {
            manager.withDb {
                gate.await()
                null
            }
        }

        // associateDevice should reach the merge branch, publish dest, then time out on the drain.
        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }

        // Advance virtual time past WRITER_DRAIN_TIMEOUT_MS (5_000ms) to trigger the drain timeout.
        advanceTimeBy(5_501)
        assertTrue(associateJob.isCompleted, "associateDevice should complete after drain timeout")
        assertEquals(dbB, manager.currentDb.value, "source (addrB) should be restored after drain timeout")

        // Release the writer gate so the source DB quiesces.
        gate.complete(Unit)
        writerJob.join()

        // Retry: with no writer in-flight, the drain succeeds and the merge commits.
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        assertEquals(
            dbA,
            manager.currentDb.value,
            "dest (addrA's canonical DB) should be active after successful merge",
        )
    }

    /**
     * 3: Cancelling [associateDevice] during the writer-drain wait propagates [CancellationException], restores the
     * source DB, and does not leak the drain waiter — a subsequent retry succeeds.
     */
    @Test
    fun cancellationDuringDrainRestoresSource() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()

        val gate = CompletableDeferred<Unit>()
        val writerJob = launch {
            manager.withDb {
                gate.await()
                null
            }
        }

        val associateJob = launch { manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef") }

        // Cancel while suspended in drainWriters (before the 5s timeout fires).
        associateJob.cancelAndJoin()

        assertTrue(associateJob.isCancelled, "associateDevice coroutine should be cancelled")
        assertEquals(
            dbB,
            manager.currentDb.value,
            "source should be restored after cancellation (NonCancellable publishActiveDb)",
        )

        // Release the writer and retry — if the drain waiter leaked, the retry would also stall.
        gate.complete(Unit)
        writerJob.join()

        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        assertEquals(
            dbA,
            manager.currentDb.value,
            "retry should succeed after cancellation cleanup — dest is canonical",
        )
    }

    /**
     * 4: A successful association persists device, node, and address routing metadata in one atomic DataStore edit, all
     * pointing to the same canonical DB.
     */
    @Test
    fun atomicRoutingMetadataAllPointToCanonical() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrA")
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        manager.switchActiveDatabase("addrB")

        // No writer is held, so the drain succeeds immediately and the merge commits.
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        val prefs = armableDs.data.first()
        val deviceClaim = prefs[stringPreferencesKey("device_db_for:${deviceKeyHex("deadbeefdeadbeef")}")]
        val nodeClaim = prefs[stringPreferencesKey("node_db_for:123")]
        val addrClaim = prefs[stringPreferencesKey("addr_db_for:ADDRB")]

        assertNotNull(deviceClaim, "device-id claim should be persisted")
        assertNotNull(nodeClaim, "node-num claim should be persisted")
        assertNotNull(addrClaim, "address alias should be persisted")
        assertEquals(deviceClaim, nodeClaim, "device and node claims point to the same DB")
        assertEquals(deviceClaim, addrClaim, "address alias points to the same canonical DB")
        assertNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    @Test
    fun pendingRouteIsPersistedBeforeMergeAndRemovedWithFinalRouting() = runTest(testDispatcher) {
        setupTwoDatabases()
        var pendingObserved = false
        manager.beforeMerge = { _, _, sourceName ->
            val prefs = armableDs.data.first()
            assertEquals(sourceName, prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
            assertEquals(buildDbName("addrA"), prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
            pendingObserved = true
        }

        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        assertTrue(pendingObserved, "pending route must be durable before merge starts")
        val prefs = armableDs.data.first()
        assertNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    @Test
    fun preCommitMergeFailureClearsPendingRouteAndKeepsSource() = runTest(testDispatcher) {
        val (_, source) = setupTwoDatabases()
        manager.failMergeWith = RuntimeException("pre-commit failure")

        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        assertEquals(source, manager.currentDb.value)
        val prefs = armableDs.data.first()
        assertNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
        assertFalse(manager.debugWriterGateArmed())
    }

    @Test
    fun stalePendingRouteWithoutMarkerIsDiscarded() = runTest(testDispatcher) {
        manager.switchActiveDatabase("addrB")
        val fallback = manager.currentDb.value
        manager.switchActiveDatabase(null)
        armableDs.edit {
            it[stringPreferencesKey("pending_source_db_for:ADDRB")] = buildDbName("addrB")
            it[stringPreferencesKey("pending_destination_db_for:ADDRB")] = buildDbName("addrA")
        }

        manager.switchActiveDatabase("addrB")

        assertEquals(fallback, manager.currentDb.value, "marker-free pending route must use the normal fallback")
        val prefs = armableDs.data.first()
        assertNull(prefs[stringPreferencesKey("addr_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    @Test
    fun committedPendingRouteForCurrentAddressSwitchesToRepairedDestination() = runTest(testDispatcher) {
        val (destination, source) = setupTwoDatabases()
        val sourceName = buildDbName("addrB")
        val destinationName = buildDbName("addrA")
        armableDs.edit {
            it[stringPreferencesKey("pending_source_db_for:ADDRB")] = sourceName
            it[stringPreferencesKey("pending_destination_db_for:ADDRB")] = destinationName
        }
        manager.markerVerification = { database, pendingSource ->
            database === destination && pendingSource == sourceName
        }

        manager.switchActiveDatabase("addrB")

        assertTrue(manager.currentDb.value === destination)
        assertTrue(manager.currentDb.value !== source)
        val prefs = armableDs.data.first()
        assertEquals(destinationName, prefs[stringPreferencesKey("addr_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    @Test
    fun committedPendingRouteRepairsStaleExistingAliasBeforePublishing() = runTest(testDispatcher) {
        val (destination, _) = setupTwoDatabases()
        manager.afterMergeCommitted = {
            armableDs.armFailBeforeCommit(RuntimeException("simulated final-routing failure"))
        }
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        armableDs.edit { it[stringPreferencesKey("addr_db_for:ADDRB")] = buildDbName("addrB") }

        manager.switchActiveDatabase(null)
        manager.switchActiveDatabase("addrB")

        assertEquals(destination, manager.currentDb.value, "merge marker must override the stale source alias")
        val repaired = armableDs.data.first()
        assertEquals(buildDbName("addrA"), repaired[stringPreferencesKey("addr_db_for:ADDRB")])
        assertNull(repaired[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(repaired[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    @Test
    fun recoveredSourceAfterDevicePublicationIsProtectedUntilShutdown() = runTest(testDispatcher) {
        manager.switchActiveDatabase("already-published")
        manager.switchActiveDatabase(null)
        val sourceName = buildDbName("addrB")
        val destinationName = buildDbName("addrA")
        armableDs.edit {
            it[stringPreferencesKey("pending_source_db_for:ADDRB")] = sourceName
            it[stringPreferencesKey("pending_destination_db_for:ADDRB")] = destinationName
        }
        manager.markerVerification = { _, _ -> true }

        manager.switchActiveDatabase("addrB")

        assertTrue(manager.deletedDatabaseNames.isEmpty(), "same-process recovery must defer source deletion")
        assertTrue(sourceName in armableDs.data.first()[retiredDbNamesKey].orEmpty())

        manager.close()
        assertEquals(listOf(sourceName), manager.deletedDatabaseNames)
    }

    @Test
    fun cancellationAfterRecoveredRouteCommitStillProtectsPublishedSource() = runTest(testDispatcher) {
        manager.switchActiveDatabase("already-published")
        manager.switchActiveDatabase(null)
        val sourceName = buildDbName("addrB")
        val destinationName = buildDbName("addrA")
        armableDs.edit {
            it[stringPreferencesKey("pending_source_db_for:ADDRB")] = sourceName
            it[stringPreferencesKey("pending_destination_db_for:ADDRB")] = destinationName
        }
        manager.markerVerification = { _, _ -> true }

        val recovery = launch {
            armableDs.armCancelAfterCommit(coroutineContext[Job]!!)
            manager.switchActiveDatabase("addrB")
        }
        recovery.join()

        assertTrue(recovery.isCancelled, "recovery caller should observe cancellation after the durable commit")
        assertTrue(
            manager.debugIsLogicallyRetired(sourceName),
            "the published source must remain eviction-protected",
        )
        assertTrue(
            manager.deletedDatabaseNames.isEmpty(),
            "same-process recovery must not physically reclaim source",
        )
        val prefs = armableDs.data.first()
        assertEquals(destinationName, prefs[stringPreferencesKey("addr_db_for:ADDRB")])
        assertTrue(sourceName in prefs[retiredDbNamesKey].orEmpty())
    }

    @Test
    fun markerVerificationFailureDoesNotPublishFallback() = runTest(testDispatcher) {
        val original = manager.currentDb.value
        armableDs.edit {
            it[stringPreferencesKey("pending_source_db_for:ADDRB")] = buildDbName("addrB")
            it[stringPreferencesKey("pending_destination_db_for:ADDRB")] = buildDbName("addrA")
        }
        manager.markerVerification = { _, _ -> throw TestError("marker read failed") }

        var caught: TestError? = null
        try {
            manager.switchActiveDatabase("addrB")
        } catch (error: TestError) {
            caught = error
        }

        assertNotNull(caught)
        assertEquals(original, manager.currentDb.value, "fallback source must never publish without marker proof")
        assertNull(manager.currentAddress.value)
    }

    /**
     * Commit-then-cancel: the routing DataStore edit commits durably, then the wrapper throws CancellationException.
     * Destination must remain active, all routing metadata must agree, and cancellation must propagate.
     */
    @Test
    fun cancellationAfterMergeCommitKeepsDestination() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()
        assertEquals(dbB, manager.currentDb.value, "addrB should be active before association")

        manager.afterMergeCommitted = { armableDs.armFailAfterCommit(CancellationException("post-merge cancel")) }

        var caught: CancellationException? = null
        try {
            manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        } catch (e: CancellationException) {
            caught = e
        }
        assertNotNull(caught, "CancellationException should propagate after merge commit")
        assertEquals(dbA, manager.currentDb.value, "destination must remain active — source is NOT restored")

        // Routing metadata was durably committed before the wrapper threw. All claims must agree.
        val prefs = armableDs.data.first()
        val deviceClaim = prefs[stringPreferencesKey("device_db_for:${deviceKeyHex("deadbeefdeadbeef")}")]
        val nodeClaim = prefs[stringPreferencesKey("node_db_for:123")]
        val addrClaim = prefs[stringPreferencesKey("addr_db_for:ADDRB")]
        assertNotNull(deviceClaim, "device claim committed")
        assertNotNull(nodeClaim, "node claim committed")
        assertNotNull(addrClaim, "address alias committed")
        assertEquals(deviceClaim, nodeClaim, "device and node claims agree")
        assertEquals(deviceClaim, addrClaim, "address alias agrees with claims")
    }

    /**
     * DataStore failure after merge: final routing does not commit. Destination stays active, the pending route remains
     * as crash-recovery intent, and an ordinary in-process retry can also finalize all routing metadata.
     */
    @Test
    fun dataStoreFailureAfterMergeKeepsDestinationAndRepairsOnRetry() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()

        manager.afterMergeCommitted = {
            armableDs.armFailBeforeCommit(RuntimeException("simulated DataStore failure"))
        }

        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        assertEquals(dbA, manager.currentDb.value, "dest must remain active — source is NOT restored")

        // Before retry: routing metadata was NOT persisted (FailBeforeCommit prevented the edit).
        val prefsBefore = armableDs.data.first()
        assertNull(
            prefsBefore[stringPreferencesKey("addr_db_for:ADDRB")],
            "address alias must be absent before repair — routing edit failed",
        )
        assertNotNull(
            prefsBefore[stringPreferencesKey("pending_source_db_for:ADDRB")],
            "pending source must survive a post-commit routing failure",
        )
        assertNotNull(
            prefsBefore[stringPreferencesKey("pending_destination_db_for:ADDRB")],
            "pending destination must survive a post-commit routing failure",
        )

        // Retry repairs routing metadata via the already-unified branch.
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        val prefsAfter = armableDs.data.first()
        val deviceClaim = prefsAfter[stringPreferencesKey("device_db_for:${deviceKeyHex("deadbeefdeadbeef")}")]
        val nodeClaim = prefsAfter[stringPreferencesKey("node_db_for:123")]
        val addrClaim = prefsAfter[stringPreferencesKey("addr_db_for:ADDRB")]
        assertNotNull(deviceClaim, "device claim repaired")
        assertNotNull(nodeClaim, "node claim repaired")
        assertNotNull(addrClaim, "address alias repaired")
        assertEquals(deviceClaim, nodeClaim, "device and node claims agree after repair")
        assertEquals(deviceClaim, addrClaim, "address alias agrees after repair")
        assertNull(prefsAfter[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefsAfter[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    /**
     * After a post-merge routing failure, new writes land in destination (not source), proving source is never
     * reactivated past the merge-commit boundary.
     */
    @Test
    fun postMergeWriteLandsInDestination() = runTest(testDispatcher) {
        val (dbA, dbB) = setupTwoDatabases()

        manager.afterMergeCommitted = {
            armableDs.armFailBeforeCommit(RuntimeException("simulated DataStore failure"))
        }
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")

        val destDb = manager.currentDb.value
        assertEquals(dbA, destDb, "destination is dbA (the canonical DB)")

        manager.withDb {
            it.nodeInfoDao()
                .setMyNodeInfo(
                    MyNodeEntity(
                        myNodeNum = 999,
                        model = null,
                        firmwareVersion = null,
                        couldUpdate = false,
                        shouldUpdate = false,
                        currentPacketId = 0L,
                        messageTimeoutMsec = 0,
                        minAppVersion = 0,
                        maxChannels = 0,
                        hasWifi = false,
                    ),
                )
        }

        // The write exists in destination.
        val destMyNode = destDb.nodeInfoDao().getMyNodeInfo().first()
        assertNotNull(destMyNode, "post-merge write landed in destination")
        assertEquals(999, destMyNode.myNodeNum, "write is durable in destination")

        // The write does NOT exist in source.
        val sourceMyNode = dbB.nodeInfoDao().getMyNodeInfo().first()
        assertNull(sourceMyNode, "source must not have the post-merge write")
    }

    /** A fatal failure before the merge commits still clears intent, releases the gate, and restores source. */
    @Test
    fun errorBeforeMergeCommitReleasesGateAndRestoresSource() = runTest(testDispatcher) {
        val (_, source) = setupTwoDatabases()
        manager.failMergeWith = TestError("fatal pre-commit failure")

        var caught: TestError? = null
        try {
            manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        } catch (error: TestError) {
            caught = error
        }

        assertNotNull(caught, "fatal failure must propagate")
        assertEquals(source, manager.currentDb.value, "source remains canonical before the commit boundary")
        assertFalse(manager.debugWriterGateArmed(), "fatal failure must release writer admission")
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
        val prefs = armableDs.data.first()
        assertNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }

    /** A fatal final-routing failure after commit propagates but keeps destination and its repair intent canonical. */
    @Test
    fun errorAfterMergeCommitReleasesGateAndKeepsDestination() = runTest(testDispatcher) {
        val (destination, _) = setupTwoDatabases()
        manager.afterMergeCommitted = { armableDs.armFailBeforeCommit(TestError("fatal routing failure")) }

        var caught: TestError? = null
        try {
            manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        } catch (error: TestError) {
            caught = error
        }

        assertNotNull(caught, "fatal failure must propagate")
        assertEquals(destination, manager.currentDb.value, "destination remains canonical after merge commit")
        assertFalse(manager.debugWriterGateArmed(), "fatal failure must release writer admission")
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers)
        assertEquals(0, waiters)
        val prefs = armableDs.data.first()
        assertNotNull(prefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
        assertNotNull(prefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])
    }
}
