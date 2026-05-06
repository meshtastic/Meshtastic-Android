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
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.DeviceHardwareRemoteDataSource
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.testing.FakeDatabaseProvider
import org.meshtastic.core.testing.setupTestContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceHardwareRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(main = testDispatcher, io = testDispatcher, default = testDispatcher)

    private lateinit var dbProvider: FakeDatabaseProvider
    private lateinit var apiService: ApiService
    private lateinit var jsonDataSource: DeviceHardwareJsonDataSource
    private lateinit var quirksJsonDataSource: BootloaderOtaQuirksJsonDataSource
    private lateinit var repository: DeviceHardwareRepositoryImpl

    private var remoteCallCount = 0
    private var jsonCallCount = 0

    @BeforeTest
    fun setUp() {
        setupTestContext()
        dbProvider = FakeDatabaseProvider()
        apiService = mock(MockMode.autofill)
        jsonDataSource = mock(MockMode.autofill)
        quirksJsonDataSource = mock(MockMode.autofill)

        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            emptyList()
        }
        every { jsonDataSource.loadDeviceHardwareFromJsonAsset() } calls {
            jsonCallCount += 1
            emptyList()
        }
        every { quirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset() } returns emptyList()

        repository = DeviceHardwareRepositoryImpl(
            remoteDataSource = DeviceHardwareRemoteDataSource(apiService, dispatchers),
            localDataSource = DeviceHardwareLocalDataSource(dbProvider, dispatchers),
            jsonDataSource = jsonDataSource,
            bootloaderOtaQuirksJsonDataSource = quirksJsonDataSource,
            dispatchers = dispatchers,
        )
    }

    @AfterTest
    fun tearDown() {
        dbProvider.close()
    }

    @Test
    fun `returns fresh cached hardware without hitting remote sources`() = runTest(testDispatcher) {
        cacheHardware(hardware(hwModel = 1, target = "t-echo", displayName = "Cached"))

        val result = repository.getDeviceHardwareByModel(hwModel = 1)

        assertEquals("Cached", result.getOrNull()?.displayName)
        assertEquals(0, remoteCallCount)
        assertEquals(0, jsonCallCount)
    }

    @Test
    fun `disambiguates cached variants by target ignoring case and preserves reported target`() = runTest(testDispatcher) {
        cacheHardware(
            hardware(hwModel = 7, target = "t-beam", displayName = "Beam"),
            hardware(hwModel = 7, target = "t-deck", displayName = "Deck"),
        )

        val result = repository.getDeviceHardwareByModel(hwModel = 7, target = "T-DECK")
        val device = result.getOrNull()

        assertNotNull(device)
        assertEquals("Deck", device.displayName)
        assertEquals("T-DECK", device.platformioTarget)
        assertEquals(0, remoteCallCount)
    }

    @Test
    fun `falls back to cached target lookup when model cache is empty`() = runTest(testDispatcher) {
        cacheHardware(hardware(hwModel = 42, target = "target-only", displayName = "Target Match"))

        val result = repository.getDeviceHardwareByModel(hwModel = 999, target = "target-only")
        val device = result.getOrNull()

        assertNotNull(device)
        assertEquals(42, device.hwModel)
        assertEquals("Target Match", device.displayName)
    }

    @Test
    fun `force refresh clears cache and replaces it with remote data`() = runTest(testDispatcher) {
        cacheHardware(hardware(hwModel = 5, target = "old-target", displayName = "Old Cache"))
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            listOf(hardware(hwModel = 5, target = "new-target", displayName = "Remote Fresh"))
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 5, forceRefresh = true)
        val cachedAfterRefresh = dbProvider.currentDb.value.deviceHardwareDao().getByHwModel(5)

        assertEquals("Remote Fresh", result.getOrNull()?.displayName)
        assertEquals(listOf("new-target"), cachedAfterRefresh.map { it.platformioTarget })
        assertEquals(1, remoteCallCount)
    }

    @Test
    fun `stale cache refreshes from remote and returns updated hardware`() = runTest(testDispatcher) {
        cacheHardware(
            hardware(hwModel = 9, target = "stale", displayName = "Stale Cache"),
            lastUpdated = nowMillis - TimeConstants.ONE_DAY.inWholeMilliseconds - 1,
        )
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            listOf(hardware(hwModel = 9, target = "fresh", displayName = "Fresh Remote"))
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 9)

        assertEquals("Fresh Remote", result.getOrNull()?.displayName)
        assertEquals(1, remoteCallCount)
        assertEquals(0, jsonCallCount)
    }

    @Test
    fun `returns stale cache when remote fails and stale data is still complete`() = runTest(testDispatcher) {
        cacheHardware(
            hardware(hwModel = 10, target = "complete", displayName = "Stale Complete"),
            lastUpdated = nowMillis - TimeConstants.ONE_DAY.inWholeMilliseconds - 1,
        )
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            throw IllegalStateException("network down")
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 10)

        assertEquals("Stale Complete", result.getOrNull()?.displayName)
        assertTrue(result.isSuccess)
        assertEquals(1, remoteCallCount)
        assertEquals(0, jsonCallCount)
    }

    @Test
    fun `falls back to bundled json when stale cache is incomplete`() = runTest(testDispatcher) {
        cacheHardware(
            hardware(hwModel = 11, target = "broken", displayName = "", images = emptyList()),
            lastUpdated = nowMillis - TimeConstants.ONE_DAY.inWholeMilliseconds - 1,
        )
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            throw IllegalStateException("network down")
        }
        every { jsonDataSource.loadDeviceHardwareFromJsonAsset() } calls {
            jsonCallCount += 1
            listOf(hardware(hwModel = 11, target = "json-target", displayName = "Bundled Json"))
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 11)

        assertEquals("Bundled Json", result.getOrNull()?.displayName)
        assertEquals(1, remoteCallCount)
        assertEquals(1, jsonCallCount)
    }

    @Test
    fun `applies bootloader quirks to cached hardware`() = runTest(testDispatcher) {
        cacheHardware(hardware(hwModel = 12, target = "quirky", displayName = "Quirky"))
        every { quirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset() } returns listOf(
            BootloaderOtaQuirk(
                hwModel = 12,
                requiresBootloaderUpgradeForOta = true,
                infoUrl = "https://example.invalid/bootloader",
            ),
        )

        val result = repository.getDeviceHardwareByModel(hwModel = 12)
        val device = result.getOrNull()

        assertNotNull(device)
        assertTrue(device.requiresBootloaderUpgradeForOta == true)
        assertEquals("https://example.invalid/bootloader", device.bootloaderInfoUrl)
    }

    @Test
    fun `returns success null when remote data does not contain requested model`() = runTest(testDispatcher) {
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            listOf(hardware(hwModel = 99, target = "other", displayName = "Other"))
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 13)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
        assertEquals(1, remoteCallCount)
        assertEquals(0, jsonCallCount)
    }

    @Test
    fun `uses target lookup after remote fetch when requested model is absent`() = runTest(testDispatcher) {
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            listOf(hardware(hwModel = 77, target = "shared-target", displayName = "Remote Target Match"))
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 14, target = "shared-target")
        val device = result.getOrNull()

        assertNotNull(device)
        assertEquals(77, device.hwModel)
        assertEquals("shared-target", device.platformioTarget)
        assertEquals(1, remoteCallCount)
    }

    @Test
    fun `returns failure when both remote and bundled json sources fail`() = runTest(testDispatcher) {
        everySuspend { apiService.getDeviceHardware() } calls {
            remoteCallCount += 1
            throw IllegalStateException("network down")
        }
        every { jsonDataSource.loadDeviceHardwareFromJsonAsset() } calls {
            jsonCallCount += 1
            throw IllegalArgumentException("missing asset")
        }

        val result = repository.getDeviceHardwareByModel(hwModel = 15)

        assertTrue(result.isFailure)
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertEquals(1, remoteCallCount)
        assertEquals(1, jsonCallCount)
    }

    private suspend fun cacheHardware(vararg hardware: NetworkDeviceHardware, lastUpdated: Long = nowMillis) {
        dbProvider.currentDb.value.deviceHardwareDao().insertAll(hardware.map { it.asEntity().copy(lastUpdated = lastUpdated) })
    }

    private fun hardware(
        hwModel: Int,
        target: String,
        displayName: String,
        images: List<String>? = listOf("$target.png"),
    ) = NetworkDeviceHardware(
        activelySupported = true,
        architecture = "esp32s3",
        displayName = displayName,
        hwModel = hwModel,
        hwModelSlug = "hw-$hwModel",
        images = images,
        platformioTarget = target,
        requiresDfu = false,
        supportLevel = 3,
        tags = listOf("portable"),
    )
}
