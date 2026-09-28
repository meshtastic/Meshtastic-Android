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
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.meshtastic.core.database.di.DatabaseDataStore
import org.meshtastic.core.database.di.asDatabaseDataStore
import org.meshtastic.core.database.entity.MyNodeEntity
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Wedge recovery against real database files, where an abandoned `withDb` block and a replacement pool share one SQLite
 * file and therefore one write lock.
 *
 * Runs in real time: a lock held by a native connection is invisible to virtual time, so `runTest` would skip past the
 * `withDb` deadline before the lock is even taken.
 */
class DatabaseManagerAbandonedWriterJvmTest {

    private lateinit var tmpDir: Path
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var manager: FileBackedManager

    @BeforeTest
    fun setUp() {
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "abandonedWriter-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val datastore =
            PreferenceDataStoreFactory.createWithPath(
                scope = dataStoreScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        manager =
            FileBackedManager(
                datastore.asDatabaseDataStore(),
                CoroutineDispatchers(io = Dispatchers.IO, main = Dispatchers.Default, default = Dispatchers.Default),
                tmpDir,
            )
    }

    @AfterTest
    fun tearDown() = runBlocking {
        manager.close()
        dataStoreScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    /**
     * The production shape: a long write (a large MeshLog `DELETE`) outlives the `withDb` deadline and keeps the file's
     * write lock. Room's first DAO Flow on the active database then syncs its invalidation triggers under `BEGIN
     * IMMEDIATE`, and SQLITE_BUSY there is uncaught.
     */
    @Test
    fun abandonedBlockHoldingTheWriteLockLeavesTheActiveDatabaseUsable() = runBlocking {
        manager.switchActiveDatabase("addrA")
        manager.withDb { it.nodeInfoDao().setMyNodeInfo(myNode(firmwareVersion = "seed")) }
        val original = manager.currentDb.value
        val lockHeld = CompletableDeferred<Unit>()
        val releaseLock = CompletableDeferred<Unit>()

        manager.withDbTimeoutMillisForTest = SHORT_WITH_DB_TIMEOUT_MS
        val abandoned = async {
            runCatching {
                manager.withDb { db ->
                    db.useWriterConnection { connection ->
                        connection.immediateTransaction {
                            lockHeld.complete(Unit)
                            releaseLock.await()
                        }
                    }
                }
            }
        }
        lockHeld.await()
        assertIs<DatabaseOperationTimeoutException>(abandoned.await().exceptionOrNull())
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS

        val observed = async { runCatching { manager.currentDb.value.nodeInfoDao().getMyNodeInfo().first() } }
        val write = async {
            runCatching { manager.withDb { it.nodeInfoDao().setMyNodeInfo(myNode(firmwareVersion = "after")) } }
        }
        // Past the busy timeout, so anything that contends for the lock has already failed by the time it is released.
        delay(HOLD_AFTER_ABANDON_MS)
        releaseLock.complete(Unit)

        assertNull(
            observed.await().exceptionOrNull(),
            "a DAO Flow must not fail while the abandoned block holds the lock",
        )
        assertNull(write.await().exceptionOrNull(), "a write must wait out the abandoned block, not fail")
        assertEquals("after", manager.currentDb.value.nodeInfoDao().getMyNodeInfo().first()?.firmwareVersion)
        assertTrue(
            manager.currentDb.value === original,
            "a pool whose write lock is held elsewhere is busy, not wedged, so it stays published",
        )
        awaitWritersDrained()
    }

    /** A block stuck before SQLite holds no lock, so recovery must still publish a replacement that can write. */
    @Test
    fun abandonedBlockHoldingNoLockIsReplacedByAWritablePool() = runBlocking {
        manager.switchActiveDatabase("addrA")
        manager.withDb { it.nodeInfoDao().setMyNodeInfo(myNode(firmwareVersion = "seed")) }
        val original = manager.currentDb.value
        val blockStarted = CompletableDeferred<Unit>()
        val releaseBlock = CompletableDeferred<Unit>()

        manager.withDbTimeoutMillisForTest = SHORT_WITH_DB_TIMEOUT_MS
        val abandoned = async {
            runCatching {
                manager.withDb {
                    blockStarted.complete(Unit)
                    releaseBlock.await()
                }
            }
        }
        blockStarted.await()
        assertIs<DatabaseOperationTimeoutException>(abandoned.await().exceptionOrNull())
        manager.withDbTimeoutMillisForTest = DatabaseManager.WITH_DB_TIMEOUT_MS

        val replacement = manager.currentDb.value
        assertTrue(replacement !== original, "a block that holds no lock must still be replaced")
        manager.withDb { it.nodeInfoDao().setMyNodeInfo(myNode(firmwareVersion = "after")) }
        assertEquals("after", replacement.nodeInfoDao().getMyNodeInfo().first()?.firmwareVersion)

        releaseBlock.complete(Unit)
        awaitWritersDrained()
    }

    private suspend fun awaitWritersDrained() =
        withTimeout(DRAIN_TIMEOUT_MS) { while (manager.debugWriterCounts() != (0 to 0)) delay(POLL_MS) }

    private class FileBackedManager(
        datastore: DatabaseDataStore,
        dispatchers: CoroutineDispatchers,
        private val dir: Path,
    ) : DatabaseManager(datastore, dispatchers) {
        var withDbTimeoutMillisForTest: Long = DatabaseManager.WITH_DB_TIMEOUT_MS

        override val withDbTimeoutMillis: Long
            get() = withDbTimeoutMillisForTest

        override fun buildDatabase(dbName: String): MeshtasticDatabase = Room.databaseBuilder<MeshtasticDatabase>(
            name = (dir / "$dbName.db").toString(),
            factory = { MeshtasticDatabaseConstructor.initialize() },
        )
            .configureCommon()
            .setDriver(BusyTimeoutSQLiteDriver(BundledSQLiteDriver(), ROOM_MIN_BUSY_TIMEOUT_MS))
            .build()

        /** No-op: eviction, legacy cleanup and backfill would read the platform data directory. */
        override fun schedulePostSwitchMaintenance(dbName: String, db: MeshtasticDatabase) = Unit
    }

    private fun myNode(firmwareVersion: String) = MyNodeEntity(
        myNodeNum = 1,
        model = null,
        firmwareVersion = firmwareVersion,
        couldUpdate = false,
        shouldUpdate = false,
        currentPacketId = 0L,
        messageTimeoutMsec = 0,
        minAppVersion = 0,
        maxChannels = 0,
        hasWifi = false,
    )

    private companion object {
        /** Room raises any lower `busy_timeout` to this when it opens a connection. */
        const val ROOM_MIN_BUSY_TIMEOUT_MS = 3_000L

        /** Long enough for the abandoned block to take the lock first, even on a loaded machine. */
        const val SHORT_WITH_DB_TIMEOUT_MS = 3_000L
        const val HOLD_AFTER_ABANDON_MS = ROOM_MIN_BUSY_TIMEOUT_MS + 2_000L
        const val DRAIN_TIMEOUT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
