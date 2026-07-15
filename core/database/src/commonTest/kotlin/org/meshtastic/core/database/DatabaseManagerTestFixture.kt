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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Verifies the association and shutdown state machines in [DatabaseManager]: merge admission and recovery preserve the
 * canonical database, routing metadata commits atomically, and orderly close rejects new work while draining admitted
 * writers and reclaiming every owned Room pool.
 *
 * Uses [UnconfinedTestDispatcher] so background coroutines launched by [DatabaseManager] run eagerly, and virtual-time
 * advancement to drive [DatabaseManager.WRITER_DRAIN_TIMEOUT_MS] deterministically.
 */
abstract class DatabaseManagerTestFixture {

    protected val testDispatcher = UnconfinedTestDispatcher()
    protected lateinit var dataStoreScope: CoroutineScope

    protected lateinit var tmpDir: Path
    protected lateinit var armableDs: ArmableDataStore
    protected lateinit var dispatchers: CoroutineDispatchers
    protected lateinit var manager: TestDatabaseManager
    protected val managers = mutableListOf<TestDatabaseManager>()
    protected val retiredDbNamesKey = stringSetPreferencesKey(DatabaseConstants.RETIRED_DB_NAMES_KEY)

    protected fun setUpFixture() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dbManagerAssocTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStoreScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val realDatastore =
            PreferenceDataStoreFactory.createWithPath(
                scope = dataStoreScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        armableDs = ArmableDataStore(realDatastore)
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        managers.clear()
        manager = createManager()
    }

