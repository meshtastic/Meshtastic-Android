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

import androidx.room3.Room
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.meshtastic.core.database.entity.MyNodeEntity
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseBackupJvmTest {
    private val fileSystem = FileSystem.SYSTEM
    private val dir: Path = Files.createTempDirectory("meshtastic-backup").toOkioPath()

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(dir)
    }

    private fun openRealDatabase(file: Path): MeshtasticDatabase {
        val db =
            Room.databaseBuilder<MeshtasticDatabase>(
                name = file.toString(),
                factory = { MeshtasticDatabaseConstructor.initialize() },
            )
                .configureCommon()
                .setDriver(BundledSQLiteDriver())
                .build()
        runBlocking { db.nodeInfoDao().getMyNodeEntity() }
        return db
    }

    private fun createRealDatabase(file: Path) {
        openRealDatabase(file).close()
    }

    private fun insertMetadataRows(db: MeshtasticDatabase, count: Int) = runBlocking {
        db.useWriterConnection { connection ->
            repeat(count) { num ->
                connection.usePrepared("INSERT INTO metadata (num, proto, timestamp) VALUES (?, x'00', 1)") {
                    it.bindLong(1, num.toLong())
                    it.step()
                }
            }
        }
    }

    private fun countRows(file: Path, table: String): Long =
        BundledSQLiteDriver().open(file.toString()).use { connection ->
            connection.prepare("SELECT count(*) FROM $table").use {
                it.step()
                it.getLong(0)
            }
        }

    private fun headerOf(file: Path): DatabaseBackup.Validation =
        DatabaseBackup.validateHeader(fileSystem.source(file).buffer().use { DatabaseBackup.readHeader(it) })

    /** A file system that fails the rename that would install [failingSource]. */
    private class FailingMoveFileSystem(private val failingSource: Path) : ForwardingFileSystem(FileSystem.SYSTEM) {
        override fun atomicMove(source: Path, target: Path) {
            if (source == failingSource) throw IOException("simulated move failure")
            super.atomicMove(source, target)
        }
    }

    @Test
    fun `the bundled SQLite supports VACUUM INTO`() {
        val version =
            BundledSQLiteDriver().open(":memory:").use { connection ->
                connection.prepare("SELECT sqlite_version()").use {
                    it.step()
                    it.getText(0)
                }
            }
        val (major, minor) = version.split('.').map { it.toInt() }
        assertTrue(major > 3 || (major == 3 && minor >= 27), "SQLite $version predates VACUUM INTO")
    }

    @Test
    fun `a database Room just created validates against the declared schema version`() {
        val file = dir / "real.db"
        createRealDatabase(file)
        assertEquals(DatabaseBackup.Validation.Valid, headerOf(file))
        assertEquals(DatabaseBackup.Validation.Valid, validateBackupFile(file, fileSystem))
    }

    @Test
    fun `export of an open database includes rows still in the wal and passes integrity check`() {
        val live = dir / "live.db"
        val snapshot = dir / "snapshot.db"
        val db = openRealDatabase(live)
        try {
            insertMetadataRows(db, count = 5)
            val walSize = fileSystem.metadata(dir / "live.db-wal").size ?: 0L
            assertTrue(walSize > 0, "rows should still sit in the wal")

            vacuumInto(live, snapshot)

            assertEquals(DatabaseBackup.Validation.Valid, validateBackupFile(snapshot, fileSystem))
            assertEquals(5, countRows(snapshot, "metadata"))
            assertFalse(fileSystem.exists(dir / "snapshot.db-wal"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `a truncated backup is rejected even though its header is intact`() {
        val full = dir / "full.db"
        val truncated = dir / "truncated.db"
        createRealDatabase(full)
        val bytes = fileSystem.read(full) { readByteArray() }
        fileSystem.write(truncated) { write(bytes, 0, bytes.size / 2) }

        assertEquals(DatabaseBackup.Validation.Valid, headerOf(truncated))
        assertEquals(DatabaseBackup.Validation.Corrupt, validateBackupFile(truncated, fileSystem))
    }

    @Test
    fun `a backup with a damaged page is rejected`() {
        val file = dir / "damaged.db"
        val db = openRealDatabase(file)
        insertMetadataRows(db, count = 200)
        db.close()
        val pageSize =
            BundledSQLiteDriver().open(file.toString()).use { connection ->
                connection.prepare("PRAGMA page_size").use {
                    it.step()
                    it.getLong(0)
                }
            }
        val lastPage = (fileSystem.metadata(file).size ?: 0L) / pageSize - 1
        RandomAccessFile(file.toFile(), "rw").use { raf ->
            raf.seek(lastPage * pageSize)
            raf.write(ByteArray(pageSize.toInt() / 2) { 0x5A })
        }

        assertEquals(DatabaseBackup.Validation.Valid, headerOf(file))
        assertEquals(DatabaseBackup.Validation.Corrupt, validateBackupFile(file, fileSystem))
    }

    @Test
    fun `a backup from another schema version is rejected`() {
        val file = dir / "older.db"
        createRealDatabase(file)
        BundledSQLiteDriver().open(file.toString()).use { it.execSQL("PRAGMA user_version = 63") }

        assertEquals(
            DatabaseBackup.Validation.SchemaMismatch(found = 63, expected = DatabaseBackup.SCHEMA_VERSION),
            validateBackupFile(file, fileSystem),
        )
    }

    @Test
    fun `staged restore replaces the database and clears the wal and shm files`() {
        val live = dir / "live.db"
        val backup = dir / "backup.db"
        createRealDatabase(backup)
        val expected = fileSystem.read(backup) { readByteString() }
        fileSystem.write(live) { writeUtf8("old contents") }
        fileSystem.write(dir / "live.db-wal") { writeUtf8("wal") }
        fileSystem.write(dir / "live.db-shm") { writeUtf8("shm") }
        fileSystem.copy(backup, DatabaseBackup.stagedPath(live))

        applyStagedRestore("live", live, fileSystem)

        assertEquals(expected, fileSystem.read(live) { readByteString() })
        assertFalse(fileSystem.exists(DatabaseBackup.stagedPath(live)))
        assertFalse(fileSystem.exists(dir / "live.db-wal"))
        assertFalse(fileSystem.exists(dir / "live.db-shm"))
        assertTrue(fileSystem.list(dir).none { it.name.endsWith(DatabaseBackup.BACKUP_SUFFIX) })
    }

    @Test
    fun `a failed move of the staged file leaves the original database in place`() {
        val live = dir / "live.db"
        val staged = DatabaseBackup.stagedPath(live)
        createRealDatabase(dir / "backup.db")
        fileSystem.copy(dir / "backup.db", staged)
        fileSystem.write(live) { writeUtf8("keep me") }
        fileSystem.write(dir / "live.db-wal") { writeUtf8("keep wal") }

        applyStagedRestore("live", live, FailingMoveFileSystem(failingSource = staged))

        assertEquals("keep me", fileSystem.read(live) { readUtf8() })
        assertEquals("keep wal", fileSystem.read(dir / "live.db-wal") { readUtf8() })
        assertFalse(fileSystem.exists(DatabaseBackup.backupPath(live)))
        assertFalse(fileSystem.exists(DatabaseBackup.backupPath(dir / "live.db-wal")))
    }

    @Test
    fun `a swap interrupted after the original moved aside is put back on the next open`() {
        val live = dir / "live.db"
        fileSystem.write(DatabaseBackup.backupPath(live)) { writeUtf8("keep me") }

        applyStagedRestore("live", live, fileSystem)

        assertEquals("keep me", fileSystem.read(live) { readUtf8() })
        assertFalse(fileSystem.exists(DatabaseBackup.backupPath(live)))
    }

    @Test
    fun `a leftover bak beside a database Room just created empty is kept`() {
        val live = dir / "live.db"
        createRealDatabase(live)
        fileSystem.write(DatabaseBackup.backupPath(live)) { writeUtf8("original") }

        applyStagedRestore("live", live, fileSystem)

        assertEquals("original", fileSystem.read(DatabaseBackup.backupPath(live)) { readUtf8() })
    }

    @Test
    fun `a leftover bak beside a restored database with a local node is deleted`() {
        val live = dir / "live.db"
        val db = openRealDatabase(live)
        runBlocking { db.nodeInfoDao().setMyNodeInfo(myNode(num = 1)) }
        db.close()
        fileSystem.write(DatabaseBackup.backupPath(live)) { writeUtf8("stale") }

        applyStagedRestore("live", live, fileSystem)

        assertFalse(fileSystem.exists(DatabaseBackup.backupPath(live)))
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

    @Test
    fun `invalid staged restore is discarded and the live database is untouched`() {
        val live = dir / "live.db"
        fileSystem.write(live) { writeUtf8("keep me") }
        fileSystem.write(DatabaseBackup.stagedPath(live)) { writeUtf8("garbage that is not a database") }

        applyStagedRestore("live", live, fileSystem)

        assertEquals("keep me", fileSystem.read(live) { readUtf8() })
        assertFalse(fileSystem.exists(DatabaseBackup.stagedPath(live)))
    }

    @Test
    fun `no staged restore leaves the live database alone`() {
        val live = dir / "live.db"
        fileSystem.write(live) { writeUtf8("keep me") }

        applyStagedRestore("live", live, fileSystem)

        assertTrue(fileSystem.exists(live))
        assertEquals("keep me", fileSystem.read(live) { readUtf8() })
    }
}
