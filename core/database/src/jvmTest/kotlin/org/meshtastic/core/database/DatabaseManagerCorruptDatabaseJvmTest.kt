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

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.meshtastic.core.database.di.DatabaseDataStore
import org.meshtastic.core.database.di.asDatabaseDataStore
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** A device database too damaged to open is moved aside on switch, and the device gets a fresh database. */
class DatabaseManagerCorruptDatabaseJvmTest {

    private lateinit var tmpDir: Path
    private lateinit var persistentDir: Path
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var datastore: DatabaseDataStore
    private lateinit var dispatchers: CoroutineDispatchers
    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "corruptDatabase-${Uuid.random()}"
        persistentDir = tmpDir / "persistent"
        FileSystem.SYSTEM.createDirectories(persistentDir)
        testDispatcher = UnconfinedTestDispatcher()
        dataStoreScope = CoroutineScope(SupervisorJob() + testDispatcher)
        datastore =
            PreferenceDataStoreFactory.createWithPath(
                scope = dataStoreScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
                .asDatabaseDataStore()
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
    }

    @AfterTest
    fun tearDown() = runTest(testDispatcher) {
        dataStoreScope.coroutineContext[Job]?.cancelAndJoin()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun aFileThatIsNotADatabaseIsMovedAsideAndReplaced() = runTest(testDispatcher) {
        val dbFile = createDeviceDatabase()
        FileSystem.SYSTEM.write(dbFile) { writeUtf8("not a database ".repeat(PAGE_BYTES)) }

        assertRecoversOnSwitch(dbFile)
    }

    @Test
    fun aDatabaseWithADamagedSchemaPageIsMovedAsideAndReplaced() = runTest(testDispatcher) {
        val dbFile = createDeviceDatabase()
        val original = FileSystem.SYSTEM.read(dbFile) { readByteArray() }
        // Keep the 100-byte header so SQLite accepts the file, then wreck the rest of page 1 (sqlite_schema).
        for (i in SQLITE_HEADER_BYTES until PAGE_BYTES) original[i] = 0x5A
        FileSystem.SYSTEM.write(dbFile) { write(original) }

        assertRecoversOnSwitch(dbFile)
    }

    @Test
    fun aCorruptDatabaseWhosePoolFailsToCloseStaysInPlace() = runTest(testDispatcher) {
        val dbFile = createDeviceDatabase()
        FileSystem.SYSTEM.write(dbFile) { writeUtf8("not a database ".repeat(PAGE_BYTES)) }
        val damaged = FileSystem.SYSTEM.read(dbFile) { readByteArray() }
        val manager = FilePersistentManager(datastore, dispatchers, persistentDir)
        manager.failDeviceClose = true
        try {
            assertTrue(runCatching { manager.switchActiveDatabase(ADDRESS) }.isFailure)

            assertTrue(FileSystem.SYSTEM.list(persistentDir).none { ".corrupt-" in it.name })
            assertTrue(FileSystem.SYSTEM.read(dbFile) { readByteArray() }.contentEquals(damaged))
        } finally {
            manager.failDeviceClose = false
            manager.close()
            runCurrent()
        }
    }

    @Test
    fun aSwitchCancelledDuringTheProbeLeavesNoUnprobedPoolCached() = runTest(testDispatcher) {
        val manager = FilePersistentManager(datastore, dispatchers, persistentDir)
        try {
            val gate = CompletableDeferred<Unit>()
            manager.probeGate = gate
            val switch = launch { manager.switchActiveDatabase(ADDRESS) }
            runCurrent()
            assertEquals(1, manager.probeCount)
            switch.cancelAndJoin()

            manager.probeGate = null
            manager.switchActiveDatabase(ADDRESS)

            assertEquals(
                2,
                manager.probeCount,
                "the second switch must probe a fresh pool, not reuse the cached one",
            )
        } finally {
            manager.close()
            runCurrent()
        }
    }

    private suspend fun TestScope.createDeviceDatabase(): Path {
        val manager = FilePersistentManager(datastore, dispatchers, persistentDir)
        manager.switchActiveDatabase(ADDRESS)
        manager.currentDb.value.useReaderConnection { connection ->
            connection.usePrepared("SELECT count(*) FROM sqlite_master") { it.step() }
        }
        val dbName = manager.builtNames.last { it != DatabaseConstants.DEFAULT_DB_NAME }
        manager.close()
        runCurrent()
        for (suffix in listOf("-wal", "-shm")) {
            FileSystem.SYSTEM.delete(persistentDir / "$dbName.db$suffix", mustExist = false)
        }
        return persistentDir / "$dbName.db"
    }

    private suspend fun TestScope.assertRecoversOnSwitch(dbFile: Path) {
        val damaged = FileSystem.SYSTEM.read(dbFile) { readByteArray() }
        val manager = FilePersistentManager(datastore, dispatchers, persistentDir)
        try {
            manager.switchActiveDatabase(ADDRESS)

            val tables =
                manager.currentDb.value.useReaderConnection { connection ->
                    connection.usePrepared("SELECT count(*) FROM sqlite_master WHERE name = 'nodes'") {
                        it.step()
                        it.getLong(0)
                    }
                }
            assertEquals(1L, tables)
            val aside = FileSystem.SYSTEM.list(persistentDir).filter { ".db.corrupt-" in it.name }
            assertEquals(1, aside.size, "expected one moved-aside file, found ${aside.map { it.name }}")
            assertTrue(FileSystem.SYSTEM.read(aside.single()) { readByteArray() }.contentEquals(damaged))
            assertFalse(FileSystem.SYSTEM.read(dbFile) { readByteArray() }.contentEquals(damaged))
        } finally {
            manager.close()
            runCurrent()
        }
    }

    private class FilePersistentManager(
        datastore: DatabaseDataStore,
        private val testDispatchers: CoroutineDispatchers,
        private val persistentDir: Path,
    ) : DatabaseManager(datastore, testDispatchers) {
        val builtNames = mutableListOf<String>()
        var probeGate: CompletableDeferred<Unit>? = null
        var probeCount = 0

        override suspend fun probeDatabaseOpen(database: MeshtasticDatabase) {
            probeCount++
            probeGate?.await()
            super.probeDatabaseOpen(database)
        }

        override fun buildDatabase(dbName: String): MeshtasticDatabase {
            builtNames += dbName
            return Room.databaseBuilder<MeshtasticDatabase>(
                name = (persistentDir / "$dbName.db").toString(),
                factory = { MeshtasticDatabaseConstructor.initialize() },
            )
                .configureCommon()
                .setDriver(BundledSQLiteDriver())
                .build()
        }

        override fun databaseFileFor(dbName: String): Path = persistentDir / "$dbName.db"

        var failDeviceClose = false

        override fun closeDatabase(database: MeshtasticDatabase) {
            if (failDeviceClose) throw IllegalStateException("forced close failure")
            super.closeDatabase(database)
        }

        override fun createPoolLane(): kotlinx.coroutines.CoroutineDispatcher = testDispatchers.io

        override fun deleteDatabaseFiles(dbName: String) {
            listOf("$dbName.db", "$dbName.db-wal", "$dbName.db-shm", "$dbName.db-journal").forEach { fileName ->
                FileSystem.SYSTEM.delete(persistentDir / fileName, mustExist = false)
            }
        }

        override fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) = Unit
    }

    private companion object {
        const val ADDRESS = "x0123456789ab"
        const val SQLITE_HEADER_BYTES = 100
        const val PAGE_BYTES = 4096
    }
}
