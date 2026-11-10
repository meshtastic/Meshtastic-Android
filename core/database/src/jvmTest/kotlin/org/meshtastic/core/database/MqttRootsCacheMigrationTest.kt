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
import kotlin.test.assertTrue

/**
 * 64→65 adds the `mqtt_roots_cache` table. Rows already in the sibling API caches and in a user table survive the
 * addition, and the new table starts empty so the repository seeds it from the bundled snapshot.
 */
class MqttRootsCacheMigrationTest {

    // The JVM MigrationTestHelper resolves schemas relative to the Gradle test working directory (the module dir).
    private val dbFile = File.createTempFile("meshtastic-mqtt-roots-migration", ".db").apply { delete() }

    // Not wired as a @Rule: jvmTest runs on the JUnit Platform, where JUnit4 @Rule does not apply.
    private val helper =
        MigrationTestHelper(
            schemaDirectoryPath = Path("schemas"),
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
    fun mqttRootsTableAddedEmptyWithoutDisturbingExistingRows() = runTest {
        helper.createDatabase(FROM_VERSION).use { connection ->
            connection.execSQL(
                "INSERT INTO bootloader_ota_quirks_cache (id, devices_json, soft_device_variants_json) " +
                    "VALUES (0, '[{\"hwModel\":\"HELTEC_V3\"}]', '[{\"target\":\"rak4631\",\"variant\":\"7.3.0\"}]')",
            )
            connection.execSQL("INSERT INTO maintenance_uf2_cache (id, manifest_json) VALUES (0, '{\"erase\":{}}')")
            connection.execSQL(
                "INSERT INTO quick_chat (uuid, name, message, mode, position) " +
                    "VALUES (1, 'Hi', 'hello', 'Append', 0), (2, 'Ok', 'on my way', 'Instant', 1)",
            )
        }

        helper.runMigrationsAndValidate(TO_VERSION, emptyList()).use { connection ->
            assertEquals(listOf("1"), queryColumn(connection, "SELECT COUNT(*) FROM bootloader_ota_quirks_cache"))
            assertEquals(
                listOf("[{\"target\":\"rak4631\",\"variant\":\"7.3.0\"}]"),
                queryColumn(connection, "SELECT soft_device_variants_json FROM bootloader_ota_quirks_cache"),
            )
            assertEquals(
                listOf("[{\"hwModel\":\"HELTEC_V3\"}]"),
                queryColumn(connection, "SELECT devices_json FROM bootloader_ota_quirks_cache"),
            )
            assertEquals(listOf("1"), queryColumn(connection, "SELECT COUNT(*) FROM maintenance_uf2_cache"))
            assertEquals(
                listOf("{\"erase\":{}}"),
                queryColumn(connection, "SELECT manifest_json FROM maintenance_uf2_cache"),
            )
            val chats = "FROM quick_chat ORDER BY uuid"
            assertEquals(listOf("2"), queryColumn(connection, "SELECT COUNT(*) FROM quick_chat"))
            assertEquals(listOf("1", "2"), queryColumn(connection, "SELECT uuid $chats"))
            assertEquals(listOf("Hi", "Ok"), queryColumn(connection, "SELECT name $chats"))
            assertEquals(listOf("hello", "on my way"), queryColumn(connection, "SELECT message $chats"))
            assertEquals(listOf("Append", "Instant"), queryColumn(connection, "SELECT mode $chats"))
            assertEquals(listOf("0", "1"), queryColumn(connection, "SELECT position $chats"))

            assertTrue(
                "mqtt_roots_cache" in queryColumn(connection, "SELECT name FROM sqlite_master WHERE type = 'table'"),
            )
            assertEquals(listOf("0"), queryColumn(connection, "SELECT COUNT(*) FROM mqtt_roots_cache"))
            connection.execSQL("INSERT INTO mqtt_roots_cache (id, roots_json) VALUES (0, '{}')")
            assertEquals(listOf("{}"), queryColumn(connection, "SELECT roots_json FROM mqtt_roots_cache"))
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

    private companion object {
        const val FROM_VERSION = 64
        const val TO_VERSION = 65
    }
}
