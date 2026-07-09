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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Source
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.EventFirmwareEditionLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.EventFirmwareBuild
import org.meshtastic.core.model.EventFirmwareEdition
import org.meshtastic.core.model.EventFirmwareFonts
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.EventFirmwareTheme
import org.meshtastic.core.model.EventFirmwareThemeColors
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.EventFirmwareRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventFirmwareRepositoryImplTest {

    /** Only [getEventFirmware] is exercised; the other endpoints are never called by this repository. */
    private class FakeApiService(var response: EventFirmwareResponse) : ApiService {
        var eventFirmwareCalls = 0
            private set

        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = error("unused")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")

        override suspend fun getEventFirmware(): EventFirmwareResponse {
            eventFirmwareCalls++
            return response
        }
    }

    /** Serves only `event_firmware.json`, serializing [editions] so the repo decodes via the real path. */
    private class FakeBundledAssetReader(
        var editions: List<EventFirmwareEdition>,
        private val json: Json,
        var present: Boolean = true,
    ) : BundledAssetReader {
        override fun open(name: String): Source? {
            if (name != "event_firmware.json" || !present) return null
            val bytes = json.encodeToString(EventFirmwareResponse(editions = editions)).encodeToByteArray()
            return Buffer().write(bytes)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Real dispatchers + runBlocking (per test), NOT runTest/UnconfinedTestDispatcher — see the comment in
    // DeviceLinkRepositoryImplTest for why runTest's virtual clock flakes the withTimeoutOrNull-bounded refresh.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: EventFirmwareEditionLocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: EventFirmwareRepositoryImpl

    private fun edition(name: String) =
        EventFirmwareEdition(edition = name, displayName = name.lowercase(), welcomeMessage = "hi $name")

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = EventFirmwareEditionLocalDataSource(dbProvider, dispatchers)
        api = FakeApiService(EventFirmwareResponse())
        seed = FakeBundledAssetReader(emptyList(), json)
        repository =
            EventFirmwareRepositoryImpl(
                remoteDataSource = EventFirmwareRemoteDataSource(api, dispatchers),
                assetReader = seed,
                json = json,
                localDataSource = local,
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun getEditionReturnsMatchingRecordByEnumName() = runBlocking {
        seed.editions = listOf(edition("HAMVENTION"), edition("DEFCON"))

        assertEquals("hamvention", repository.getEdition("HAMVENTION")?.displayName)
        assertEquals("hi DEFCON", repository.getEdition("DEFCON")?.welcomeMessage)
    }

    @Test
    fun getEditionReturnsNullForUnknownEdition() = runBlocking {
        seed.editions = listOf(edition("HAMVENTION"))

        assertNull(repository.getEdition("VANILLA"))
    }

    @Test
    fun absentAssetYieldsNullWithoutCrashing() = runBlocking {
        seed.present = false

        assertNull(repository.getEdition("HAMVENTION"))
    }

    @Test
    fun seedsFromBundledJsonOnlyWhenTableEmpty() = runBlocking {
        seed.editions = listOf(edition("HAMVENTION"))
        repository.getEdition("HAMVENTION")
        assertEquals(1, local.count())

        // A larger snapshot must NOT re-seed once the table is populated.
        seed.editions = seed.editions + edition("DEFCON")
        repository.getEdition("DEFCON")
        assertEquals(1, local.count())
    }

    @Test
    fun networkRefreshReplacesBundledSnapshot() = runBlocking {
        seed.editions = listOf(edition("HAMVENTION"))
        api.response = EventFirmwareResponse(editions = listOf(edition("DEFCON")))

        // First call is always stale (never refreshed yet), so it triggers + awaits the network fetch.
        assertNull(repository.getEdition("HAMVENTION"))
        assertEquals("hi DEFCON", repository.getEdition("DEFCON")?.welcomeMessage)
    }

    @Test
    fun emptyNetworkResponseLeavesBundledSnapshotInPlace() = runBlocking {
        seed.editions = listOf(edition("HAMVENTION"))
        api.response = EventFirmwareResponse(editions = emptyList())

        assertEquals("hamvention", repository.getEdition("HAMVENTION")?.displayName)
    }

    @Test
    fun failedRefreshDoesNotRetryOnEveryCall() = runBlocking {
        // Empty response never advances the success timestamp; without the retry cooldown the second call would
        // re-enter the stale branch and fetch again. The cooldown (minutes) means a second immediate call skips it.
        seed.editions = listOf(edition("HAMVENTION"))
        api.response = EventFirmwareResponse(editions = emptyList())

        repository.getEdition("HAMVENTION")
        repository.getEdition("HAMVENTION")

        assertEquals(1, api.eventFirmwareCalls)
    }

    @Test
    fun v2FieldsRoundTripThroughCache() = runBlocking {
        seed.editions =
            listOf(
                EventFirmwareEdition(
                    edition = "HAMVENTION",
                    displayName = "Dayton Hamvention 2026",
                    welcomeMessage = "hi",
                    tag = "Hamvention",
                    domain = "hamvention.meshtastic.org",
                    theme =
                    EventFirmwareTheme(
                        name = "Radio Adventure",
                        palette = listOf("#BF1E2E"),
                        colors = EventFirmwareThemeColors(primary = "#BF1E2E"),
                        fonts = EventFirmwareFonts(heading = "Lato", body = "Atkinson Hyperlegible"),
                    ),
                    firmware = EventFirmwareBuild(version = "2.7.23.07741e6", zipUrl = "https://example/f.zip"),
                ),
            )

        val got = repository.getEdition("HAMVENTION")!!

        assertEquals("Hamvention", got.tag)
        assertEquals("hamvention.meshtastic.org", got.domain)
        assertEquals("Radio Adventure", got.theme?.name)
        assertEquals("#BF1E2E", got.theme?.colors?.primary)
        assertEquals(listOf("#BF1E2E"), got.theme?.palette)
        assertEquals("Lato", got.theme?.fonts?.heading)
        assertEquals("Atkinson Hyperlegible", got.theme?.fonts?.body)
        assertEquals("2.7.23.07741e6", got.firmware?.version)
        assertEquals("https://example/f.zip", got.firmware?.zipUrl)
    }

    @Test
    fun persistsAcrossRepositoryInstancesBackedByTheSameDb() = runBlocking {
        seed.editions = listOf(edition("HAMVENTION"))
        repository.getEdition("HAMVENTION")

        // A fresh repository instance (e.g. after a process restart) reads the same DB without re-seeding.
        seed.editions = emptyList()
        val restarted =
            EventFirmwareRepositoryImpl(
                remoteDataSource = EventFirmwareRemoteDataSource(api, dispatchers),
                assetReader = seed,
                json = json,
                localDataSource = local,
                dispatchers = dispatchers,
            )

        assertEquals("hamvention", restarted.getEdition("HAMVENTION")?.displayName)
    }
}
