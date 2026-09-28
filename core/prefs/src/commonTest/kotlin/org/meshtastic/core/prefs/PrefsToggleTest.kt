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
package org.meshtastic.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.analytics.AnalyticsPrefsImpl
import org.meshtastic.core.prefs.appfunctions.AppFunctionsPrefsImpl
import org.meshtastic.core.prefs.di.asAnalyticsDataStore
import org.meshtastic.core.prefs.di.asAppDataStore
import org.meshtastic.core.prefs.di.asHomoglyphEncodingDataStore
import org.meshtastic.core.prefs.di.asUiDataStore
import org.meshtastic.core.prefs.homoglyph.HomoglyphPrefsImpl
import org.meshtastic.core.prefs.ui.UiPrefsImpl
import org.meshtastic.core.repository.AppFunctionsPrefs
import org.meshtastic.core.repository.AppFunctionsSetting
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class PrefsToggleTest {
    private lateinit var tmpDir: Path
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var dispatchers: CoroutineDispatchers

    @BeforeTest
    fun setup() {
        // Standard, not Unconfined: both toggles must be queued before either write lands.
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "prefsToggleTest-${Uuid.random()}"
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

    @Test
    fun `quick chat toggle turns the default off state on`() = testScope.runTest {
        val dataStore = store("ui")
        val prefs = UiPrefsImpl(dataStore.asUiDataStore(), dispatchers)

        prefs.toggleShowQuickChat()
        advanceUntilIdle()

        assertEquals(true, dataStore.data.first()[UiPrefsImpl.KEY_SHOW_QUICK_CHAT_PREF])
    }

    @Test
    fun `two rapid quick chat toggles both flip and land back on off`() = testScope.runTest {
        val dataStore = store("ui")
        val prefs = UiPrefsImpl(dataStore.asUiDataStore(), dispatchers)

        prefs.toggleShowQuickChat()
        prefs.toggleShowQuickChat()
        advanceUntilIdle()

        assertEquals(false, dataStore.data.first()[UiPrefsImpl.KEY_SHOW_QUICK_CHAT_PREF])
    }

    @Test
    fun `analytics toggle turns the default allowed state off`() = testScope.runTest {
        val dataStore = store("analytics")
        val prefs = AnalyticsPrefsImpl(dataStore.asAnalyticsDataStore(), store("app").asAppDataStore(), dispatchers)

        prefs.toggleAnalyticsAllowed()
        advanceUntilIdle()

        assertEquals(false, dataStore.data.first()[AnalyticsPrefsImpl.KEY_ANALYTICS_ALLOWED_PREF])
    }

    @Test
    fun `two rapid analytics toggles both flip and land back on allowed`() = testScope.runTest {
        val dataStore = store("analytics")
        val prefs = AnalyticsPrefsImpl(dataStore.asAnalyticsDataStore(), store("app").asAppDataStore(), dispatchers)

        prefs.toggleAnalyticsAllowed()
        prefs.toggleAnalyticsAllowed()
        advanceUntilIdle()

        assertEquals(true, dataStore.data.first()[AnalyticsPrefsImpl.KEY_ANALYTICS_ALLOWED_PREF])
    }

    @Test
    fun `homoglyph toggle turns the default off state on`() = testScope.runTest {
        val dataStore = store("homoglyph")
        val prefs = HomoglyphPrefsImpl(dataStore.asHomoglyphEncodingDataStore(), dispatchers)

        prefs.toggleHomoglyphEncodingEnabled()
        advanceUntilIdle()

        assertEquals(true, dataStore.data.first()[HomoglyphPrefsImpl.KEY_ENABLED_PREF])
    }

    @Test
    fun `two rapid homoglyph toggles both flip and land back on off`() = testScope.runTest {
        val dataStore = store("homoglyph")
        val prefs = HomoglyphPrefsImpl(dataStore.asHomoglyphEncodingDataStore(), dispatchers)

        prefs.toggleHomoglyphEncodingEnabled()
        prefs.toggleHomoglyphEncodingEnabled()
        advanceUntilIdle()

        assertEquals(false, dataStore.data.first()[HomoglyphPrefsImpl.KEY_ENABLED_PREF])
    }

    @Test
    fun `an app functions toggle turns only its own setting off`() = testScope.runTest {
        AppFunctionsSetting.entries.forEach { setting ->
            val prefs = AppFunctionsPrefsImpl(store("appfn-${setting.name}").asAppDataStore(), dispatchers)

            prefs.toggle(setting)
            advanceUntilIdle()

            AppFunctionsSetting.entries.forEach { other ->
                assertEquals(other != setting, prefs.enabled(other).value, "toggled $setting then read $other")
            }
        }
    }

    @Test
    fun `two rapid app functions toggles both flip and land back on enabled`() = testScope.runTest {
        AppFunctionsSetting.entries.forEach { setting ->
            val dataStore = store("appfn-${setting.name}")
            val prefs = AppFunctionsPrefsImpl(dataStore.asAppDataStore(), dispatchers)

            prefs.toggle(setting)
            prefs.toggle(setting)
            advanceUntilIdle()

            assertEquals(listOf<Any>(true), dataStore.data.first().asMap().values.toList(), setting.name)
        }
    }

    private fun AppFunctionsPrefs.enabled(setting: AppFunctionsSetting): StateFlow<Boolean> = when (setting) {
        AppFunctionsSetting.MASTER -> masterEnabled
        AppFunctionsSetting.SEND_MESSAGE -> sendMessageEnabled
        AppFunctionsSetting.GET_MESH_STATUS -> getMeshStatusEnabled
        AppFunctionsSetting.GET_NODE_LIST -> getNodeListEnabled
        AppFunctionsSetting.GET_CHANNEL_INFO -> getChannelInfoEnabled
        AppFunctionsSetting.GET_DEVICE_STATUS -> getDeviceStatusEnabled
        AppFunctionsSetting.GET_NODE_DETAILS -> getNodeDetailsEnabled
        AppFunctionsSetting.GET_MESH_METRICS -> getMeshMetricsEnabled
        AppFunctionsSetting.GET_RECENT_MESSAGES -> getRecentMessagesEnabled
        AppFunctionsSetting.GET_UNREAD_SUMMARY -> getUnreadSummaryEnabled
    }
}
