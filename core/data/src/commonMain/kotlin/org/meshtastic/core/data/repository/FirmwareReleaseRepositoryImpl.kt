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
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSource
import org.meshtastic.core.data.datasource.FirmwareReleaseLocalDataSource
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.FirmwareReleaseRemoteDataSource
import org.meshtastic.core.repository.FirmwareReleaseRepository

@Single
open class FirmwareReleaseRepositoryImpl(
    private val remoteDataSource: FirmwareReleaseRemoteDataSource,
    private val localDataSource: FirmwareReleaseLocalDataSource,
    private val jsonDataSource: FirmwareReleaseJsonDataSource,
) : FirmwareReleaseRepository {

    /**
     * A flow that provides the latest STABLE firmware release.
     *
     * Pipeline:
     * 1. If the local DB is empty, seed it from the bundled JSON asset (instant baseline).
     * 2. Immediately emit the cached version.
     * 3. If the cached version is stale, refresh from the remote API and re-emit.
     *
     * Collectors should use `.distinctUntilChanged()` to avoid redundant UI updates.
     */
    override val stableRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.STABLE)

    /**
     * A flow that provides the latest ALPHA firmware release.
     *
     * @see stableRelease for behavior details.
     */
    override val alphaRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.ALPHA)

    private fun getLatestFirmware(releaseType: FirmwareReleaseType): Flow<FirmwareRelease?> = flow {
        // 1. Seed from bundled JSON if the DB is completely empty (first launch or post-wipe).
        if (!localDataSource.hasAnyEntries()) {
            seedCacheFromBundledJson()
        }

        // 2. Emit cached data immediately.
        val cachedRelease = localDataSource.getLatestRelease(releaseType)
        emit(cachedRelease?.asExternalModel())

        // 3. If fresh, we're done.
        if (cachedRelease?.isStale() == false) {
            return@flow
        }

        // 4. Cache is stale or empty — refresh from network and re-emit.
        refreshFromNetwork()
        val finalRelease = localDataSource.getLatestRelease(releaseType)
        Logger.d { "Emitting final firmware for $releaseType from cache." }
        emit(finalRelease?.asExternalModel())
    }

    private suspend fun refreshFromNetwork() {
        safeCatching {
            Logger.d { "Fetching fresh firmware releases from remote API." }
            val networkReleases = remoteDataSource.getFirmwareReleases()
            localDataSource.insertFirmwareReleases(networkReleases.releases.stable, FirmwareReleaseType.STABLE)
            localDataSource.insertFirmwareReleases(networkReleases.releases.alpha, FirmwareReleaseType.ALPHA)
        }
            .onFailure { e -> Logger.w(e) { "FirmwareReleaseRepository: network refresh failed" } }
    }

    override suspend fun invalidateCache() {
        localDataSource.deleteAllFirmwareReleases()
    }

    private suspend fun seedCacheFromBundledJson() {
        safeCatching {
            Logger.d { "FirmwareReleaseRepository: seeding cache from bundled JSON" }
            val jsonReleases = jsonDataSource.loadFirmwareReleaseFromJsonAsset()
            localDataSource.insertFirmwareReleases(jsonReleases.releases.stable, FirmwareReleaseType.STABLE)
            localDataSource.insertFirmwareReleases(jsonReleases.releases.alpha, FirmwareReleaseType.ALPHA)
        }
            .onFailure { e -> Logger.w(e) { "FirmwareReleaseRepository: failed to seed cache from bundled JSON" } }
    }

    private fun FirmwareReleaseEntity.isStale(): Boolean = (nowMillis - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_HOUR.inWholeMilliseconds
    }
}
