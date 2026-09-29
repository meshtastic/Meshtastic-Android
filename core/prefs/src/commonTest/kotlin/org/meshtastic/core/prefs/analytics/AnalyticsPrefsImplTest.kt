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
package org.meshtastic.core.prefs.analytics

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.asAnalyticsDataStore
import org.meshtastic.core.prefs.di.asAppDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AnalyticsPrefsImplTest {
    private lateinit var tmpDir: Path
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var dispatchers: CoroutineDispatchers

    @BeforeTest
    fun setup() {
        // Standard, not Unconfined: the store must not have loaded when the first read happens.
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "analyticsPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    private fun store(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.createWithPath(scope = testScope, produceFile = { tmpDir / "$name.preferences_pb" })

    private fun prefs(analytics: DataStore<Preferences>) =
        AnalyticsPrefsImpl(analytics.asAnalyticsDataStore(), store("app").asAppDataStore(), dispatchers)

    @Test
    fun `a stored opt out is not allowed before or after the store loads`() = testScope.runTest {
        val analytics = store("analytics")
        analytics.edit { it[AnalyticsPrefsImpl.KEY_ANALYTICS_ALLOWED_PREF] = false }

        val prefs = prefs(analytics)

        assertFalse(prefs.analyticsAllowed.value, "first read, before the store loads")
        advanceUntilIdle()
        assertFalse(prefs.analyticsAllowed.value, "after the store loads")
    }

    @Test
    fun `no stored choice is allowed only once the store loads`() = testScope.runTest {
        val prefs = prefs(store("analytics"))

        assertFalse(prefs.analyticsAllowed.value, "first read, before the store loads")
        advanceUntilIdle()
        assertTrue(prefs.analyticsAllowed.value, "after the store loads")
    }
}
