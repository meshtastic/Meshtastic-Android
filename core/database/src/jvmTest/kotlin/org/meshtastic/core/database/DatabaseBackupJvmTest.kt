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
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
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

    private fun createRealDatabase(file: Path) {
        val db =
            Room.databaseBuilder<MeshtasticDatabase>(
                name = file.toString(),
                factory = { MeshtasticDatabaseConstructor.initialize() },
            )
                .configureCommon()
                .setDriver(BundledSQLiteDriver())
                .build()
        runBlocking { db.nodeInfoDao().getMyNodeEntity() }
        db.close()
    }

    private fun headerOf(file: Path): DatabaseBackup.Validation =
        DatabaseBackup.validateHeader(fileSystem.source(file).buffer().use { DatabaseBackup.readHeader(it) })

    @Test
    fun `a database Room just created validates against the declared schema version`() {
        val file = dir / "real.db"
        createRealDatabase(file)
        assertEquals(DatabaseBackup.Validation.Valid, headerOf(file))
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
    }

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
