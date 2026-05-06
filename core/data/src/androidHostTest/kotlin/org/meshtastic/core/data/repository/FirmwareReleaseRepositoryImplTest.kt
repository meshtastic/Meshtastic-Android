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

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSource
import org.meshtastic.core.data.datasource.FirmwareReleaseLocalDataSource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NetworkFirmwareRelease
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.Releases
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.FirmwareReleaseRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.setupTestContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareReleaseRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var apiService: ApiService
    private lateinit var jsonDataSource: FirmwareReleaseJsonDataSource
    private lateinit var repository: FirmwareReleaseRepositoryImpl

    private var remoteCallCount = 0
    private var jsonCallCount = 0

    @BeforeTest
    fun setUp() {
        setupTestContext()
        dbProvider = FakeDatabaseProvider()
        apiService = mock(MockMode.autofill)
        jsonDataSource = mock(MockMode.autofill)

        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            NetworkFirmwareReleases()
        }
        every { jsonDataSource.loadFirmwareReleaseFromJsonAsset() } calls {
            jsonCallCount += 1
            NetworkFirmwareReleases()
        }

        repository = FirmwareReleaseRepositoryImpl(
            remoteDataSource = FirmwareReleaseRemoteDataSource(apiService, dispatchers),
            localDataSource = FirmwareReleaseLocalDataSource(dbProvider, dispatchers),
            jsonDataSource = jsonDataSource,
        )
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `empty cache emits null then latest stable from remote`() = runTest(testDispatcher) {
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            releases(
                stable = listOf(release("v2.9.0.abc"), release("v2.10.0.abc")),
                alpha = listOf(release("v2.11.0.alpha.1")),
            )
        }

        val emissions = repository.stableRelease.toList()

        assertReleaseIds(emissions, null, "v2.10.0.abc")
        assertReleaseTypes(emissions, null, FirmwareReleaseType.STABLE)
        assertEquals(1, remoteCallCount)
        assertEquals(0, jsonCallCount)
    }

    @Test
    fun `fresh stable cache emits once and skips remote refresh`() = runTest(testDispatcher) {
        cacheRelease(release("v2.8.0.abc"), FirmwareReleaseType.STABLE)

        val emissions = repository.stableRelease.toList()

        assertReleaseIds(emissions, "v2.8.0.abc")
        assertReleaseTypes(emissions, FirmwareReleaseType.STABLE)
        assertEquals(0, remoteCallCount)
        assertEquals(0, jsonCallCount)
    }

    @Test
    fun `stale stable cache emits stale value then refreshed remote value`() = runTest(testDispatcher) {
        cacheRelease(
            release("v2.7.0.abc"),
            FirmwareReleaseType.STABLE,
            lastUpdated = nowMillis - TimeConstants.ONE_HOUR.inWholeMilliseconds - 1,
        )
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            releases(stable = listOf(release("v2.10.1.abc")))
        }

        val emissions = repository.stableRelease.toList()

        assertReleaseIds(emissions, "v2.7.0.abc", "v2.10.1.abc")
        assertReleaseTypes(emissions, FirmwareReleaseType.STABLE, FirmwareReleaseType.STABLE)
    }

    @Test
    fun `stale cache falls back to bundled json when remote fetch fails`() = runTest(testDispatcher) {
        cacheRelease(
            release("v2.6.0.abc"),
            FirmwareReleaseType.STABLE,
            lastUpdated = nowMillis - TimeConstants.ONE_HOUR.inWholeMilliseconds - 1,
        )
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            throw IllegalStateException("network down")
        }
        every { jsonDataSource.loadFirmwareReleaseFromJsonAsset() } calls {
            jsonCallCount += 1
            releases(stable = listOf(release("v2.11.0.abc")))
        }

        val emissions = repository.stableRelease.toList()

        assertReleaseIds(emissions, "v2.6.0.abc", "v2.11.0.abc")
        assertReleaseTypes(emissions, FirmwareReleaseType.STABLE, FirmwareReleaseType.STABLE)
        assertEquals(1, remoteCallCount)
        assertEquals(1, jsonCallCount)
    }

    @Test
    fun `stale cache is re-emitted when both remote and json refreshes fail`() = runTest(testDispatcher) {
        cacheRelease(
            release("v2.5.0.abc"),
            FirmwareReleaseType.STABLE,
            lastUpdated = nowMillis - TimeConstants.ONE_HOUR.inWholeMilliseconds - 1,
        )
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            throw IllegalStateException("network down")
        }
        every { jsonDataSource.loadFirmwareReleaseFromJsonAsset() } calls {
            jsonCallCount += 1
            throw IllegalArgumentException("missing asset")
        }

        val emissions = repository.stableRelease.toList()

        assertReleaseIds(emissions, "v2.5.0.abc", "v2.5.0.abc")
        assertReleaseTypes(emissions, FirmwareReleaseType.STABLE, FirmwareReleaseType.STABLE)
    }

    @Test
    fun `alpha release emits the newest alpha version only`() = runTest(testDispatcher) {
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            releases(
                stable = listOf(release("v2.9.0.abc")),
                alpha = listOf(release("v2.11.0.alpha.1"), release("v2.12.0.alpha.1")),
            )
        }

        val emissions = repository.alphaRelease.toList()

        assertReleaseIds(emissions, null, "v2.12.0.alpha.1")
        assertReleaseTypes(emissions, null, FirmwareReleaseType.ALPHA)
    }

    @Test
    fun `stable collection warms alpha cache for subsequent alpha collectors`() = runTest(testDispatcher) {
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            releases(
                stable = listOf(release("v2.9.9.abc")),
                alpha = listOf(release("v2.12.0.alpha.2")),
            )
        }

        val stableEmissions = repository.stableRelease.toList()
        val alphaEmissions = repository.alphaRelease.toList()

        assertReleaseIds(stableEmissions, null, "v2.9.9.abc")
        assertReleaseTypes(stableEmissions, null, FirmwareReleaseType.STABLE)
        assertReleaseIds(alphaEmissions, "v2.12.0.alpha.2")
        assertReleaseTypes(alphaEmissions, FirmwareReleaseType.ALPHA)
        assertEquals(1, remoteCallCount)
    }

    @Test
    fun `invalidateCache clears database and next collection refetches`() = runTest(testDispatcher) {
        cacheRelease(release("v2.8.0.abc"), FirmwareReleaseType.STABLE)
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            releases(stable = listOf(release("v2.10.2.abc")))
        }

        repository.invalidateCache()
        val emissions = repository.stableRelease.toList()

        assertTrue(dbProvider.currentDb.value.firmwareReleaseDao().getAllReleases().isNotEmpty())
        assertReleaseIds(emissions, null, "v2.10.2.abc")
        assertReleaseTypes(emissions, null, FirmwareReleaseType.STABLE)
        assertEquals(1, remoteCallCount)
    }

    @Test
    fun `selects highest semantic version from fresh cached releases`() = runTest(testDispatcher) {
        cacheRelease(release("v2.9.9.abc"), FirmwareReleaseType.STABLE)
        cacheRelease(release("v2.10.0.abc"), FirmwareReleaseType.STABLE)

        val emissions = repository.stableRelease.toList()

        assertReleaseIds(emissions, "v2.10.0.abc")
        assertReleaseTypes(emissions, FirmwareReleaseType.STABLE)
        assertEquals(0, remoteCallCount)
    }

    @Test
    fun `empty remote release list emits null twice`() = runTest(testDispatcher) {
        everySuspend { apiService.getFirmwareReleases() } calls {
            remoteCallCount += 1
            NetworkFirmwareReleases()
        }

        val emissions = repository.stableRelease.toList()

        assertEquals(listOf(null, null), emissions)
        assertEquals(1, remoteCallCount)
    }

    private suspend fun cacheRelease(
        release: NetworkFirmwareRelease,
        type: FirmwareReleaseType,
        lastUpdated: Long = nowMillis,
    ) {
        dbProvider.currentDb.value.firmwareReleaseDao().insert(release.asEntity(type).copy(lastUpdated = lastUpdated))
    }

    private fun release(id: String) = NetworkFirmwareRelease(
        id = id,
        pageUrl = "https://example.invalid/$id",
        releaseNotes = "notes for $id",
        title = id,
        zipUrl = "https://example.invalid/$id.zip",
    )

    private fun releases(
        stable: List<NetworkFirmwareRelease> = emptyList(),
        alpha: List<NetworkFirmwareRelease> = emptyList(),
    ) = NetworkFirmwareReleases(releases = Releases(alpha = alpha, stable = stable))

    private fun assertReleaseIds(emissions: List<FirmwareRelease?>, vararg expected: String?) {
        assertEquals(expected.toList(), emissions.map { it?.id })
    }

    private fun assertReleaseTypes(emissions: List<FirmwareRelease?>, vararg expected: FirmwareReleaseType?) {
        assertEquals(expected.toList(), emissions.map { it?.releaseType })
    }
}
