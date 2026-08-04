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
package org.meshtastic.app.map.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.asMapTileProviderDataStore
import org.meshtastic.core.prefs.map.MapTileProviderPrefsImpl
import org.meshtastic.core.repository.MapTileProviderPrefs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Guards the offline map surviving an app restart.
 *
 * The repository used to seed itself with an empty list and read the stored value synchronously at construction, before
 * the store had answered. That lost the imported providers on every cold start, and — worse — the next edit persisted a
 * list built on the empty baseline, destroying what was on disk rather than merely failing to show it.
 */
class CustomTileProviderRepositoryTest {

    private val json = Json

    private val scan25 =
        CustomTileProviderConfig(
            name = "SCAN 25",
            urlTemplate = "",
            localUri = "file:///data/user/0/app/files/map_layers/mbtiles_scan25.mbtiles",
        )
    private val planIgn =
        CustomTileProviderConfig(
            name = "Plan IGN",
            urlTemplate = "",
            localUri = "file:///data/user/0/app/files/map_layers/mbtiles_planign.mbtiles",
        )

    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var testScope: TestScope
    private lateinit var dispatchers: CoroutineDispatchers

    @Before
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        dispatchers = CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "customTileProviderRepositoryTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
    }

    @After
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    /** A repository built over the persistent store — a new one stands in for a cold app start. */
    private fun coldStart(): CustomTileProviderRepository = CustomTileProviderRepositoryImpl(
        json = json,
        dispatchers = dispatchers,
        mapTileProviderPrefs = MapTileProviderPrefsImpl(dataStore.asMapTileProviderDataStore(), dispatchers),
    )

    private fun repositoryOver(prefs: MapTileProviderPrefs): CustomTileProviderRepository =
        CustomTileProviderRepositoryImpl(json = json, dispatchers = dispatchers, mapTileProviderPrefs = prefs)

    @Test
    fun `an imported offline map is still there after a cold start`() = testScope.runTest {
        coldStart().addCustomTileProvider(scan25)

        val restored = coldStart().getCustomTileProviders().first()

        assertEquals(listOf(scan25), restored)
    }

    @Test
    fun `editing after a cold start keeps the providers already stored`() = testScope.runTest {
        coldStart().addCustomTileProvider(scan25)

        // No read before the write: a cold-started repository must still fetch the stored list first.
        coldStart().addCustomTileProvider(planIgn)

        assertEquals(listOf(scan25, planIgn), coldStart().getCustomTileProviders().first())
    }

    @Test
    fun `nothing is published while the store has not answered yet`() = testScope.runTest {
        val repository = repositoryOver(SilentStore())

        val published = withTimeoutOrNull(1.seconds) { repository.getCustomTileProviders().first() }

        // An empty list here would be indistinguishable from "no providers saved", which is what used to leak out.
        assertNull(published)
    }

    @Test
    fun `a write waits for the stored list rather than overwriting it`() = testScope.runTest {
        val store = SilentStore()
        val repository = repositoryOver(store)

        val write = launch { repository.addCustomTileProvider(planIgn) }
        assertNull(store.written, "wrote before knowing what was already stored")

        store.publish(json.encodeToString(listOf(scan25)))
        write.join()

        val stored = json.decodeFromString<List<CustomTileProviderConfig>>(store.written.orEmpty())
        assertEquals(listOf(scan25, planIgn), stored)
    }

    /** A store that stays silent until [publish] is called, standing in for a disk read still in flight. */
    private class SilentStore : MapTileProviderPrefs {
        private val emissions = MutableSharedFlow<String?>(replay = 1)

        var written: String? = null
            private set

        override val customTileProviders: Flow<String?> = emissions

        override fun setCustomTileProviders(providers: String?) {
            written = providers
            emissions.tryEmit(providers)
        }

        suspend fun publish(stored: String?) = emissions.emit(stored)
    }
}
