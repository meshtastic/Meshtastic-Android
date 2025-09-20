/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.api

import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.warn
import com.geeksville.mesh.database.entity.FirmwareRelease
import com.geeksville.mesh.database.entity.FirmwareReleaseEntity
import com.geeksville.mesh.database.entity.FirmwareReleaseType
import com.geeksville.mesh.database.entity.asExternalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.meshtastic.core.network.FirmwareReleaseRemoteDataSource
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirmwareReleaseRepository
@Inject
constructor(
    private val remoteDataSource: FirmwareReleaseRemoteDataSource,
    private val localDataSource: FirmwareReleaseLocalDataSource,
    private val jsonDataSource: FirmwareReleaseJsonDataSource,
) {

    /**
     * A flow that provides the latest STABLE firmware release. It follows a "cache-then-network" strategy:
     * 1. Immediately emits the cached version (if any).
     * 2. If the cached version is stale, triggers a network fetch in the background.
     * 3. Emits the updated version upon successful fetch. Collectors should use `.distinctUntilChanged()` to avoid
     *    redundant UI updates.
     */
    val stableRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.STABLE)

    /**
     * A flow that provides the latest ALPHA firmware release.
     *
     * @see stableRelease for behavior details.
     */
    val alphaRelease: Flow<FirmwareRelease?> = getLatestFirmware(FirmwareReleaseType.ALPHA)

    private fun getLatestFirmware(
        releaseType: FirmwareReleaseType,
        forceRefresh: Boolean = false,
    ): Flow<FirmwareRelease?> = flow {
        if (forceRefresh) {
            invalidateCache()
        }

        // 1. Emit cached data first, regardless of staleness.
        // This gives the UI something to show immediately.
        val cachedRelease = localDataSource.getLatestRelease(releaseType)
        cachedRelease?.let {
            debug("Emitting cached firmware for $releaseType (isStale=${it.isStale()})")
            emit(it.asExternalModel())
        }

        // 2. If the cache was fresh and we are not forcing a refresh, we're done.
        if (cachedRelease != null && !cachedRelease.isStale() && !forceRefresh) {
            return@flow
        }

        // 3. Cache is stale, empty, or refresh is forced. Fetch new data.
        updateCacheFromSources()

        // 4. Emit the final, updated value from the cache.
        // The `distinctUntilChanged()` operator on the collector side will prevent
        // re-emitting the same data if the cache wasn't actually updated.
        val finalRelease = localDataSource.getLatestRelease(releaseType)
        debug("Emitting final firmware for $releaseType from cache.")
        emit(finalRelease?.asExternalModel())
    }

    /**
     * Updates the local cache by fetching from the remote API, with a fallback to a bundled JSON asset if the remote
     * fetch fails.
     *
     * This method is efficient because it fetches and caches all release types (stable, alpha, etc.) in a single
     * operation.
     */
    private suspend fun updateCacheFromSources() {
        val remoteFetchSuccess =
            runCatching {
                debug("Fetching fresh firmware releases from remote API.")
                val networkReleases = remoteDataSource.getFirmwareReleases()

                // The API fetches all release types, so we cache them all at once.
                localDataSource.insertFirmwareReleases(networkReleases.releases.stable, FirmwareReleaseType.STABLE)
                localDataSource.insertFirmwareReleases(networkReleases.releases.alpha, FirmwareReleaseType.ALPHA)
            }
                .isSuccess

        // If remote fetch failed, try the JSON fallback as a last resort.
        if (!remoteFetchSuccess) {
            warn("Remote fetch failed, attempting to cache from bundled JSON.")
            runCatching {
                val jsonReleases = jsonDataSource.loadFirmwareReleaseFromJsonAsset()
                localDataSource.insertFirmwareReleases(jsonReleases.releases.stable, FirmwareReleaseType.STABLE)
                localDataSource.insertFirmwareReleases(jsonReleases.releases.alpha, FirmwareReleaseType.ALPHA)
            }
                .onFailure { warn("Failed to cache from JSON: ${it.message}") }
        }
    }

    suspend fun invalidateCache() {
        localDataSource.deleteAllFirmwareReleases()
    }

    /** Extension function to check if the cached entity is stale. */
    private fun FirmwareReleaseEntity.isStale(): Boolean =
        (System.currentTimeMillis() - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeUnit.HOURS.toMillis(1)
    }
}
