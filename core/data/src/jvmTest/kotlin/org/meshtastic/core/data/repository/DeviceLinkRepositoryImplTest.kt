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

import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.datasource.DeviceLinkLocalDataSource
import org.meshtastic.core.data.datasource.DeviceLinksJsonDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLink
import org.meshtastic.core.model.NetworkDeviceLinksResponse
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
    }

    private class FakeDeviceLinksJsonDataSource(var links: List<NetworkDeviceLink>) : DeviceLinksJsonDataSource {
        override fun loadDeviceLinksFromJsonAsset(): List<NetworkDeviceLink> = links
    }

    private lateinit var dispatcher: TestDispatcher
    private lateinit var dispatchers: CoroutineDispatchers

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: DeviceLinkLocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeDeviceLinksJsonDataSource
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
        dispatcher = UnconfinedTestDispatcher()
        dispatchers = CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher)
        dbProvider = FakeDatabaseProvider()
        local = DeviceLinkLocalDataSource(dbProvider, dispatchers)
        api = FakeApiService(NetworkDeviceLinksResponse())
        seed = FakeDeviceLinksJsonDataSource(emptyList())
        repository =
            DeviceLinkRepositoryImpl(
                remoteDataSource = DeviceLinksRemoteDataSource(api, dispatchers),
                jsonDataSource = seed,
                localDataSource = local,
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun seedsFromBundledJsonWhenEmptyAndDropsInternalLinks() = runTest(dispatcher) {
        seed.links =
            listOf(
                link("rak4631", targets = listOf("rak4631")),
                link("github", type = NetworkDeviceLink.TYPE_INTERNAL),
            )
        repository.ensureImported()

        assertEquals(setOf("rak4631"), local.getAll().map { it.shortCode }.toSet())
    }

    @Test
    fun ensureImportedSeedsOnlyWhenEmpty() = runTest(dispatcher) {
        seed.links = listOf(link("rak4631", targets = listOf("rak4631")))
        repository.ensureImported()
        assertEquals(1, local.count())

        // A larger snapshot must NOT re-seed once the table is populated.
        seed.links = seed.links + link("heltec-v3", targets = listOf("heltec-v3"))
        repository.ensureImported()
        assertEquals(1, local.count())
    }

    @Test
    fun getLinksForTargetFiltersByTargetAndRegionVendorFirst() = runTest(dispatcher) {
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
    fun worldwideLinksShowRegardlessOfRegion() = runTest(dispatcher) {
        api.response =
            NetworkDeviceLinksResponse(
                links =
                listOf(
                    link("ww", type = NetworkDeviceLink.TYPE_MARKETPLACE, targets = listOf("t"), regions = null),
                ),
            )
        repository.reconcile()

        assertEquals(listOf("ww"), repository.getLinksForTarget("t", regionCode = "ZZ").map { it.shortCode })
    }

    @Test
    fun reconcilePrunesShortCodesNoLongerInCatalog() = runTest(dispatcher) {
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
    fun emptyResponseLeavesCacheUntouched() = runTest(dispatcher) {
        api.response = NetworkDeviceLinksResponse(links = listOf(link("a", targets = listOf("t"))))
        repository.reconcile()
        assertEquals(1, local.count())

        api.response = NetworkDeviceLinksResponse(links = emptyList())
        repository.reconcile()
        assertEquals(1, local.count())
    }
}