    protected fun tearDownFixture() = runTest(testDispatcher) {
        managers.asReversed().forEach { it.close() }
        dataStoreScope.cancel()
        runCurrent()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    protected fun createManager(): TestDatabaseManager = TestDatabaseManager(armableDs, dispatchers).also(managers::add)

    /**
     * A callback that started on one pool must never be replayed after an active-database switch. The first invocation
     * can already have performed a side effect before surfacing a closed-pool failure, so a transparent second
     * invocation against the replacement pool would duplicate or split the logical operation.
     */
    protected suspend fun TestScope.assertClosedPoolFailureIsNotReplayedAfterSwitch() {
        manager.switchActiveDatabase("addrA")
        val original = manager.currentDb.value
        val firstBlockStarted = CompletableDeferred<Unit>()
        val releaseFirstBlock = CompletableDeferred<Unit>()
        var invocationCount = 0

        val result = async {
            runCatching {
                manager.withDb { db ->
                    invocationCount += 1
                    assertTrue(db === original)
                    firstBlockStarted.complete(Unit)
                    releaseFirstBlock.await()
                    throw RuntimeException("database connection closed")
                }
            }
        }

        firstBlockStarted.await()
        manager.switchActiveDatabase("addrB")
        val replacement = manager.currentDb.value
        assertTrue(replacement !== original)

        releaseFirstBlock.complete(Unit)
        val failure = result.await().exceptionOrNull()

        assertNotNull(failure)
        assertEquals("database connection closed", failure.message)
        assertEquals(1, invocationCount, "a started callback must never be replayed automatically")
        assertTrue(manager.currentDb.value === replacement)
        assertFalse(manager.debugWriterGateArmed())
        val (writers, waiters) = manager.debugWriterCounts()
        assertEquals(0, writers, "the original writer registration must balance")
        assertEquals(0, waiters, "the failure path must not leak drain waiters")
    }

    /**
     * Overrides [buildDatabase] to create distinct in-memory DBs per [dbName]. Each call to
     * [getInMemoryDatabaseBuilder] returns a new Room instance with its own connection, so
     * [dbCache][DatabaseManager.dbCache] entries for different names are genuinely separate databases.
     */
    protected class TestDatabaseManager(
        datastore: DataStore<Preferences>,
        private val testDispatchers: CoroutineDispatchers,
    ) : DatabaseManager(datastore, testDispatchers) {
        val builtDatabases = mutableListOf<MeshtasticDatabase>()
        val closedDatabases = mutableListOf<MeshtasticDatabase>()
        val closeAttempts = mutableListOf<MeshtasticDatabase>()
        var failCloseFor: MeshtasticDatabase? = null
        var failNextBuildWith: Throwable? = null
        var defaultBuildStarted: CompletableDeferred<Unit>? = null
        var releaseDefaultBuild: CompletableDeferred<Unit>? = null

        override fun buildDatabase(dbName: String): MeshtasticDatabase {
            failNextBuildWith?.let { failure ->
                failNextBuildWith = null
                throw failure
            }
            val database = getInMemoryDatabaseBuilder().build().also(builtDatabases::add)
            if (dbName == DatabaseConstants.DEFAULT_DB_NAME) {
                defaultBuildStarted?.complete(Unit)
                releaseDefaultBuild?.let { release -> runBlocking { release.await() } }
            }
            return database
        }

        override fun closeDatabase(database: MeshtasticDatabase) {
            closeAttempts += database
            if (database === failCloseFor) throw IllegalStateException("forced database close failure")
            closedDatabases += database
            super.closeDatabase(database)
        }

        suspend fun closeCachedForTest(dbName: String) = closeCachedDatabase(dbName)

        override val limitedIo: kotlinx.coroutines.CoroutineDispatcher
            get() = testDispatchers.default

        /**
         * No-op: in-memory test databases have no filesystem directory, so LRU eviction, legacy-DB cleanup, and FTS
         * search-index backfill would crash on Android host tests trying to access platform singletons.
         */
        override fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) = Unit

        /** When non-null, [mergeDatabases] throws this before the merge commits, simulating a pre-commit failure. */
        var failMergeWith: Throwable? = null
        var performRealMerge: Boolean = true
        var beforeMerge: suspend (MeshtasticDatabase, MeshtasticDatabase, String) -> Unit = { _, _, _ -> }
        var afterMergeCommitted: suspend () -> Unit = {}
        var markerVerification: (suspend (MeshtasticDatabase, String) -> Boolean)? = null
        var backfillStarted: CompletableDeferred<MeshtasticDatabase>? = null
        var backfillRelease: CompletableDeferred<Unit>? = null
        val backfillDatabases = mutableListOf<MeshtasticDatabase>()
        val deletedDatabaseNames = mutableListOf<String>()

        override suspend fun mergeDatabases(
            source: MeshtasticDatabase,
            dest: MeshtasticDatabase,
            sourceName: String,
            isAssociationActive: () -> Boolean,
        ) {
            beforeMerge(source, dest, sourceName)
            failMergeWith?.let { throw it }
            if (performRealMerge) super.mergeDatabases(source, dest, sourceName, isAssociationActive)
            afterMergeCommitted()
        }

        override suspend fun verifyMergeMarker(destination: MeshtasticDatabase, sourceName: String): Boolean =
            markerVerification?.invoke(destination, sourceName) ?: super.verifyMergeMarker(destination, sourceName)

        override suspend fun performSearchIndexBackfill(db: MeshtasticDatabase) {
            backfillDatabases += db
            backfillStarted?.complete(db)
            backfillRelease?.await()
        }

        override fun deleteDatabaseFiles(dbName: String) {
            // In-memory test databases have no filesystem files; record the name so retirement assertions can verify
            // which databases the manager logically retired without touching platform storage.
            deletedDatabaseNames += dbName
        }

        fun launchManagerWorkForTest(
            dispatcher: kotlinx.coroutines.CoroutineDispatcher = testDispatchers.default,
            block: suspend () -> Unit,
        ): Job = launchManagerWork(dispatcher) { block() }
    }

    protected class TestError(message: String) : Error(message)

    protected fun roomPoolTimeout() =
        IllegalStateException("Timed out attempting to acquire a reader connection from the database pool")

