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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.Source
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.FirmwareReleaseLocalDataSource
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareRelease
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.Releases
import org.meshtastic.core.network.FirmwareReleaseRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirmwareReleaseRepositoryImplTest {

    /** Only [getFirmwareReleases] is exercised; the other endpoints are never called by this repository. */
    private class FakeApiService(var response: NetworkFirmwareReleases) : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = error("unused")

        override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = error("unused")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = response

        override suspend fun getEventFirmware(): EventFirmwareResponse = error("unused")
    }

    /** Serves `firmware_releases.json` from [bundled] via the real decode path, or nothing when null. */
    private class FakeBundledAssetReader(var bundled: Releases? = null) : BundledAssetReader {
        var failuresBeforeSuccess = 0

        override fun open(name: String): Source? {
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess -= 1
                error("Bundled asset read failed")
            }
            val releases = bundled ?: return null
            if (name != "firmware_releases.json") return null
            val bytes = Json.encodeToString(NetworkFirmwareReleases(releases = releases)).encodeToByteArray()
            return Buffer().write(bytes)
        }
    }

    // Real dispatchers + runBlocking, NOT runTest — see DeviceLinkRepositoryImplTest for why (Room + virtual time).
    private val unconfined = Dispatchers.Unconfined
    private val dispatchers = CoroutineDispatchers(main = unconfined, io = unconfined, default = unconfined)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var api: FakeApiService
    private lateinit var seed: FakeBundledAssetReader
    private lateinit var repository: FirmwareReleaseRepositoryImpl

    private val dao
        get() = dbProvider.currentDb.value.firmwareReleaseDao()

    private fun release(id: String) = NetworkFirmwareRelease(id = id, title = id, zipUrl = "https://example.com/$id")

    /** A cached row old enough that the repository always refreshes. */
    private fun staleRow(id: String, type: FirmwareReleaseType) =
        FirmwareReleaseEntity(id = id, title = id, releaseType = type, lastUpdated = 0)

    @BeforeTest
    fun setup() {
        dbProvider = FakeDatabaseProvider()
        api = FakeApiService(NetworkFirmwareReleases())
        seed = FakeBundledAssetReader()
        repository =
            FirmwareReleaseRepositoryImpl(
                remoteDataSource = FirmwareReleaseRemoteDataSource(api, dispatchers),
                localDataSource = FirmwareReleaseLocalDataSource(dbProvider),
                assetReader = seed,
                json = Json { ignoreUnknownKeys = true },
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = dbProvider.close()

    @Test
    fun refreshPrunesRemovedAndReclassifiedReleases() = runBlocking {
        // Old snapshot: 2.7.26 was published as alpha, 9.9.9 was pulled entirely upstream.
        dao.insert(staleRow("v2.7.15.567b8ea", FirmwareReleaseType.STABLE))
        dao.insert(staleRow("v2.7.26.54e0d8d", FirmwareReleaseType.ALPHA))
        dao.insert(staleRow("v9.9.9.deadbee", FirmwareReleaseType.ALPHA))
        // Upstream promoted 2.7.26 to stable and dropped 9.9.9.
        api.response =
            NetworkFirmwareReleases(
                releases =
                Releases(stable = listOf(release("v2.7.26.54e0d8d")), alpha = listOf(release("v2.7.25.104df5f"))),
            )

        val stableEmissions = repository.stableRelease.toList()

        assertEquals("v2.7.15.567b8ea", stableEmissions.first()?.id, "cached value is emitted before the refresh")
        assertEquals("v2.7.26.54e0d8d", stableEmissions.last()?.id, "refreshed value is emitted after the fetch")
        assertEquals(
            listOf("v2.7.25.104df5f"),
            dao.getReleasesByType(FirmwareReleaseType.ALPHA).map { it.id },
            "pulled and reclassified releases are pruned from the alpha rows",
        )
    }

    @Test
    fun refreshLeavesLocalRowsUntouched() = runBlocking {
        dao.insert(staleRow("v2.7.15.567b8ea", FirmwareReleaseType.STABLE))
        dao.insert(staleRow("local-import", FirmwareReleaseType.LOCAL))
        api.response = NetworkFirmwareReleases(releases = Releases(stable = listOf(release("v2.7.26.54e0d8d"))))

        repository.stableRelease.toList()

        assertEquals(listOf("local-import"), dao.getReleasesByType(FirmwareReleaseType.LOCAL).map { it.id })
    }

    @Test
    fun emptyResponseLeavesCacheUntouched() = runBlocking {
        dao.insert(staleRow("v2.7.15.567b8ea", FirmwareReleaseType.STABLE))
        api.response = NetworkFirmwareReleases()

        val emissions = repository.stableRelease.toList()

        assertEquals("v2.7.15.567b8ea", emissions.last()?.id)
        assertTrue(dao.getReleasesByType(FirmwareReleaseType.STABLE).isNotEmpty())
    }

    @Test
    fun emptyCacheSeedsFromBundledSnapshot() = runBlocking {
        seed.bundled = Releases(stable = listOf(release("v2.7.15.567b8ea")), alpha = listOf(release("v2.7.25.104df5f")))
        api.response = NetworkFirmwareReleases()

        val emissions = repository.stableRelease.toList()

        assertEquals("v2.7.15.567b8ea", emissions.first()?.id)
        assertEquals(listOf("v2.7.25.104df5f"), dao.getReleasesByType(FirmwareReleaseType.ALPHA).map { it.id })
    }

    @Test
    fun failedBundledDecodeIsNotRetried() = runBlocking {
        // A broken bundled asset is a permanent failure — retrying on every collection just burns I/O.
        seed.failuresBeforeSuccess = 1
        seed.bundled = Releases(stable = listOf(release("v2.7.15.567b8ea")))
        api.response = NetworkFirmwareReleases()

        val initialEmissions = repository.stableRelease.toList()
        // Subsequent collection must NOT retry the decode (the second call would succeed if it did).
        val emissions = repository.stableRelease.toList()

        assertEquals(null, initialEmissions.first())
        assertEquals(null, emissions.first(), "failed bundled decode is permanent; no retry on subsequent collections")
    }

    @Test
    fun reSeedsWhenActiveDatabaseSwitches() = runBlocking {
        // Reproduces the original bug: the active Room DB switches per selected device. A process-wide
        // seed gate set during the first collection (against the default DB) skipped seeding the newly
        // activated device DB, leaving the firmware picker empty.
        seed.bundled = Releases(stable = listOf(release("v2.7.15.567b8ea")))
        api.response = NetworkFirmwareReleases()

        val firstEmissions = repository.stableRelease.toList()
        assertEquals("v2.7.15.567b8ea", firstEmissions.first()?.id, "first collection seeds DB-A")

        // The selected device's DB becomes active — it has no firmware_release rows.
        dbProvider.switchToNewDatabase()

        val secondEmissions = repository.stableRelease.toList()
        assertEquals(
            "v2.7.15.567b8ea",
            secondEmissions.first()?.id,
            "DB switch re-evaluates the bundled snapshot against the now-empty active DB and re-seeds it",
        )
    }

    @Test
    fun newerBundledSnapshotReplacesOlderCache() = runBlocking {
        // App update ships a fresher bundle than what a network-starved user has cached.
        dao.insert(FirmwareReleaseEntity(id = "v2.7.15.567b8ea", releaseType = FirmwareReleaseType.STABLE))
        seed.bundled = Releases(stable = listOf(release("v2.7.26.54e0d8d")))

        val emissions = repository.stableRelease.toList()

        assertEquals("v2.7.26.54e0d8d", emissions.first()?.id, "bundle applies before the first emission")
    }

    @Test
    fun partialBundleLeavesTypesItDoesNotShipUntouched() = runBlocking {
        // Cache has data for both types; the bundle ships only a newer stable list.
        dao.insert(FirmwareReleaseEntity(id = "v2.7.15.567b8ea", releaseType = FirmwareReleaseType.STABLE))
        dao.insert(FirmwareReleaseEntity(id = "v2.7.25.104df5f", releaseType = FirmwareReleaseType.ALPHA))
        seed.bundled = Releases(stable = listOf(release("v2.7.26.54e0d8d")))

        val emissions = repository.stableRelease.toList()

        assertEquals("v2.7.26.54e0d8d", emissions.first()?.id, "newer bundled stable applies")
        assertEquals(
            listOf("v2.7.25.104df5f"),
            dao.getReleasesByType(FirmwareReleaseType.ALPHA).map { it.id },
            "alpha cache survives a stable-only bundle",
        )
    }

    @Test
    fun olderBundledSnapshotNeverRegressesCache() = runBlocking {
        // A successful network refresh left the cache newer than the (weekly) bundle.
        dao.insert(FirmwareReleaseEntity(id = "v2.8.0.abc1234", releaseType = FirmwareReleaseType.STABLE))
        seed.bundled = Releases(stable = listOf(release("v2.7.26.54e0d8d")))

        val emissions = repository.stableRelease.toList()

        assertEquals("v2.8.0.abc1234", emissions.first()?.id)
    }
}
