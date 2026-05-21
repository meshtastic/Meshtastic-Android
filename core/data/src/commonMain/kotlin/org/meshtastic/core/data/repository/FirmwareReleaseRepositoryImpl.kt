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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSource
import org.meshtastic.core.data.datasource.FirmwareReleaseLocalDataSource
import org.meshtastic.core.data.util.staleWhileRevalidateFlow
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.network.FirmwareReleaseRemoteDataSource
import org.meshtastic.core.repository.FirmwareReleaseRepository

@Single
open class FirmwareReleaseRepositoryImpl(
    private val remoteDataSource: FirmwareReleaseRemoteDataSource,
    private val localDataSource: FirmwareReleaseLocalDataSource,
    private val jsonDataSource: FirmwareReleaseJsonDataSource,
    private val dispatchers: CoroutineDispatchers,
) : FirmwareReleaseRepository {

    /** Single-flight guard so concurrent collectors share one network refresh. */
    private val refreshMutex = Mutex()

    override val stableRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.STABLE)

    override val alphaRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.ALPHA)

    private fun getLatestFirmware(releaseType: FirmwareReleaseType): Flow<FirmwareRelease?> = staleWhileRevalidateFlow(
        loadFromCache = {
            ensureSeeded()
            localDataSource.getLatestRelease(releaseType)?.asExternalModel()
        },
        shouldFetch = { cached ->
            cached == null || localDataSource.getLatestRelease(releaseType)?.isStale() != false
        },
        fetch = { singleFlightRefresh() },
        context = dispatchers.default,
        networkTimeoutMs = NETWORK_REFRESH_TIMEOUT_MS,
        tag = "FirmwareReleaseRepository",
    )

    override suspend fun invalidateCache() {
        localDataSource.deleteAllFirmwareReleases()
    }

    private suspend fun ensureSeeded() {
        if (!localDataSource.hasAnyEntries()) {
            safeCatching {
                Logger.d { "FirmwareReleaseRepository: seeding cache from bundled JSON" }
                val jsonReleases = jsonDataSource.loadFirmwareReleaseFromJsonAsset()
                localDataSource.insertFirmwareReleases(jsonReleases.releases.stable, FirmwareReleaseType.STABLE)
                localDataSource.insertFirmwareReleases(jsonReleases.releases.alpha, FirmwareReleaseType.ALPHA)
            }
                .onFailure { e -> Logger.w(e) { "FirmwareReleaseRepository: failed to seed cache from bundled JSON" } }
        }
    }

    private suspend fun singleFlightRefresh() {
        refreshMutex.withLock {
            Logger.d { "FirmwareReleaseRepository: fetching from remote API" }
            val networkReleases = remoteDataSource.getFirmwareReleases()
            localDataSource.insertFirmwareReleases(networkReleases.releases.stable, FirmwareReleaseType.STABLE)
            localDataSource.insertFirmwareReleases(networkReleases.releases.alpha, FirmwareReleaseType.ALPHA)
        }
    }

    private fun FirmwareReleaseEntity.isStale(): Boolean = (nowMillis - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeConstants.ONE_HOUR.inWholeMilliseconds

        /** Maximum time to wait for the remote API before falling back to cached/bundled data. */
        private const val NETWORK_REFRESH_TIMEOUT_MS = 5_000L
    }
}
