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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Source
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceLink
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.DeviceHardwareRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.repository.DeviceLinkRepository
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceHardwareRepositoryImplTest {
    private class FakeApiService(var response: List<NetworkDeviceHardware>) : ApiService {
        var hardwareCalls = 0

        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> {
            hardwareCalls += 1
            return response
        }

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = error("unused")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = error("unused")

        override suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest = error("unused")

        override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? = error("unused")

        override suspend fun getEventFirmware(): EventFirmwareResponse = error("unused")
    }

    private class FakeBundledAssetReader(var hardware: List<NetworkDeviceHardware>, private val json: Json) :
        BundledAssetReader {
        override fun open(name: String): Source? {
            if (name != "device_hardware.json") return null
            return Buffer().write(json.encodeToString(hardware).encodeToByteArray())
        }
    }

    private class FakeDeviceLinkRepository : DeviceLinkRepository {
        var reconcileCalls = 0

        override suspend fun ensureImported() = Unit

        override suspend fun reconcile() {
            reconcileCalls += 1
        }

        override suspend fun getLinksForTarget(platformioTarget: String, regionCode: String): List<DeviceLink> =
            emptyList()

        override fun observeAllLinks(): Flow<List<DeviceLink>> = flowOf(emptyList())
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val dispatchers =
        CoroutineDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined, Dispatchers.Unconfined)
    private val knownHardware =
        NetworkDeviceHardware(
            hwModel = 1,
            hwModelSlug = "KNOWN",
            platformioTarget = "known",
            architecture = "esp32",
            activelySupported = true,
            displayName = "Known",
            images = listOf("known.svg"),
        )

    private val remoteOnlyHardware =
        NetworkDeviceHardware(
            hwModel = 2,
            hwModelSlug = "REMOTE_ONLY",
            platformioTarget = "remote-only",
            architecture = "esp32",
            activelySupported = true,
            displayName = "Remote Only",
            images = listOf("remote-only.svg"),
        )

    private lateinit var databaseProvider: FakeDatabaseProvider
    private lateinit var api: FakeApiService
    private lateinit var links: FakeDeviceLinkRepository
    private lateinit var repository: DeviceHardwareRepositoryImpl

    @BeforeTest
    fun setup() {
        databaseProvider = FakeDatabaseProvider()
        api = FakeApiService(listOf(knownHardware))
        links = FakeDeviceLinkRepository()
        repository =
            DeviceHardwareRepositoryImpl(
                remoteDataSource = DeviceHardwareRemoteDataSource(api, dispatchers),
                localDataSource = DeviceHardwareLocalDataSource(databaseProvider),
                assetReader = FakeBundledAssetReader(listOf(knownHardware), json),
                json = json,
                deviceLinkRepository = links,
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = databaseProvider.close()

    @Test
    fun repeatedMissingModelUsesOneSuccessfulCatalogRefresh() = runBlocking {
        assertNull(repository.getDeviceHardwareByModel(hwModel = 37).getOrThrow())
        assertNull(repository.getDeviceHardwareByModel(hwModel = 37).getOrThrow())

        assertEquals(1, api.hardwareCalls)
        assertEquals(1, links.reconcileCalls)
    }

    @Test
    fun forceRefreshBypassesRecentSuccessfulCatalogRefresh() = runBlocking {
        repository.getDeviceHardwareByModel(hwModel = 37).getOrThrow()
        repository.getDeviceHardwareByModel(hwModel = 37, forceRefresh = true).getOrThrow()

        assertEquals(2, api.hardwareCalls)
        assertEquals(2, links.reconcileCalls)
    }

    @Test
    fun emptyForcedRefreshPreservesPreviouslyCachedRemoteCatalog() = runBlocking {
        api.response = listOf(remoteOnlyHardware)
        val initial = repository.getDeviceHardwareByModel(hwModel = remoteOnlyHardware.hwModel).getOrThrow()
        assertEquals(remoteOnlyHardware.displayName, initial?.displayName)
        assertNull(repository.getDeviceHardwareByModel(hwModel = knownHardware.hwModel).getOrThrow())

        api.response = emptyList()
        val afterEmptyRefresh =
            repository.getDeviceHardwareByModel(hwModel = remoteOnlyHardware.hwModel, forceRefresh = true).getOrThrow()

        assertEquals(remoteOnlyHardware.displayName, afterEmptyRefresh?.displayName)
        assertEquals(2, api.hardwareCalls)
        assertEquals(2, links.reconcileCalls)
    }
}

class DeviceHardwareRefreshGateTest {
    private val gate = DeviceHardwareRefreshGate(retryIntervalMs = 100, successTtlMs = 1_000)

    @Test
    fun freshCacheNeedsNoRefresh() {
        assertFalse(gate.shouldRefresh(nowMs = 0, forceRefresh = false, cacheNeedsRefresh = false))
    }

    @Test
    fun failedAttemptIsThrottledUntilRetryInterval() {
        assertTrue(gate.shouldRefresh(nowMs = 0, forceRefresh = false, cacheNeedsRefresh = true))
        gate.recordAttempt(0)

        assertFalse(gate.shouldRefresh(nowMs = 99, forceRefresh = false, cacheNeedsRefresh = true))
        assertTrue(gate.shouldRefresh(nowMs = 100, forceRefresh = false, cacheNeedsRefresh = true))
    }

    @Test
    fun successfulCatalogIsAuthoritativeForTtl() {
        gate.recordAttempt(0)
        gate.recordSuccess(10)

        assertFalse(gate.shouldRefresh(nowMs = 1_009, forceRefresh = false, cacheNeedsRefresh = true))
        assertTrue(gate.shouldRefresh(nowMs = 1_010, forceRefresh = false, cacheNeedsRefresh = true))
    }

    @Test
    fun forceRefreshAlwaysBypassesGate() {
        gate.recordAttempt(100)
        gate.recordSuccess(100)

        assertTrue(gate.shouldRefresh(nowMs = 101, forceRefresh = true, cacheNeedsRefresh = true))
    }
}
