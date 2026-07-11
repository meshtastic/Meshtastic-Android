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
import org.meshtastic.core.data.datasource.DeviceLinkLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLink
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.DeviceLinksRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceLinkRepositoryImplTest {

    /** Only [getDeviceLinks] is exercised; the other endpoints are never called by the link repository. */
    private class FakeApiService(var response: NetworkDeviceLinksResponse) : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = response

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")

        override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? = error("unused")

        override suspend fun getEventFirmware(): EventFirmwareResponse = error("unused")
    }

    /** Serves only `device_links.json`, serializing the current [links] so the repo seeds via the real decode path. */
    private class FakeBundledAssetReader(var links: List<NetworkDeviceLink>, private val json: Json) :
        BundledAssetReader {
        override fun open(name: String): Source? {
            if (name != "device_links.json") return null
            val bytes = json.encodeToString(NetworkDeviceLinksResponse(links = links)).encodeToByteArray()
            return Buffer().write(bytes)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Real dispatchers + runBlocking (per test), NOT runTest/UnconfinedTestDispatcher. reconcile() guards its network
    // fetch with withTimeoutOrNull, whose deadline follows the calling coroutine's clock. Under runTest that clock is
    // virtual and runTest fast-forwards it while the coroutine parks on Room's real IO dispatcher; under load the 5s
    // budget "elapsed" in virtual time, so the fetch was treated as timed out, store() was skipped, and the cache kept
    // stale rows (reconcilePrunes... flaked). On the wall clock the instant fake never times out.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: DeviceLinkLocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: DeviceLinkRepositoryImpl

    private fun link(
        shortCode: String,
        type: String = NetworkDeviceLink.TYPE_VENDOR,
        targets: List<String>? = null,
        regions: List<String>? = null,
    ) = NetworkDeviceLink(
        shortCode = shortCode,
        url = "https://msh.to/$shortCode",
        description = shortCode,
        type = type,
        targets = targets,
        regions = regions,
    )

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = DeviceLinkLocalDataSource(dbProvider)
        api = FakeApiService(NetworkDeviceLinksResponse())
        seed = FakeBundledAssetReader(emptyList(), json)
        repository =
            DeviceLinkRepositoryImpl(
                remoteDataSource = DeviceLinksRemoteDataSource(api, dispatchers),
                assetReader = seed,
                json = json,
                localDataSource = local,
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun seedsFromBundledJsonWhenEmptyAndDropsInternalLinks() = runBlocking {
        seed.links =
            listOf(link("rak4631", targets = listOf("rak4631")), link("github", type = NetworkDeviceLink.TYPE_INTERNAL))
        repository.ensureImported()

        assertEquals(setOf("rak4631"), local.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun ensureImportedSeedsOnlyWhenEmpty() = runBlocking {
        seed.links = listOf(link("rak4631", targets = listOf("rak4631")))
        repository.ensureImported()
        assertEquals(1, local.count())

        // A larger snapshot must NOT re-seed once the table is populated.
        seed.links = seed.links + link("heltec-v3", targets = listOf("heltec-v3"))
        repository.ensureImported()
        assertEquals(1, local.count())
    }

    @Test
    fun getLinksForTargetFiltersByTargetAndRegionVendorFirst() = runBlocking {
        api.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(
                    link(
                        "rokland-rak4631",
                        type = NetworkDeviceLink.TYPE_MARKETPLACE,
                        targets = listOf("rak4631"),
                        regions = listOf("US"),
                    ),
                    link("rak4631", targets = listOf("rak4631")),
                    link("heltec-v3", targets = listOf("heltec-v3")),
                    link(
                        "de-only",
                        type = NetworkDeviceLink.TYPE_MARKETPLACE,
                        targets = listOf("rak4631"),
                        regions = listOf("DE"),
                    ),
                ),
            )
        repository.reconcile()

        val links = repository.getLinksForTarget("rak4631", regionCode = "US")

        // de-only filtered by region; heltec-v3 filtered by target; vendor sorted ahead of marketplace.
        assertEquals(listOf("rak4631", "rokland-rak4631"), links.map { it.shortCode })
        assertTrue(links.first().isVendor)
    }

    @Test
    fun worldwideLinksShowRegardlessOfRegion() = runBlocking {
        api.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(link("ww", type = NetworkDeviceLink.TYPE_MARKETPLACE, targets = listOf("t"), regions = null)),
            )
        repository.reconcile()

        assertEquals(listOf("ww"), repository.getLinksForTarget("t", regionCode = "ZZ").map { it.shortCode })
    }

    @Test
    fun reconcilePrunesShortCodesNoLongerInCatalog() = runBlocking {
        api.response =
            NetworkDeviceLinksResponse(
                links = listOf(link("a", targets = listOf("t")), link("b", targets = listOf("t"))),
            )
        repository.reconcile()
        assertEquals(2, local.count())

        api.response = NetworkDeviceLinksResponse(links = listOf(link("a", targets = listOf("t"))))
        repository.reconcile()
        assertEquals(setOf("a"), local.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun emptyResponseLeavesCacheUntouched() = runBlocking {
        api.response = NetworkDeviceLinksResponse(links = listOf(link("a", targets = listOf("t"))))
        repository.reconcile()
        assertEquals(1, local.count())

        api.response = NetworkDeviceLinksResponse(links = emptyList())
        repository.reconcile()
        assertEquals(1, local.count())
    }
}
