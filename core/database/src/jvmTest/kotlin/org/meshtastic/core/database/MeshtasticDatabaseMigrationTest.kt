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

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Creates the earliest exported schema (v3) and walks every auto-migration up to the current version, validating the
 * resulting schema against the exported JSON at each step. This is the guard that a broken migration would trip
 * *before* users hit [MeshtasticDatabase]'s `fallbackToDestructiveMigration(dropAllTables = false)`, which would
 * otherwise silently wipe their data.
 *
 * Runs on the JVM target — so it is part of `:core:database:allTests` and executes in PR CI. The JVM
 * [MigrationTestHelper] reads the exported schemas straight from the module's `schemas/` directory, so no APK/asset
 * packaging is needed (unlike the instrumented `androidDeviceTest` variant, which does not run in the PR pipeline).
 */
class MeshtasticDatabaseMigrationTest {

    // The JVM MigrationTestHelper resolves schemas relative to the Gradle test working directory (the module dir).
    private val schemaDir = Path("schemas")

    private val dbFile = File.createTempFile("meshtastic-migration", ".db").apply { delete() }

    // Not wired as a @Rule: jvmTest runs on the JUnit Platform, where JUnit4 @Rule does not apply. Connections are
    // closed inline instead.
    private val helper =
        MigrationTestHelper(
            schemaDirectoryPath = schemaDir,
            databasePath = dbFile.toPath(),
            driver = BundledSQLiteDriver(),
            databaseClass = MeshtasticDatabase::class,
            databaseFactory = { MeshtasticDatabaseConstructor.initialize() },
        )

    @AfterTest
    fun cleanUp() {
        dbFile.delete()
    }

    @Test
    fun migrateAll() = runTest {
        helper.createDatabase(EARLIEST_SCHEMA_VERSION).close()
        // No manual migrations: every version bump is an @AutoMigration, so Room derives the full path itself.
        helper.runMigrationsAndValidate(latestSchemaVersion(), emptyList()).close()
    }

    private fun latestSchemaVersion(): Int {
        val dbSchemas = schemaDir.resolve(checkNotNull(MeshtasticDatabase::class.qualifiedName)).toFile()
        return dbSchemas
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.maxOrNull() ?: error("No exported Room schemas found in $dbSchemas")
    }

    private companion object {
        const val EARLIEST_SCHEMA_VERSION = 3
    }
}
