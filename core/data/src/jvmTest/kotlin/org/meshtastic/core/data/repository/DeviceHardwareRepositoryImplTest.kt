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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.DeviceLink
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.SoftDeviceVariant
import org.meshtastic.core.network.DeviceHardwareRemoteDataSource
import org.meshtastic.core.repository.BootloaderOtaQuirksRepository
import org.meshtastic.core.repository.DeviceLinkRepository
import org.meshtastic.core.testing.FakeDatabaseProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DeviceHardwareRepositoryImplTest {
    private class FakeDeviceLinkRepository : DeviceLinkRepository {
        var reconcileCalls = 0
        var reconcileFailure: Throwable? = null

        override suspend fun ensureImported() = Unit

        override suspend fun reconcile() {
            reconcileCalls += 1
            reconcileFailure?.let { throw it }
        }

        override suspend fun getLinksForTarget(platformioTarget: String, regionCode: String): List<DeviceLink> =
            emptyList()

        override fun observeAllLinks(): Flow<List<DeviceLink>> = flowOf(emptyList())
    }

    /**
     * Mirrors the pre-migration `loadQuirksAsset()` behavior exactly: reads straight from the same bundled-asset fake
     * the hardware-catalog seed uses, fails open to an empty response on an absent or malformed asset. Real caching and
     * network-refresh behavior is covered separately in BootloaderOtaQuirksRepositoryImplTest; this fake exists so
     * these tests can drive SoftDevice/quirk resolution through the bundled quirks asset alone.
     */
    private class FakeBootloaderOtaQuirksRepository(
        private val assetReader: BundledAssetReader,
        private val json: Json,
    ) : BootloaderOtaQuirksRepository {
        var reconcileCalls = 0

        override suspend fun getSnapshot(): BootloaderOtaQuirksResponse =
            safeCatching { assetReader.decode<BootloaderOtaQuirksResponse>("device_bootloader_ota_quirks.json", json) }
                .getOrNull() ?: BootloaderOtaQuirksResponse()

        override suspend fun reconcile() {
            reconcileCalls += 1
        }
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
    private lateinit var assetReader: FakeBundledAssetReader
    private lateinit var repository: DeviceHardwareRepositoryImpl

    /** An asset reader serving [hardware] as the bundled catalog and, when given, [quirks] as the raw quirks asset. */
    private fun bundledAssets(hardware: List<NetworkDeviceHardware>, quirks: String? = null) =
        FakeBundledAssetReader().apply {
            put("device_hardware.json", hardware, json)
            quirks?.let { put("device_bootloader_ota_quirks.json", it) }
        }

    @BeforeTest
    fun setup() {
        databaseProvider = FakeDatabaseProvider()
        api = FakeApiService(deviceHardware = { listOf(knownHardware) })
        links = FakeDeviceLinkRepository()
        assetReader = bundledAssets(listOf(knownHardware))
        repository =
            DeviceHardwareRepositoryImpl(
                remoteDataSource = DeviceHardwareRemoteDataSource(api, dispatchers),
                localDataSource = DeviceHardwareLocalDataSource(databaseProvider),
                assetReader = assetReader,
                json = json,
                deviceLinkRepository = links,
                bootloaderOtaQuirksRepository = FakeBootloaderOtaQuirksRepository(assetReader, json),
                dispatchers = dispatchers,
            )
    }

    @AfterTest fun tearDown() = databaseProvider.close()

    @Test
    fun repeatedMissingModelUsesOneSuccessfulCatalogRefresh() = runBlocking {
        // The second lookup runs to completion while the first fetch is held open, so the success TTL cannot be
        // what stops it from fetching again.
        val fetchStarted = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        api.deviceHardware = {
            fetchStarted.complete(Unit)
            gate.await()
            listOf(knownHardware)
        }
        coroutineScope {
            val first = async { repository.getDeviceHardwareByModel(hwModel = 37) }
            withTimeout(5.seconds) { fetchStarted.await() }
            repository.getDeviceHardwareByModel(hwModel = 37)
            gate.complete(Unit)
            first.await()
        }

        assertEquals(1, api.deviceHardwareCalls, "concurrent callers must share one fetch")
        assertEquals(1, links.reconcileCalls, "concurrent callers must share one reconcile")
    }

    @Test
    fun reconciliationFailureDoesNotTriggerAnotherCatalogRefresh() = runBlocking {
        // The refresher records catalog success BEFORE calling reconcile(), so a reconcile failure must not
        // poison the success TTL window. A subsequent missing-model lookup must NOT cause another hardware
        // fetch, because the catalog was already recorded as fresh.
        links.reconcileFailure = RuntimeException("reconcile boom")
        assertNull(repository.getDeviceHardwareByModel(hwModel = 37).getOrThrow())

        links.reconcileFailure = null
        assertNull(repository.getDeviceHardwareByModel(hwModel = 37).getOrThrow())

        assertEquals(1, api.deviceHardwareCalls, "catalog success must gate further hardware fetches")
        assertEquals(1, links.reconcileCalls, "retry outside TTL must not retry reconcile either")
    }

    @Test
    fun forceRefreshBypassesRecentSuccessfulCatalogRefresh() = runBlocking {
        repository.getDeviceHardwareByModel(hwModel = 37).getOrThrow()
        repository.getDeviceHardwareByModel(hwModel = 37, forceRefresh = true).getOrThrow()

        assertEquals(2, api.deviceHardwareCalls)
        assertEquals(2, links.reconcileCalls)
    }

    @Test
    fun emptyForcedRefreshPreservesPreviouslyCachedRemoteCatalog() = runBlocking {
        api.deviceHardware = { listOf(remoteOnlyHardware) }
        val initial = repository.getDeviceHardwareByModel(hwModel = remoteOnlyHardware.hwModel).getOrThrow()
        assertEquals(remoteOnlyHardware.displayName, initial?.displayName)
        assertNull(repository.getDeviceHardwareByModel(hwModel = knownHardware.hwModel).getOrThrow())

        api.deviceHardware = { emptyList() }
        val afterEmptyRefresh =
            repository.getDeviceHardwareByModel(hwModel = remoteOnlyHardware.hwModel, forceRefresh = true).getOrThrow()

        assertEquals(remoteOnlyHardware.displayName, afterEmptyRefresh?.displayName)
        assertEquals(2, api.deviceHardwareCalls)
        assertEquals(2, links.reconcileCalls)
    }

    // ── SoftDevice variant resolution: fail-closed on every unresolvable case ──────────────────────

    private val nrfHardware =
        NetworkDeviceHardware(
            hwModel = 9,
            hwModelSlug = "RAK4631",
            platformioTarget = "rak4631",
            architecture = "nrf52840",
            activelySupported = true,
            displayName = "RAK4631",
            images = listOf("rak4631.svg"),
        )

    private fun quirksAsset(hwModel: Int = 9, targets: String = "[\"rak4631\"]", softDevice: String? = "6.1.1") =
        """
        {
          "devices": [],
          "softDeviceVariants": [
            { "hwModel": $hwModel, "hwModelSlug": "RAK4631", "platformioTargets": $targets
              ${softDevice?.let { ", \"softDevice\": \"$it\"" } ?: ""} }
          ]
        }
        """
            .trimIndent()

    /** Rebuilds the repository around an nRF fixture so SoftDevice resolution can be exercised. */
    private fun nrfRepository(quirks: String?): DeviceHardwareRepositoryImpl {
        api = FakeApiService(deviceHardware = { listOf(nrfHardware) })
        assetReader = bundledAssets(listOf(nrfHardware), quirks)
        return DeviceHardwareRepositoryImpl(
            remoteDataSource = DeviceHardwareRemoteDataSource(api, dispatchers),
            localDataSource = DeviceHardwareLocalDataSource(databaseProvider),
            assetReader = assetReader,
            json = json,
            deviceLinkRepository = links,
            bootloaderOtaQuirksRepository = FakeBootloaderOtaQuirksRepository(assetReader, json),
            dispatchers = dispatchers,
        )
    }

    @Test
    fun softDeviceResolvesWhenModelAndTargetMatch() = runBlocking {
        val repo = nrfRepository(quirksAsset())

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "rak4631").getOrNull()

        assertEquals(SoftDeviceVariant.S140_6_1_1, hardware?.softDeviceVariant)
    }

    @Test
    fun softDeviceIsNullWhenTheAssetIsAbsent() = runBlocking {
        val repo = nrfRepository(quirks = null)

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "rak4631").getOrNull()

        assertNotNull(hardware, "hardware lookup must still succeed — only the variant is unavailable")
        assertEquals(null, hardware.softDeviceVariant, "an absent asset must not resolve a variant")
    }

    @Test
    fun softDeviceIsNullWhenTheAssetIsMalformed() = runBlocking {
        val repo = nrfRepository(quirks = "{ not json at all")

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "rak4631").getOrNull()

        assertNotNull(hardware, "a malformed asset must not fail the hardware lookup")
        assertEquals(null, hardware.softDeviceVariant)
    }

    @Test
    fun softDeviceIsNullWhenTheModelIsUnmapped() = runBlocking {
        val repo = nrfRepository(quirksAsset(hwModel = 999))

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "rak4631").getOrNull()

        assertEquals(null, hardware?.softDeviceVariant, "an unmapped model must not borrow another row")
    }

    @Test
    fun softDeviceIsNullWhenTheReportedTargetIsNotInTheRow() = runBlocking {
        // The dangerous case: hwModel matches but the device reports a build we have not verified. Borrowing the row's
        // variant here is exactly what would write an erase image into a SoftDevice.
        val repo = nrfRepository(quirksAsset(targets = "[\"rak4631_some_other_build\"]"))

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "rak4631").getOrNull()

        assertEquals(null, hardware?.softDeviceVariant, "a target mismatch must refuse, not fall back")
    }

    /** Two rows for the same hwModel with different SoftDevices — the shape hwModel 94 actually has. */
    private fun ambiguousQuirksAsset() =
        """
        {
          "devices": [],
          "softDeviceVariants": [
            { "hwModel": 9, "hwModelSlug": "RAK4631", "platformioTargets": ["rak4631"], "softDevice": "6.1.1" },
            { "hwModel": 9, "hwModelSlug": "RAK4631", "platformioTargets": ["rak4631_v2"], "softDevice": "7.3.0" }
          ]
        }
        """
            .trimIndent()

    @Test
    fun softDeviceResolvesWithoutAReportedTargetWhenTheModelIsUnambiguous() = runBlocking {
        val repo = nrfRepository(quirksAsset())

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = null).getOrNull()

        assertEquals(SoftDeviceVariant.S140_6_1_1, hardware?.softDeviceVariant)
    }

    @Test
    fun softDeviceIsNullWithoutAReportedTargetWhenTheModelHasMultipleVariants() = runBlocking {
        // Without a device-reported build target, platformioTarget may be disambiguate()'s arbitrary pick — it
        // must not choose between rows whose SoftDevices differ.
        val repo = nrfRepository(ambiguousQuirksAsset())

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = null).getOrNull()

        assertEquals(null, hardware?.softDeviceVariant, "an ambiguous model must not resolve from a guessed target")
    }

    @Test
    fun softDeviceTreatsABlankReportedTargetAsAbsent() = runBlocking {
        val repo = nrfRepository(ambiguousQuirksAsset())

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "").getOrNull()

        assertEquals(null, hardware?.softDeviceVariant)
    }

    @Test
    fun softDeviceIsNullWhenTheValueIsUnrecognised() = runBlocking {
        val repo = nrfRepository(quirksAsset(softDevice = "6.1.2"))

        val hardware = repo.getDeviceHardwareByModel(hwModel = 9, target = "rak4631").getOrNull()

        assertEquals(null, hardware?.softDeviceVariant, "an unknown SoftDevice string must not map to an image")
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
