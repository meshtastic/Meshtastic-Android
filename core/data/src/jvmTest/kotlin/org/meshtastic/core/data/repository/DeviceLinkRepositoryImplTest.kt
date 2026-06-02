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

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.data.datasource.DeviceLinkLocalDataSource
import org.meshtastic.core.data.datasource.MshToLinksJsonDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.MshToMarketplace
import org.meshtastic.core.model.MshToRoute
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceLinkRepositoryImplTest {

    private class FakeMshToLinksJsonDataSource(
        var routes: List<MshToRoute>,
        var marketplaces: Map<String, MshToMarketplace>,
    ) : MshToLinksJsonDataSource {
        override fun loadRoutes(): List<MshToRoute> = routes

        override fun loadMarketplaces(): Map<String, MshToMarketplace> = marketplaces
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var linkLocal: DeviceLinkLocalDataSource
    private lateinit var hardwareLocal: DeviceHardwareLocalDataSource
    private lateinit var json: FakeMshToLinksJsonDataSource
    private lateinit var repository: DeviceLinkRepositoryImpl

    private val marketplaces =
        mapOf(
            "rokland" to MshToMarketplace(regions = listOf("US"), match = "prefix"),
            "aliexpress" to MshToMarketplace(regions = emptyList(), match = "suffix"),
        )

    private fun route(shortCode: String) =
        MshToRoute(shortCode = shortCode, originalUrl = "https://example.com/$shortCode", description = shortCode)

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        linkLocal = DeviceLinkLocalDataSource(dbProvider, dispatchers)
        hardwareLocal = DeviceHardwareLocalDataSource(dbProvider, dispatchers)
        json =
            FakeMshToLinksJsonDataSource(
                routes =
                listOf(route("rak4631"), route("rokland-rak4631"), route("rak4631_aliexpress"), route("github")),
                marketplaces = marketplaces,
            )
        repository = DeviceLinkRepositoryImpl(json, linkLocal, hardwareLocal)
    }

    @AfterTest fun tearDown() = dbProvider.close()

    private suspend fun seedDeviceTargets(vararg targets: String) {
        hardwareLocal.insertAllDeviceHardware(
            targets.mapIndexed { i, t -> NetworkDeviceHardware(hwModel = i + 1, platformioTarget = t) },
        )
    }

    @Test
    fun importClassifiesVendorAndMarketplaceLinks() = runTest(dispatcher) {
        seedDeviceTargets("rak4631", "heltec-v3")
        repository.reconcile()

        val byCode = linkLocal.getAll().associateBy { it.shortCode }
        assertEquals(4, byCode.size)

        // rak4631 is a known device target → vendor, no regions.
        assertTrue(byCode.getValue("rak4631").isVendor)
        assertNull(byCode.getValue("rak4631").regions)

        // rokland-rak4631 → prefix marketplace, region-tagged.
        assertTrue(!byCode.getValue("rokland-rak4631").isVendor)
        assertEquals(listOf("US"), byCode.getValue("rokland-rak4631").regions)

        // rak4631_aliexpress → suffix marketplace, worldwide (empty regions).
        assertEquals(emptyList(), byCode.getValue("rak4631_aliexpress").regions)

        // github → neither vendor nor marketplace, null regions.
        assertTrue(!byCode.getValue("github").isVendor)
        assertNull(byCode.getValue("github").regions)
    }

    @Test
    fun reconcilePrunesOrphanedShortCodes() = runTest(dispatcher) {
        seedDeviceTargets("rak4631")
        repository.reconcile()
        assertEquals(4, linkLocal.count())

        // Drop "github" from the bundled file and reconcile again.
        json.routes = json.routes.filterNot { it.shortCode == "github" }
        repository.reconcile()

        val codes = linkLocal.getAll().map { it.shortCode }.toSet()
        assertEquals(setOf("rak4631", "rokland-rak4631", "rak4631_aliexpress"), codes)
    }

    @Test
    fun ensureImportedSeedsOnlyWhenEmpty() = runTest(dispatcher) {
        seedDeviceTargets("rak4631")
        repository.ensureImported()
        assertEquals(4, linkLocal.count())

        // A second ensureImported with a larger bundled file must NOT re-import (table already populated).
        json.routes = json.routes + route("new-code")
        repository.ensureImported()
        assertEquals(4, linkLocal.count())
    }
}
