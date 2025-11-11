/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseManagerLegacyCleanupTest {
    @Test
    fun deletes_legacy_db_on_switch_when_flag_not_set() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("db-manager-prefs", Context.MODE_PRIVATE)

        // Reset the one-time flag
        prefs.edit().remove(DatabaseConstants.LEGACY_DB_CLEANED_KEY).apply()

        // Ensure legacy DB file exists
        val legacyName = DatabaseConstants.LEGACY_DB_NAME
        val legacyFile = app.getDatabasePath(legacyName)
        // Create or overwrite the legacy DB file by opening it once
        app.openOrCreateDatabase(legacyName, Context.MODE_PRIVATE, null).close()
        assertTrue("Precondition: legacy DB should exist before switch", legacyFile.exists())

        val manager = DatabaseManager(app)

        // Switch to a non-null address so active DB != legacy
        manager.switchActiveDatabase("01:23:45:67:89:AB")

        // Cleanup runs asynchronously; wait briefly for deletion
        var attempts = 0
        while (legacyFile.exists() && attempts < 20) {
            delay(100)
            attempts++
        }

        assertFalse("Legacy DB should be deleted after switch", legacyFile.exists())
    }
}
