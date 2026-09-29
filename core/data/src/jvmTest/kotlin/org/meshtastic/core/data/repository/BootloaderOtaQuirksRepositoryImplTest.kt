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
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.SoftDeviceVariantEntry
import org.meshtastic.core.network.BootloaderOtaQuirksRemoteDataSource
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val QUIRKS_ASSET = "device_bootloader_ota_quirks.json"

class BootloaderOtaQuirksRepositoryImplTest {

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
        api = FakeApiService(bootloaderOtaQuirks = { BootloaderOtaQuirksResponse() })
        seed = FakeBundledAssetReader()
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
        seed.put(
            QUIRKS_ASSET,
            BootloaderOtaQuirksResponse(
                devices = listOf(quirk(hwModel = 9)),
                softDeviceVariants = listOf(variant(hwModel = 9, target = "rak4631", softDevice = "6.1.1")),
            ),
            json,
        )

        val snapshot = repository.getSnapshot()

        assertEquals(listOf(9), snapshot.devices.map { it.hwModel })
        assertEquals(listOf(9), snapshot.softDeviceVariants.map { it.hwModel })
    }

    @Test
    fun getSnapshotSeedsOnlyWhenCacheIsEmpty() = runBlocking {
        seed.put(QUIRKS_ASSET, BootloaderOtaQuirksResponse(devices = listOf(quirk(hwModel = 9))), json)
        repository.getSnapshot()
        assertEquals(1, local.count())

        // A changed bundled asset must NOT re-seed once the cache is populated.
        seed.put(
            QUIRKS_ASSET,
            BootloaderOtaQuirksResponse(devices = listOf(quirk(hwModel = 9), quirk(hwModel = 18))),
            json,
        )
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
        api.bootloaderOtaQuirks = {
            BootloaderOtaQuirksResponse(softDeviceVariants = listOf(variant(hwModel = 9, target = "rak4631", "7.3.0")))
        }
        repository.reconcile()

        val snapshot = repository.getSnapshot()

        assertEquals("7.3.0", snapshot.softDeviceVariants.single().softDevice)
    }

    @Test
    fun emptyNetworkResponseLeavesCacheUntouched() = runBlocking {
        api.bootloaderOtaQuirks = { BootloaderOtaQuirksResponse(devices = listOf(quirk(hwModel = 9))) }
        repository.reconcile()
        assertEquals(1, local.count())

        api.bootloaderOtaQuirks = { BootloaderOtaQuirksResponse() }
        repository.reconcile()

        assertEquals(1, local.count())
        assertEquals(listOf(9), repository.getSnapshot().devices.map { it.hwModel })
    }
}
