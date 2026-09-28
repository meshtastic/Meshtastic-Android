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
package org.meshtastic.core.prefs.di

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreatePreferencesDataStoreTest {

    @Test
    fun `a corrupt preferences file reads as empty and accepts new writes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.preferencesDataStoreFile(FILE_NAME)
        file.parentFile?.mkdirs()
        // A varint whose continuation bit never clears cannot parse as a protobuf.
        file.writeBytes(ByteArray(GARBAGE_LENGTH) { 0xFF.toByte() })

        val dispatcher = StandardTestDispatcher(testScheduler)
        val store =
            createPreferencesDataStore(
                context,
                CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher),
                legacyName = "corrupt-prefs",
                fileName = FILE_NAME,
            )

        assertTrue(store.data.first().asMap().isEmpty())

        store.edit { it[KEY] = true }
        assertEquals(true, store.data.first()[KEY])
    }

    private companion object {
        const val FILE_NAME = "corrupt_ds"
        const val GARBAGE_LENGTH = 16
        val KEY = booleanPreferencesKey("written-after-recovery")
    }
}