    /** Builds a minimal [MyNodeEntity] carrying only [num] for association/merge write-leak checks. */
    protected fun myNode(num: Int) = MyNodeEntity(
        myNodeNum = num,
        model = null,
        firmwareVersion = null,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0L,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 0,
        hasWifi = false,
    )

    // Helpers

    /** Hex-encoded [deviceId] as [deviceDbPrefKey] would produce it. */
    protected fun deviceKeyHex(deviceId: String): String = deviceId.encodeUtf8().hex()

    /**
     * Associates the fixture's currently active transport, matching the production address-bound handshake contract.
     */
    protected suspend fun TestDatabaseManager.associateCurrentDevice(
        nodeNum: Int,
        deviceId: String?,
        isSessionActive: () -> Boolean = { true },
    ) {
        associateDevice(
            address = assertNotNull(currentAddress.value),
            nodeNum = nodeNum,
            deviceId = deviceId,
            isSessionActive = isSessionActive,
        )
    }

    /**
     * Sets up two switched databases (addrA claimed as canonical, addrB active) and returns the two DB instances plus
     * the claimed canonical DB name.
     */
    protected suspend fun setupTwoDatabases(): Pair<MeshtasticDatabase, MeshtasticDatabase> {
        manager.switchActiveDatabase("addrA")
        manager.associateCurrentDevice(nodeNum = 123, deviceId = "deadbeefdeadbeef")
        val dbA = manager.currentDb.value

        manager.switchActiveDatabase("addrB")
        val dbB = manager.currentDb.value

        return dbA to dbB
    }

    // DataStore fault injection

    /**
     * Armable DataStore wrapper that can inject faults deterministically.
     *
     * Modes:
     * - [Mode.Normal]: delegates normally.
     * - [Mode.FailBeforeCommit]: throws [exception] before delegating, then resets to Normal.
     * - [Mode.FailAfterCommit]: delegates first (durably commits), then throws [exception], then resets to Normal.
     * - [Mode.CancelAfterCommit]: delegates first, then cancels the supplied caller job and resets to Normal.
     *
     * Each fault fires once; subsequent edits succeed normally. This avoids fragile edit-count assumptions.
     */
    protected class ArmableDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data
            get() = delegate.data

        sealed interface Mode {
            data object Normal : Mode

            data class FailBeforeCommit(val exception: Throwable) : Mode

            data class FailAfterCommit(val exception: Throwable) : Mode

            data class CancelAfterCommit(val job: Job) : Mode
        }

        var mode: Mode = Mode.Normal
        private var faultFired = false

        @Suppress("TooGenericExceptionThrown")
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            when (val m = mode) {
                is Mode.Normal -> delegate.updateData(transform)

                is Mode.FailBeforeCommit -> {
                    if (!faultFired) {
                        faultFired = true
                        mode = Mode.Normal
                        throw m.exception
                    }
                    delegate.updateData(transform)
                }

                is Mode.FailAfterCommit -> {
                    val result = delegate.updateData(transform)
                    if (!faultFired) {
                        faultFired = true
                        mode = Mode.Normal
                        throw m.exception
                    }
                    result
                }

                is Mode.CancelAfterCommit -> {
                    val result = delegate.updateData(transform)
                    if (!faultFired) {
                        faultFired = true
                        mode = Mode.Normal
                        m.job.cancel(CancellationException("cancelled after durable DataStore commit"))
                    }
                    result
                }
            }

        fun reset() {
            mode = Mode.Normal
            faultFired = false
        }

        fun armFailBeforeCommit(exception: Throwable) {
            mode = Mode.FailBeforeCommit(exception)
            faultFired = false
        }

        fun armFailAfterCommit(exception: Throwable) {
            mode = Mode.FailAfterCommit(exception)
            faultFired = false
        }

        fun armCancelAfterCommit(job: Job) {
            mode = Mode.CancelAfterCommit(job)
            faultFired = false
        }
    }
}
