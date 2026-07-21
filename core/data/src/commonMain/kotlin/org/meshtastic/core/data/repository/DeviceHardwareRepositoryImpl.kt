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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BundledAssetReader
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.data.datasource.decode
import org.meshtastic.core.data.util.SingleFlightRefresher
import org.meshtastic.core.data.util.staleWhileRevalidateFlow
import org.meshtastic.core.database.entity.DeviceHardwareEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.DeviceHardwareRemoteDataSource
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.DeviceLinkRepository

@Single
class DeviceHardwareRepositoryImpl(
    private val remoteDataSource: DeviceHardwareRemoteDataSource,
    private val localDataSource: DeviceHardwareLocalDataSource,
    private val assetReader: BundledAssetReader,
    private val json: Json,
    private val deviceLinkRepository: DeviceLinkRepository,
    private val dispatchers: CoroutineDispatchers,
) : DeviceHardwareRepository {

    /**
     * Shared full-table refresh; a caller that stops waiting (the node-details path bounds its wait) can't abort it.
     */
    private val refresher =
        SingleFlightRefresher(dispatchers.io, "DeviceHardwareRepository") {
            Logger.d { "DeviceHardwareRepository: fetching from remote API" }
            val remoteHardware = remoteDataSource.getAllDeviceHardware()
            Logger.d { "DeviceHardwareRepository: remote returned ${remoteHardware.size} entries" }
            localDataSource.insertAllDeviceHardware(remoteHardware)
            // Refresh msh.to device links from the API after a hardware refresh.
            deviceLinkRepository.reconcile()
        }

    /**
     * Retrieves device hardware information by its model ID and optional target string.
     *
     * Pipeline:
     * 1. If the local DB is empty, seed it from the bundled JSON asset (instant baseline).
     * 2. If the cached entry is stale or missing, refresh from the remote API with a timeout.
     * 3. Return the best available data from the DB (never blocks longer than [NETWORK_REFRESH_TIMEOUT_MS]).
     */
    override suspend fun getDeviceHardwareByModel(
        hwModel: Int,
        target: String?,
        forceRefresh: Boolean,
    ): Result<DeviceHardware?> = withContext(dispatchers.io) {
        Logger.d {
            "DeviceHardwareRepository: getDeviceHardwareByModel(hwModel=$hwModel," +
                " target=$target, forceRefresh=$forceRefresh)"
        }

        if (forceRefresh) {
            Logger.d { "DeviceHardwareRepository: forceRefresh=true, clearing cache" }
            localDataSource.deleteAllDeviceHardware()
        }

        ensureSeeded()

        var entities = lookupEntities(hwModel, target)
        if (forceRefresh || entities.isEmpty() || entities.any { it.isStale() }) {
            refresher.refresh(maxWaitMs = NETWORK_REFRESH_TIMEOUT_MS)
            entities = lookupEntities(hwModel, target)
        }

        Result.success(resolveHardware(hwModel, entities, target))
    }

    /**
     * Non-blocking Flow that emits cached/bundled hardware data immediately, then refreshes from the network if stale.
     * Safe to use inside Flow `combine` transforms.
     */
    override fun observeDeviceHardware(hwModel: Int, target: String?): Flow<DeviceHardware?> = staleWhileRevalidateFlow(
        loadFromCache = {
            ensureSeeded()
            resolveHardware(hwModel, lookupEntities(hwModel, target), target)
        },
        shouldFetch = { cached -> cached == null || lookupEntities(hwModel, target).any { it.isStale() } },
        fetch = { refresher.refresh() },
        context = dispatchers.io,
        networkTimeoutMs = null,
        tag = "DeviceHardwareRepository",
    )

    /** Looks up entities by hwModel, falling back to a target-only lookup when needed. */
    private suspend fun lookupEntities(hwModel: Int, target: String?): List<DeviceHardwareEntity> =
        localDataSource.getByHwModel(hwModel).ifEmpty {
            target?.let { listOfNotNull(localDataSource.getByTarget(it)) } ?: emptyList()
        }

    /** Seeds the local DB from bundled JSON if completely empty. */
    private suspend fun ensureSeeded() {
        if (!localDataSource.hasAnyEntries()) {
            safeCatching {
                Logger.d { "DeviceHardwareRepository: seeding cache from bundled JSON" }
                val jsonHardware =
                    assetReader.decode<List<NetworkDeviceHardware>>("device_hardware.json", json).orEmpty()
                localDataSource.insertAllDeviceHardware(jsonHardware)
            }
                .onFailure { e -> Logger.w(e) { "DeviceHardwareRepository: failed to seed cache from bundled JSON" } }
        }
    }

    /** Resolves entities into a [DeviceHardware] domain model with quirk application. */
    private fun resolveHardware(hwModel: Int, entities: List<DeviceHardwareEntity>, target: String?): DeviceHardware? {
        val matched = disambiguate(entities, target)
        val quirks = loadQuirks()
        return applyBootloaderQuirk(hwModel, matched?.asExternalModel(), quirks, target)
    }

    private fun disambiguate(entities: List<DeviceHardwareEntity>, target: String?): DeviceHardwareEntity? =
        when (target) {
            null -> entities.firstOrNull()

            else ->
                entities.find { it.platformioTarget == target }
                    ?: entities.find { it.platformioTarget.equals(target, ignoreCase = true) }
                    ?: entities.firstOrNull()
        }

    private fun DeviceHardwareEntity.isIncomplete(): Boolean =
        displayName.isBlank() || platformioTarget.isBlank() || images.isNullOrEmpty()

    private fun DeviceHardwareEntity.isStale(): Boolean =
        isIncomplete() || (nowMillis - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    // Quirks are best-effort: swallow any parse/IO error and fall back to "no quirks" rather than failing hardware
    // lookup.
    private fun loadQuirks(): List<BootloaderOtaQuirk> = runCatching {
        assetReader.decode<BootloaderOtaQuirksResponse>("device_bootloader_ota_quirks.json", json)?.devices
    }
        .onFailure { e -> Logger.w(e) { "Failed to load device_bootloader_ota_quirks.json" } }
        .getOrNull()
        .orEmpty()

    private fun applyBootloaderQuirk(
        hwModel: Int,
        base: DeviceHardware?,
        quirks: List<BootloaderOtaQuirk>,
        reportedTarget: String? = null,
    ): DeviceHardware? = base?.let { hw ->
        val matchedQuirk = quirks.firstOrNull { it.hwModel == hwModel }
        val withQuirk =
            if (matchedQuirk != null) {
                hw.copy(
                    requiresBootloaderUpgradeForOta = matchedQuirk.requiresBootloaderUpgradeForOta,
                    bootloaderInfoUrl = matchedQuirk.infoUrl,
                )
            } else {
                hw
            }
        reportedTarget?.let { withQuirk.copy(platformioTarget = it) } ?: withQuirk
    }

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_DAY.inWholeMilliseconds

        /** Maximum time a blocking lookup waits for an in-flight refresh before returning cached/bundled data. */
        private const val NETWORK_REFRESH_TIMEOUT_MS = 5_000L
    }
}
