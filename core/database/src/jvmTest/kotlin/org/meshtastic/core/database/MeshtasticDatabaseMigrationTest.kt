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
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    /**
     * 50→51 makes the three `rssi` columns nullable, which Room implements by recreating `packet`, `reactions` and
     * `discovered_node` (DROP + RENAME). [migrateAll] only proves the resulting schema validates from an empty
     * database; this proves existing rows survive the rebuild with their values — including a legacy `rssi = 0`, which
     * stays 0 rather than becoming NULL.
     */
    @Test
    fun rssiColumnsGoNullableWithoutLosingRows() = runTest {
        helper.createDatabase(RSSI_NULLABLE_FROM_VERSION).use { connection ->
            connection.execSQL(
                "INSERT INTO packet (uuid, myNodeNum, port_num, contact_key, received_time, read, data, snr, rssi) " +
                    "VALUES (1, 42, 1, '0^all', 1000, 1, '{}', 5.0, 0)",
            )
            connection.execSQL(
                "INSERT INTO reactions (myNodeNum, reply_id, user_id, emoji, timestamp, snr, rssi) " +
                    "VALUES (42, 7, '!abc', 'X', 2000, 5.0, -70)",
            )
            connection.execSQL(
                "INSERT INTO discovery_session (id, timestamp, presets_scanned, home_preset) " +
                    "VALUES (1, 3000, 1, 'LONG_FAST')",
            )
            connection.execSQL(
                "INSERT INTO discovery_preset_result (id, session_id, preset_name) VALUES (1, 1, 'LONG_FAST')",
            )
            connection.execSQL(
                "INSERT INTO discovered_node (id, preset_result_id, node_num, snr, rssi) VALUES (1, 1, 99, 5.0, 0)",
            )
        }

        helper.runMigrationsAndValidate(RSSI_NULLABLE_TO_VERSION, emptyList()).use { connection ->
            assertEquals(listOf("0"), queryColumn(connection, "SELECT rssi FROM packet"))
            assertEquals(listOf("-70"), queryColumn(connection, "SELECT rssi FROM reactions"))
            assertEquals(listOf("0"), queryColumn(connection, "SELECT rssi FROM discovered_node"))
            // The recreate must not have orphaned the cascading FK target.
            assertEquals(listOf("1"), queryColumn(connection, "SELECT preset_result_id FROM discovered_node"))
            // A NULL is now storable where the column was previously NOT NULL DEFAULT 0.
            connection.execSQL("UPDATE packet SET rssi = NULL WHERE uuid = 1")
            assertEquals(listOf(null), queryColumn(connection, "SELECT rssi FROM packet"))
        }
    }

    @Test
    fun snrColumnsGoNullableWithoutLosingRows() = runTest {
        helper.createDatabase(SNR_NULLABLE_FROM_VERSION).use { connection ->
            connection.execSQL(
                "INSERT INTO packet (uuid, myNodeNum, port_num, contact_key, received_time, read, data, snr, rssi) " +
                    "VALUES (1, 42, 1, '0^all', 1000, 1, '{}', 0.0, -70)",
            )
            connection.execSQL(
                "INSERT INTO reactions (myNodeNum, reply_id, user_id, emoji, timestamp, snr, rssi) " +
                    "VALUES (42, 7, '!abc', 'X', 2000, -12.5, -70)",
            )
        }

        helper.runMigrationsAndValidate(SNR_NULLABLE_TO_VERSION, emptyList()).use { connection ->
            // A stored 0 dB must survive the recreate as 0, not become NULL: it is a real reading.
            assertEquals(listOf("0.0"), queryColumn(connection, "SELECT snr FROM packet"))
            assertEquals(listOf("-12.5"), queryColumn(connection, "SELECT snr FROM reactions"))
            // A NULL is now storable where the column was previously NOT NULL DEFAULT 0.
            connection.execSQL("UPDATE packet SET snr = NULL WHERE uuid = 1")
            assertEquals(listOf(null), queryColumn(connection, "SELECT snr FROM packet"))
        }
    }

    /** Reads one column of every row as a string, with SQL NULL surfaced as Kotlin null. */
    private fun queryColumn(connection: SQLiteConnection, sql: String): List<String?> =
        connection.prepare(sql).use { statement ->
            buildList {
                while (statement.step()) {
                    add(if (statement.isNull(0)) null else statement.getText(0))
                }
            }
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
        const val RSSI_NULLABLE_FROM_VERSION = 50
        const val RSSI_NULLABLE_TO_VERSION = 51
        const val SNR_NULLABLE_FROM_VERSION = 51
        const val SNR_NULLABLE_TO_VERSION = 52
    }
}
