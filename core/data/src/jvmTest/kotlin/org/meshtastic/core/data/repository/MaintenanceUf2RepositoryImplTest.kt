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
import org.meshtastic.core.data.datasource.MaintenanceUf2LocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.EraseImageEntry
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.MaintenanceUf2EraseSet
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.OtafixAssetEntry
import org.meshtastic.core.network.MaintenanceUf2RemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MaintenanceUf2RepositoryImplTest {

    /** Only [getMaintenanceUf2Manifest] is exercised; the other endpoints are never called by this repository. */
    private class FakeApiService(var response: MaintenanceUf2Manifest) : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = error("unused")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")

        override suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest = error("unused")

        override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? = error("unused")

        override suspend fun getEventFirmware(): EventFirmwareResponse = error("unused")

        override suspend fun getBootloaderOtaQuirks(): BootloaderOtaQuirksResponse = error("unused")

        override suspend fun getMaintenanceUf2Manifest(): MaintenanceUf2Manifest = response
    }

    /** Serves only `maintenance_uf2.json`, or nothing when [seed] is null (models the asset being absent). */
    private class FakeBundledAssetReader(var seed: MaintenanceUf2Manifest?, private val json: Json) :
        BundledAssetReader {
        override fun open(name: String): Source? {
            if (name != "maintenance_uf2.json") return null
            val current = seed ?: return null
            return Buffer().write(json.encodeToString(current).encodeToByteArray())
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Real dispatchers + runBlocking, not runTest — matches BootloaderOtaQuirksRepositoryImplTest's rationale for
    // staying off runTest's virtual clock near Room.
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var local: MaintenanceUf2LocalDataSource
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: MaintenanceUf2RepositoryImpl

    private val pico = EraseImageEntry(fileName = "pico_erase.uf2", sha256 = "0".repeat(64))

    private fun manifestWithBoard(boardId: String, slug: String = "wiscore_rak4631_board") =
        MaintenanceUf2Manifest(otafixByBoardId = mapOf(boardId to OtafixAssetEntry(slug, "1".repeat(64))))

    private fun manifestWithErase(softDevice: String) = MaintenanceUf2Manifest(
        erase =
        MaintenanceUf2EraseSet(
            nrf52 = mapOf(softDevice to EraseImageEntry(fileName = "nrf_erase.uf2", sha256 = "2".repeat(64))),
            rp2040 = pico,
        ),
    )

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        local = MaintenanceUf2LocalDataSource(dbProvider, dispatchers)
        api = FakeApiService(MaintenanceUf2Manifest())
        seed = FakeBundledAssetReader(null, json)
        repository =
            MaintenanceUf2RepositoryImpl(
                remoteDataSource = MaintenanceUf2RemoteDataSource(api, dispatchers),
                localDataSource = local,
                assetReader = seed,
                json = json,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun getSnapshotSeedsFromBundledJsonWhenCacheIsEmpty() = runBlocking {
        seed.seed = manifestWithBoard("HT-n5262")

        val snapshot = repository.getSnapshot()

        assertEquals(listOf("HT-n5262"), snapshot.otafixByBoardId.keys.toList())
    }

    @Test
    fun getSnapshotSeedsOnlyWhenCacheIsEmpty() = runBlocking {
        seed.seed = manifestWithBoard("HT-n5262")
        repository.getSnapshot()
        assertEquals(1, local.count())

        // A changed bundled asset must NOT re-seed once the cache is populated.
        seed.seed =
            manifestWithBoard("HT-n5262").let {
                it.copy(
                    otafixByBoardId =
                    it.otafixByBoardId + ("OTHER-ID" to OtafixAssetEntry("other_board", "3".repeat(64))),
                )
            }
        val snapshot = repository.getSnapshot()

        assertEquals(1, local.count())
        assertEquals(listOf("HT-n5262"), snapshot.otafixByBoardId.keys.toList())
    }

    @Test
    fun getSnapshotIsEmptyWhenNoSeedAndNoCache() = runBlocking {
        val snapshot = repository.getSnapshot()

        assertEquals(MaintenanceUf2Manifest(), snapshot)
    }

    @Test
    fun reconcileUpdatesCacheFromTheNetwork() = runBlocking {
        api.response = manifestWithErase("7.3.0")
        repository.reconcile()

        val snapshot = repository.getSnapshot()

        assertEquals(listOf("7.3.0"), snapshot.erase?.nrf52?.keys?.toList())
    }

    @Test
    fun emptyNetworkResponseLeavesCacheUntouched() = runBlocking {
        api.response = manifestWithBoard("HT-n5262")
        repository.reconcile()
        assertEquals(1, local.count())

        api.response = MaintenanceUf2Manifest()
        repository.reconcile()

        assertEquals(1, local.count())
        assertEquals(listOf("HT-n5262"), repository.getSnapshot().otafixByBoardId.keys.toList())
    }
}
