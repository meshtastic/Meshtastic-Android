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
import org.meshtastic.core.data.datasource.DeviceLinkLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NetworkDeviceLink
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.network.DeviceLinksRemoteDataSource
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceLinkRepositoryImplTest {

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

    /** Seeds `device_links.json` through the real decode path. */
    private fun seedLinks(links: List<NetworkDeviceLink>) =
        seed.put("device_links.json", NetworkDeviceLinksResponse(links = links), json)

    private fun serveLinks(vararg links: NetworkDeviceLink) {
        api.deviceLinks = { NetworkDeviceLinksResponse(links = links.toList()) }
    }

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = DeviceLinkLocalDataSource(dbProvider)
        api = FakeApiService(deviceLinks = { NetworkDeviceLinksResponse() })
        seed = FakeBundledAssetReader()
        seedLinks(emptyList())
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
        val vendor = link("rak4631", targets = listOf("rak4631"))
        seedLinks(listOf(vendor, link("github", type = NetworkDeviceLink.TYPE_INTERNAL)))
        repository.ensureImported()

        assertEquals(setOf("rak4631"), local.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun ensureImportedSeedsOnlyWhenEmpty() = runBlocking {
        val first = listOf(link("rak4631", targets = listOf("rak4631")))
        seedLinks(first)
        repository.ensureImported()
        assertEquals(1, local.count())

        // A larger snapshot must NOT re-seed once the table is populated.
        seedLinks(first + link("heltec-v3", targets = listOf("heltec-v3")))
        repository.ensureImported()
        assertEquals(1, local.count())
    }

    @Test
    fun getLinksForTargetFiltersByTargetAndRegionVendorFirst() = runBlocking {
        serveLinks(
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
        )
        repository.reconcile()

        val links = repository.getLinksForTarget("rak4631", regionCode = "US")

        // de-only filtered by region; heltec-v3 filtered by target; vendor sorted ahead of marketplace.
        assertEquals(listOf("rak4631", "rokland-rak4631"), links.map { it.shortCode })
        assertTrue(links.first().isVendor)
    }

    @Test
    fun worldwideLinksShowRegardlessOfRegion() = runBlocking {
        serveLinks(link("ww", type = NetworkDeviceLink.TYPE_MARKETPLACE, targets = listOf("t"), regions = null))
        repository.reconcile()

        assertEquals(listOf("ww"), repository.getLinksForTarget("t", regionCode = "ZZ").map { it.shortCode })
    }

    @Test
    fun reconcilePrunesShortCodesNoLongerInCatalog() = runBlocking {
        serveLinks(link("a", targets = listOf("t")), link("b", targets = listOf("t")))
        repository.reconcile()
        assertEquals(2, local.count())

        serveLinks(link("a", targets = listOf("t")))
        repository.reconcile()
        assertEquals(setOf("a"), local.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun emptyResponseLeavesCacheUntouched() = runBlocking {
        serveLinks(link("a", targets = listOf("t")))
        repository.reconcile()
        assertEquals(1, local.count())

        serveLinks()
        repository.reconcile()
        assertEquals(1, local.count())
    }
}
