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

/** 65→66 adds `nodes.display_name`, the local-only name a user gives a node. */
class NodeDisplayNameMigrationTest {

    private val dbFile = File.createTempFile("meshtastic-display-name-migration", ".db").apply { delete() }

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
    fun displayNameColumnAddedWithEmptyDefaultAndPreservesNodes() = runTest {
        helper.createDatabase(FROM_VERSION).use { connection ->
            val columns =
                "num, user, position, latitude, longitude, snr, rssi, last_heard, device_metrics, channel, " +
                    "via_mqtt, hops_away, is_favorite, environment_metrics, power_metrics, paxcounter"
            connection.execSQL(
                "INSERT INTO nodes ($columns, long_name, notes) VALUES " +
                    "(42, x'', x'', 0.0, 0.0, 0.0, 0, 1000, x'', 0, 0, 1, 1, x'', x'', x'', 'Minnie Mouse', 'a note')",
            )
        }

        helper.runMigrationsAndValidate(TO_VERSION, emptyList()).use { connection ->
            assertEquals(listOf("42"), queryColumn(connection, "SELECT num FROM nodes"))
            assertEquals(listOf(""), queryColumn(connection, "SELECT display_name FROM nodes WHERE num = 42"))
            assertEquals(listOf("Minnie Mouse"), queryColumn(connection, "SELECT long_name FROM nodes WHERE num = 42"))
            assertEquals(listOf("a note"), queryColumn(connection, "SELECT notes FROM nodes WHERE num = 42"))
            assertEquals(listOf("1000"), queryColumn(connection, "SELECT last_heard FROM nodes WHERE num = 42"))
        }
    }

    private fun queryColumn(connection: SQLiteConnection, sql: String): List<String?> =
        connection.prepare(sql).use { statement ->
            buildList {
                while (statement.step()) {
                    add(if (statement.isNull(0)) null else statement.getText(0))
                }
            }
        }

    private companion object {
        const val FROM_VERSION = 65
        const val TO_VERSION = 66
    }
}
