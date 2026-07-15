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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * JVM-only durability test for the database-association restart recovery.
 *
 * This exercises the real filesystem-backed restart path (close the manager, reopen the same persistent SQLite files
 * with a fresh manager) which the common suite intentionally avoids — running real file create/close/reopen/delete
 * twice (JVM + Android host) doubled the native SQLite worker churn that destabilized the `allTests` aggregate. Here it
 * runs exactly once, on the JVM target.
 */
class DatabaseManagerPendingRouteRecoveryJvmTest {

    private lateinit var tmpDir: Path
    private lateinit var persistentDir: Path
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var armableDs: ArmableDataStore
    private lateinit var dispatchers: CoroutineDispatchers
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "pendingRouteRecovery-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        persistentDir = tmpDir / "persistent"
        FileSystem.SYSTEM.createDirectories(persistentDir)
        testScheduler = TestCoroutineScheduler()
        testDispatcher = UnconfinedTestDispatcher(testScheduler)
        dataStoreScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val realDatastore =
            PreferenceDataStoreFactory.createWithPath(
                scope = dataStoreScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        armableDs = ArmableDataStore(realDatastore)
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
    }

    @AfterTest
    fun tearDown() = runTest(testDispatcher) {
        dataStoreScope.coroutineContext[Job]?.cancelAndJoin()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun newManagerRepairsCommittedPendingRouteBeforePublishing() = runTest(testDispatcher) {
        val firstManager = JvmPersistentManager(armableDs, dispatchers, persistentDir)
        var firstClosed = false
        try {
            // First process: open persistent databases, seed durable data, and commit the merge whose final routing
            // write fails. This simulates a crash AFTER the merge committed but BEFORE routing finalized, leaving a
            // durable pending route for restart recovery.
            firstManager.switchActiveDatabase("addrA")
            firstManager.associateDevice(
                address = "addrA",
                nodeNum = 123,
                deviceId = "deadbeefdeadbeef",
                isSessionActive = { true },
            )
            val dbA = firstManager.currentDb.value
            firstManager.switchActiveDatabase("addrB")

            dbA.nodeInfoDao().setMyNodeInfo(myNode(777))
            firstManager.afterMergeCommitted = {
                armableDs.armFailBeforeCommit(RuntimeException("final routing failed"))
            }
            firstManager.associateDevice(
                address = "addrB",
                nodeNum = 123,
                deviceId = "deadbeefdeadbeef",
                isSessionActive = { true },
            )

            val pendingPrefs = armableDs.data.first()
            assertNull(pendingPrefs[stringPreferencesKey("addr_db_for:ADDRB")])
            assertNotNull(pendingPrefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
            assertNotNull(pendingPrefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])

            // Real restart: fully close the first manager and its Room pools before constructing the replacement.
            // The seeded destination data and the durable pending route live on disk; nothing else may hold the
            // persistent SQLite files when the recreated manager reopens them.
            firstManager.close()
            runCurrent()
            firstClosed = true

            val recreated = JvmPersistentManager(armableDs, dispatchers, persistentDir)
            try {
                val beforeSwitch = recreated.currentDb.value
                val verificationStarted = CompletableDeferred<Unit>()
                val allowVerification = CompletableDeferred<Unit>()
                var verifiedSource: String? = null
                recreated.markerVerification = { destinationDb, sourceName ->
                    verifiedSource = sourceName
                    verificationStarted.complete(Unit)
                    allowVerification.await()
                    destinationDb.mergeMarkerDao().isMerged(sourceName)
                }

                val switchJob = launch { recreated.switchActiveDatabase("addrB") }
                verificationStarted.await()
                assertEquals(
                    beforeSwitch,
                    recreated.currentDb.value,
                    "source must not publish while marker is verified",
                )
                assertNull(recreated.currentAddress.value)
                allowVerification.complete(Unit)
                switchJob.join()

                assertEquals(buildDbName("addrB"), verifiedSource)
                assertEquals(777, recreated.currentDb.value.nodeInfoDao().getMyNodeInfo().first()?.myNodeNum)
                val repairedPrefs = armableDs.data.first()
                assertEquals(buildDbName("addrA"), repairedPrefs[stringPreferencesKey("addr_db_for:ADDRB")])
                assertNull(repairedPrefs[stringPreferencesKey("pending_source_db_for:ADDRB")])
                assertNull(repairedPrefs[stringPreferencesKey("pending_destination_db_for:ADDRB")])

                recreated.markerVerification = null
                recreated.switchActiveDatabase(null)
                recreated.switchActiveDatabase("addrB")
                assertEquals(777, recreated.currentDb.value.nodeInfoDao().getMyNodeInfo().first()?.myNodeNum)
            } finally {
                recreated.close()
            }
        } finally {
            if (!firstClosed) runCatching { firstManager.close() }
        }
    }

    /** Minimal JVM [DatabaseManager] whose databases are always real persistent files under [persistentDir]. */
    private class JvmPersistentManager(
        datastore: DataStore<Preferences>,
        private val testDispatchers: CoroutineDispatchers,
        private val persistentDir: Path,
    ) : DatabaseManager(datastore, testDispatchers) {
        override fun buildDatabase(dbName: String): MeshtasticDatabase = Room.databaseBuilder<MeshtasticDatabase>(
            name = (persistentDir / "$dbName.db").toString(),
            factory = { MeshtasticDatabaseConstructor.initialize() },
        )
            .configureCommon()
            .setDriver(BundledSQLiteDriver())
            .build()

        override val limitedIo: kotlinx.coroutines.CoroutineDispatcher
            get() = testDispatchers.default

        override fun deleteDatabaseFiles(dbName: String) {
            listOf("$dbName.db", "$dbName.db-wal", "$dbName.db-shm", "$dbName.db-journal").forEach { fileName ->
                FileSystem.SYSTEM.delete(persistentDir / fileName, mustExist = false)
            }
        }

        /** No-op: startup/retirement behavior is driven explicitly by this fixture. */
        override fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) = Unit

        var afterMergeCommitted: suspend () -> Unit = {}

        var markerVerification: (suspend (MeshtasticDatabase, String) -> Boolean)? = null

        override suspend fun mergeDatabases(
            source: MeshtasticDatabase,
            dest: MeshtasticDatabase,
            sourceName: String,
            isAssociationActive: () -> Boolean,
        ) {
            super.mergeDatabases(source, dest, sourceName, isAssociationActive)
            afterMergeCommitted()
        }

        override suspend fun verifyMergeMarker(destination: MeshtasticDatabase, sourceName: String): Boolean =
            markerVerification?.invoke(destination, sourceName) ?: super.verifyMergeMarker(destination, sourceName)
    }

    private fun myNode(num: Int) = MyNodeEntity(
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

    /**
     * Armable DataStore wrapper that can inject faults deterministically.
     *
     * Modes:
     * - [Mode.Normal]: delegates normally.
     * - [Mode.FailBeforeCommit]: throws [exception] before delegating, then resets to Normal.
     *
     * Each fault fires once; subsequent edits succeed normally. This avoids fragile edit-count assumptions.
     */
    private class ArmableDataStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        override val data
            get() = delegate.data

        sealed interface Mode {
            data object Normal : Mode

            data class FailBeforeCommit(val exception: Throwable) : Mode
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
            }

        fun armFailBeforeCommit(exception: Throwable) {
            mode = Mode.FailBeforeCommit(exception)
            faultFired = false
        }
    }
}
