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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented (on-device) equivalent of [MeshtasticDatabaseMigrationTest] (the JVM test that actually runs in CI).
 * This walks every exported schema from v3 to the current version on a real device's SQLite.
 *
 * The exported schema JSONs are made available to this test via the `androidDeviceTest/assets` symlink into the
 * module's `schemas/` directory; the android [MigrationTestHelper] loads them from the test APK assets.
 *
 * NOTE: `androidDeviceTest` (instrumented) tasks are not run by the PR pipeline — only `allTests` (JVM + Robolectric)
 * is — so migration coverage in CI comes from [MeshtasticDatabaseMigrationTest]; this test adds real-device coverage
 * when run locally via `connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class MeshtasticDatabaseTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val dbFile = instrumentation.targetContext.getDatabasePath(TEST_DB)

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            instrumentation = instrumentation,
            file = dbFile,
            driver = BundledSQLiteDriver(),
            databaseClass = MeshtasticDatabase::class,
            databaseFactory = { MeshtasticDatabaseConstructor.initialize() },
        )

    @Before
    fun deleteStaleDb() {
        instrumentation.targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    @Throws(IOException::class)
    fun migrateAll(): Unit = runBlocking {
        helper.createDatabase(EARLIEST_SCHEMA_VERSION).close()
        // No manual migrations: every version bump is an @AutoMigration, so Room derives the full path itself.
        helper.runMigrationsAndValidate(latestSchemaVersion(), emptyList()).close()
    }

    private fun latestSchemaVersion(): Int {
        val dbFqn = checkNotNull(MeshtasticDatabase::class.qualifiedName)
        return checkNotNull(instrumentation.context.assets.list(dbFqn))
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .maxOrNull() ?: error("No exported Room schemas found in test assets folder $dbFqn")
    }

    companion object {
        private const val TEST_DB = "migration-test"
        private const val EARLIEST_SCHEMA_VERSION = 3
    }
}
