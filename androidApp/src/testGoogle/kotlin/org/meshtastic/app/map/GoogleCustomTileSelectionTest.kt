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
package org.meshtastic.app.map

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.maps.android.compose.MapType
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.prefs.map.GoogleCameraPosition
import org.meshtastic.app.map.prefs.map.GoogleMapSelectionPrefs
import org.meshtastic.app.map.prefs.map.GoogleMapsPrefs
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.app.map.repository.CustomTileProviderRepositoryImpl
import org.meshtastic.app.map.repository.CustomTileProviderSaveResult
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.testing.FakeMapPrefs
import org.meshtastic.core.testing.FakeMapTileProviderPrefs
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeNotificationPrefs
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.FakeUiPrefs
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GoogleCustomTileSelectionTest {
    private val duplicateUrl = "https://tiles.example.org/{z}/{x}/{y}.png"
    private val first = CustomTileProviderConfig(id = "first", name = "First", urlTemplate = duplicateUrl)
    private val second = CustomTileProviderConfig(id = "second", name = "Second", urlTemplate = duplicateUrl)

    @Test
    fun `provider id disambiguates duplicate URLs across repository update delete and reload`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefs = FakeMapTileProviderPrefs()
        val json = Json { ignoreUnknownKeys = true }
        prefs.customTileProviders.value = json.encodeToString(listOf(first, second))
        var repository = repository(json, dispatcher, prefs)

        assertEquals(second, repository.awaitCustomTileProviders().providers.findSelectedCustomTileProvider(second.id))

        val updatedSecond = second.copy(name = "Updated")
        assertEquals(CustomTileProviderSaveResult.SAVED, repository.updateCustomTileProvider(updatedSecond))

        repository = repository(json, dispatcher, prefs)
        val afterUpdate = repository.awaitCustomTileProviders().providers
        assertEquals(updatedSecond, afterUpdate.findSelectedCustomTileProvider(second.id))

        repository.deleteCustomTileProvider(first.id)
        repository = repository(json, dispatcher, prefs)
        val afterDeletingTwin = repository.awaitCustomTileProviders().providers
        assertEquals(updatedSecond, afterDeletingTwin.findSelectedCustomTileProvider(second.id))

        repository.deleteCustomTileProvider(second.id)
        repository = repository(json, dispatcher, prefs)
        assertNull(repository.awaitCustomTileProviders().providers.findSelectedCustomTileProvider(second.id))
    }

    @Test
    fun `legacy URL migration deterministically selects first matching provider`() {
        assertEquals(first, listOf(first, second).findLegacyCustomTileProvider(duplicateUrl))
    }

    @Test
    fun `local provider accepts blank URL while remote provider does not`() {
        val local = CustomTileProviderConfig(id = "local", name = "Local", urlTemplate = "", localUri = "file.mbtiles")
        val remote = CustomTileProviderConfig(id = "remote", name = "Remote", urlTemplate = "")

        assertTrue(local.hasValidGoogleTileSource())
        assertFalse(remote.hasValidGoogleTileSource())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `legacy generated provider id survives a cold reload after URL migration`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefs = FakeMapTileProviderPrefs()
        val googleMapsPrefs = FakeGoogleMapsPrefs(customTileUrl = duplicateUrl)
        val json = Json { ignoreUnknownKeys = true }
        val legacyJson = """[{"name":"Legacy","urlTemplate":"$duplicateUrl"}]"""
        prefs.customTileProviders.value = legacyJson

        Dispatchers.setMain(dispatcher)
        var httpClient: HttpClient? = null
        try {
            val application = ApplicationProvider.getApplicationContext<Application>()
            val mapPrefs = FakeMapPrefs()
            val client = HttpClient().also { httpClient = it }
            val mapLayersManager =
                MapLayersManager(
                    application = application,
                    dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
                    httpClient = client,
                    mapPrefs = mapPrefs,
                )

            assertNull(prefs.selectedCustomTileProviderId.value)
            assertEquals(duplicateUrl, googleMapsPrefs.selectedCustomTileUrl.value)

            val firstViewModel =
                mapViewModel(
                    dispatcher = dispatcher,
                    application = application,
                    mapLayersManager = mapLayersManager,
                    mapPrefs = mapPrefs,
                    repository = repository(json, dispatcher, prefs),
                    mapTileProviderPrefs = prefs,
                    googleMapsPrefs = googleMapsPrefs,
                )
            advanceUntilIdle()

            val persistedAfterMigration = prefs.customTileProviders.value!!
            val persistedProvider =
                json.decodeFromString<List<CustomTileProviderConfig>>(persistedAfterMigration).single()

            assertNotEquals(legacyJson, persistedAfterMigration)
            assertEquals(persistedProvider.id, prefs.selectedCustomTileProviderId.value)
            assertEquals(persistedProvider.id, firstViewModel.selectedCustomTileProviderId.value)
            assertNull(googleMapsPrefs.selectedCustomTileUrl.value)

            val coldViewModel =
                mapViewModel(
                    dispatcher = dispatcher,
                    application = application,
                    mapLayersManager = mapLayersManager,
                    mapPrefs = mapPrefs,
                    repository = repository(json, dispatcher, prefs),
                    mapTileProviderPrefs = prefs,
                    googleMapsPrefs = googleMapsPrefs,
                )
            advanceUntilIdle()

            assertEquals(persistedProvider.id, coldViewModel.selectedCustomTileProviderId.value)
            assertEquals(persistedProvider.id, prefs.selectedCustomTileProviderId.value)
        } finally {
            try {
                httpClient?.close()
            } finally {
                Dispatchers.resetMain()
            }
        }
    }

    private fun mapViewModel(
        dispatcher: CoroutineDispatcher,
        application: Application,
        mapLayersManager: MapLayersManager,
        mapPrefs: FakeMapPrefs,
        repository: CustomTileProviderRepository,
        mapTileProviderPrefs: FakeMapTileProviderPrefs,
        googleMapsPrefs: GoogleMapsPrefs,
    ): MapViewModel {
        val packetRepository = mock<PacketRepository>(MockMode.autofill)
        every { packetRepository.getWaypoints() } returns flowOf(emptyList())

        return MapViewModel(
            application = application,
            dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
            mapLayersManager = mapLayersManager,
            mapPrefs = mapPrefs,
            googleMapsPrefs = googleMapsPrefs,
            nodeRepository = FakeNodeRepository(),
            packetRepository = packetRepository,
            radioConfigRepository = FakeRadioConfigRepository(),
            radioController = FakeRadioController(),
            customTileProviderRepository = repository,
            mapTileProviderPrefs = mapTileProviderPrefs,
            uiPrefs = FakeUiPrefs(),
            notificationPrefs = FakeNotificationPrefs(),
            savedStateHandle = SavedStateHandle(),
        )
    }

    private class FakeGoogleMapsPrefs(customTileUrl: String?) : GoogleMapsPrefs {
        override val selectedGoogleMapType = MutableStateFlow<String?>(MapType.NORMAL.name)
        override val selectedCustomTileUrl = MutableStateFlow(customTileUrl)
        override val cameraPosition = MutableStateFlow<GoogleCameraPosition?>(null)

        override fun setSelectedGoogleMapType(value: String?) {
            selectedGoogleMapType.value = value
        }

        override fun setSelectedCustomTileUrl(value: String?) {
            selectedCustomTileUrl.value = value
        }

        override suspend fun awaitMapSelection() = GoogleMapSelectionPrefs(
            mapType = selectedGoogleMapType.value ?: MapType.NORMAL.name,
            customTileUrl = selectedCustomTileUrl.value,
        )

        override fun setCameraPosition(value: GoogleCameraPosition) {
            cameraPosition.value = value
        }
    }

    @Test
    fun `provider id takes precedence over a different legacy URL match`() {
        val selectedById =
            CustomTileProviderConfig(
                id = "selected",
                name = "Selected",
                urlTemplate = "https://selected.example.org/{z}/{x}/{y}.png",
            )

        val resolved =
            listOf(first, selectedById)
                .resolvePersistedCustomTileSelection(
                    selectedProviderId = selectedById.id,
                    legacySource = duplicateUrl,
                    providerLoadSuccessful = true,
                )

        assertEquals(selectedById, resolved.provider)
    }

    @Test
    fun `provider without a valid URL template is not selected`() {
        val invalid =
            CustomTileProviderConfig(
                id = "invalid",
                name = "Invalid",
                urlTemplate = "https://tiles.example.org/static.png",
            )

        val resolved =
            listOf(invalid)
                .resolvePersistedCustomTileSelection(
                    selectedProviderId = invalid.id,
                    legacySource = null,
                    providerLoadSuccessful = true,
                )

        assertNull(resolved.provider)
        assertTrue(resolved.canDiscardMissingSelection)
    }

    @Test
    fun `missing persisted selection is cleared only after providers load successfully`() {
        val failedLoad =
            emptyList<CustomTileProviderConfig>()
                .resolvePersistedCustomTileSelection(
                    selectedProviderId = "selected",
                    legacySource = duplicateUrl,
                    providerLoadSuccessful = false,
                )
        val successfulLoad =
            emptyList<CustomTileProviderConfig>()
                .resolvePersistedCustomTileSelection(
                    selectedProviderId = "selected",
                    legacySource = duplicateUrl,
                    providerLoadSuccessful = true,
                )

        assertNull(failedLoad.provider)
        assertFalse(failedLoad.canDiscardMissingSelection)
        assertNull(successfulLoad.provider)
        assertTrue(successfulLoad.canDiscardMissingSelection)
    }

    private fun repository(json: Json, dispatcher: CoroutineDispatcher, prefs: FakeMapTileProviderPrefs) =
        CustomTileProviderRepositoryImpl(
            json = json,
            dispatchers = CoroutineDispatchers(dispatcher, dispatcher, dispatcher),
            mapTileProviderPrefs = prefs,
        )
}
