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
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.database.entity.DeviceHardwareEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.DeviceHardwareRemoteDataSource
import org.meshtastic.core.repository.DeviceHardwareRepository

@Single
class DeviceHardwareRepositoryImpl(
    private val remoteDataSource: DeviceHardwareRemoteDataSource,
    private val localDataSource: DeviceHardwareLocalDataSource,
    private val jsonDataSource: DeviceHardwareJsonDataSource,
    private val bootloaderOtaQuirksJsonDataSource: BootloaderOtaQuirksJsonDataSource,
    private val dispatchers: CoroutineDispatchers,
) : DeviceHardwareRepository {

    /**
     * Retrieves device hardware information by its model ID and optional target string.
     *
     * Pipeline:
     * 1. If the local DB is empty, seed it from the bundled JSON asset (instant baseline).
     * 2. If the cached entry is stale or missing, refresh from the remote API.
     * 3. Return the best available data from the DB.
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

        val quirks = loadQuirks()

        if (forceRefresh) {
            Logger.d { "DeviceHardwareRepository: forceRefresh=true, clearing cache" }
            localDataSource.deleteAllDeviceHardware()
        }

        // 1. Seed from bundled JSON if the DB is completely empty (first launch or post-wipe).
        if (!localDataSource.hasAnyEntries()) {
            seedCacheFromBundledJson()
        }

        // 2. Check cache; refresh from network if stale, empty, or forced.
        var entities = lookupEntities(hwModel, target)
        if (forceRefresh || entities.isEmpty() || entities.any { it.isStale() }) {
            refreshFromNetwork()
            entities = lookupEntities(hwModel, target)
        }

        // 3. Resolve and return the best available data.
        val matched = disambiguate(entities, target)
        Result.success(applyBootloaderQuirk(hwModel, matched?.asExternalModel(), quirks, target))
    }

    /** Looks up entities by hwModel, falling back to a target-only lookup when needed. */
    private suspend fun lookupEntities(hwModel: Int, target: String?): List<DeviceHardwareEntity> =
        localDataSource.getByHwModel(hwModel).ifEmpty {
            target?.let { listOfNotNull(localDataSource.getByTarget(it)) } ?: emptyList()
        }

    private suspend fun seedCacheFromBundledJson() {
        safeCatching {
            Logger.d { "DeviceHardwareRepository: seeding cache from bundled JSON" }
            val jsonHardware = jsonDataSource.loadDeviceHardwareFromJsonAsset()
            localDataSource.insertAllDeviceHardware(jsonHardware)
        }
            .onFailure { e -> Logger.w(e) { "DeviceHardwareRepository: failed to seed cache from bundled JSON" } }
    }

    private suspend fun refreshFromNetwork() {
        safeCatching {
            Logger.d { "DeviceHardwareRepository: fetching from remote API" }
            val remoteHardware = remoteDataSource.getAllDeviceHardware()
            Logger.d { "DeviceHardwareRepository: remote returned ${remoteHardware.size} entries" }
            localDataSource.insertAllDeviceHardware(remoteHardware)
        }
            .onFailure { e -> Logger.w(e) { "DeviceHardwareRepository: network refresh failed" } }
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

    private fun loadQuirks(): List<BootloaderOtaQuirk> {
        val quirks = bootloaderOtaQuirksJsonDataSource.loadBootloaderOtaQuirksFromJsonAsset()
        Logger.d { "DeviceHardwareRepository: loaded ${quirks.size} bootloader quirks" }
        return quirks
    }

    private fun applyBootloaderQuirk(
        hwModel: Int,
        base: DeviceHardware?,
        quirks: List<BootloaderOtaQuirk>,
        reportedTarget: String? = null,
    ): DeviceHardware? = base?.let { hw ->
        val matchedQuirk = quirks.firstOrNull { it.hwModel == hwModel }
        val withQuirk =
            if (matchedQuirk != null) {
                Logger.d {
                    "DeviceHardwareRepository: applying quirk: " +
                        "requiresBootloaderUpgradeForOta=${matchedQuirk.requiresBootloaderUpgradeForOta}, " +
                        "infoUrl=${matchedQuirk.infoUrl}"
                }
                hw.copy(
                    requiresBootloaderUpgradeForOta = matchedQuirk.requiresBootloaderUpgradeForOta,
                    bootloaderInfoUrl = matchedQuirk.infoUrl,
                )
            } else {
                hw
            }

        // If the device reported a specific build environment via pio_env, trust it for firmware retrieval
        reportedTarget?.let { withQuirk.copy(platformioTarget = it) } ?: withQuirk
    }

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_DAY.inWholeMilliseconds
    }
}
