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
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksLocalDataSource
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.SoftDeviceVariantEntry
import org.meshtastic.core.network.BootloaderOtaQuirksRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BootloaderOtaQuirksRepositoryImplTest {

    /** Only [getBootloaderOtaQuirks] is exercised; the other endpoints are never called by this repository. */
    private class FakeApiService(var response: BootloaderOtaQuirksResponse) : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = error("unused")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")

        override suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest = error("unused")

        override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? = error("unused")

        override suspend fun getEventFirmware(): EventFirmwareResponse = error("unused")

        override suspend fun getBootloaderOtaQuirks(): BootloaderOtaQuirksResponse = response

        override suspend fun getMaintenanceUf2Manifest(): MaintenanceUf2Manifest = error("unused")
    }

    /**
     * Serves only `device_bootloader_ota_quirks.json`, or nothing when [seed] is null (models the asset being absent).
     */
    private class FakeBundledAssetReader(var seed: BootloaderOtaQuirksResponse?, private val json: Json) :
        BundledAssetReader {
        override fun open(name: String): Source? {
            if (name != "device_bootloader_ota_quirks.json") return null
            val current = seed ?: return null
            return Buffer().write(json.encodeToString(current).encodeToByteArray())
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Real dispatchers + runBlocking, not runTest — reconcile() has no virtual-time interaction to worry about, but
    // this matches DeviceLinkRepositoryImplTest's rationale for staying off runTest's virtual clock near Room.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: BootloaderOtaQuirksLocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: BootloaderOtaQuirksRepositoryImpl

    private fun quirk(hwModel: Int, requiresUpgrade: Boolean = true) =
        BootloaderOtaQuirk(hwModel = hwModel, requiresBootloaderUpgradeForOta = requiresUpgrade)

    private fun variant(hwModel: Int, target: String, softDevice: String?) =
        SoftDeviceVariantEntry(hwModel = hwModel, platformioTargets = listOf(target), softDevice = softDevice)

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = BootloaderOtaQuirksLocalDataSource(dbProvider, dispatchers)
        api = FakeApiService(BootloaderOtaQuirksResponse())
        seed = FakeBundledAssetReader(null, json)
        repository =
            BootloaderOtaQuirksRepositoryImpl(
                remoteDataSource = BootloaderOtaQuirksRemoteDataSource(api, dispatchers),
                localDataSource = local,
                assetReader = seed,
                json = json,
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun getSnapshotSeedsFromBundledJsonWhenCacheIsEmpty() = runBlocking {
        seed.seed =
            BootloaderOtaQuirksResponse(
                devices = listOf(quirk(hwModel = 9)),
                softDeviceVariants = listOf(variant(hwModel = 9, target = "rak4631", softDevice = "6.1.1")),
            )

        val snapshot = repository.getSnapshot()

        assertEquals(listOf(9), snapshot.devices.map { it.hwModel })
        assertEquals(listOf(9), snapshot.softDeviceVariants.map { it.hwModel })
    }

    @Test
    fun getSnapshotSeedsOnlyWhenCacheIsEmpty() = runBlocking {
        seed.seed = BootloaderOtaQuirksResponse(devices = listOf(quirk(hwModel = 9)))
        repository.getSnapshot()
        assertEquals(1, local.count())

        // A changed bundled asset must NOT re-seed once the cache is populated.
        seed.seed = BootloaderOtaQuirksResponse(devices = listOf(quirk(hwModel = 9), quirk(hwModel = 18)))
        val snapshot = repository.getSnapshot()

        assertEquals(1, local.count())
        assertEquals(listOf(9), snapshot.devices.map { it.hwModel })
    }

    @Test
    fun getSnapshotIsEmptyWhenNoSeedAndNoCache() = runBlocking {
        val snapshot = repository.getSnapshot()

        assertEquals(BootloaderOtaQuirksResponse(), snapshot)
    }

    @Test
    fun reconcileUpdatesCacheFromTheNetwork() = runBlocking {
        api.response =
            BootloaderOtaQuirksResponse(softDeviceVariants = listOf(variant(hwModel = 9, target = "rak4631", "7.3.0")))
        repository.reconcile()

        val snapshot = repository.getSnapshot()

        assertEquals("7.3.0", snapshot.softDeviceVariants.single().softDevice)
    }

    @Test
    fun emptyNetworkResponseLeavesCacheUntouched() = runBlocking {
        api.response = BootloaderOtaQuirksResponse(devices = listOf(quirk(hwModel = 9)))
        repository.reconcile()
        assertEquals(1, local.count())

        api.response = BootloaderOtaQuirksResponse()
        repository.reconcile()

        assertEquals(1, local.count())
        assertEquals(listOf(9), repository.getSnapshot().devices.map { it.hwModel })
    }
}
