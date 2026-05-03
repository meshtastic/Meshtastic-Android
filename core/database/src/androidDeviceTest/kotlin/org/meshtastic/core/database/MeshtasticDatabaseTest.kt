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
import androidx.room3.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MeshtasticDatabaseTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MeshtasticDatabase::class.java)

    @org.junit.Ignore("KMP Android Library does not package Room schemas into test assets currently")
    @Test
    @Throws(IOException::class)
    fun migrateAll() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Create earliest version of the database.
        helper.createDatabase(TEST_DB, 3).apply { close() }

        // Open latest version of the database. Room validates the schema
        // once all migrations execute.
        Room.databaseBuilder<MeshtasticDatabase>(
            context = context,
            name = context.getDatabasePath(TEST_DB).absolutePath,
            factory = { MeshtasticDatabaseConstructor.initialize() },
        )
            .configureCommon()
            .build()
            .apply {
                openHelper.writableDatabase
                close()
            }
    }
}
