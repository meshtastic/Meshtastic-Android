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

package org.meshtastic.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareLocalDataSource
import org.meshtastic.core.database.entity.DeviceHardwareEntity
import org.meshtastic.core.database.entity.asExternalModel
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.network.DeviceHardwareRemoteDataSource
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Annotating with Singleton to ensure a single instance manages the cache
@Singleton
class DeviceHardwareRepository
@Inject
constructor(
    private val remoteDataSource: DeviceHardwareRemoteDataSource,
    private val localDataSource: DeviceHardwareLocalDataSource,
    private val jsonDataSource: DeviceHardwareJsonDataSource,
) {

    /**
     * Retrieves device hardware information by its model ID.
     *
     * This function implements a cache-aside pattern with a fallback mechanism:
     * 1. Check for a valid, non-expired local cache entry.
     * 2. If not found or expired, fetch fresh data from the remote API.
     * 3. If the remote fetch fails, attempt to use stale data from the cache.
     * 4. If the cache is empty, fall back to loading data from a bundled JSON asset.
     *
     * @param hwModel The hardware model identifier.
     * @param forceRefresh If true, the local cache will be invalidated and data will be fetched remotely.
     * @return A [Result] containing the [DeviceHardware] on success (or null if not found), or an exception on failure.
     */
    suspend fun getDeviceHardwareByModel(hwModel: Int, forceRefresh: Boolean = false): Result<DeviceHardware?> =
        withContext(Dispatchers.IO) {
            Timber.d("DeviceHardwareRepository: getDeviceHardwareByModel(hwModel=%d, forceRefresh=%b)", hwModel, forceRefresh)

            if (forceRefresh) {
                Timber.d("DeviceHardwareRepository: forceRefresh=true, clearing local device hardware cache")
                localDataSource.deleteAllDeviceHardware()
            } else {
                // 1. Attempt to retrieve from cache first
                val cachedEntity = localDataSource.getByHwModel(hwModel)
                if (cachedEntity != null && !cachedEntity.isStale()) {
                    Timber.d("DeviceHardwareRepository: using fresh cached device hardware for hwModel=%d", hwModel)
                    return@withContext Result.success(cachedEntity.asExternalModel())
                }
                Timber.d("DeviceHardwareRepository: no fresh cache for hwModel=%d, attempting remote fetch", hwModel)
            }

            // 2. Fetch from remote API
            runCatching {
                Timber.d("DeviceHardwareRepository: fetching device hardware from remote API")
                val remoteHardware = remoteDataSource.getAllDeviceHardware()
                Timber.d("DeviceHardwareRepository: remote API returned %d device hardware entries", remoteHardware.size)

                localDataSource.insertAllDeviceHardware(remoteHardware)
                val fromDb = localDataSource.getByHwModel(hwModel)?.asExternalModel()
                Timber.d(
                    "DeviceHardwareRepository: lookup after remote fetch for hwModel=%d %s",
                    hwModel,
                    if (fromDb != null) "succeeded" else "returned null",
                )
                fromDb
            }
                .onSuccess {
                    // Successfully fetched and found the model
                    return@withContext Result.success(it)
                }
                .onFailure { e ->
                    Timber.w(e, "DeviceHardwareRepository: failed to fetch device hardware from server for hwModel=%d", hwModel)

                    // 3. Attempt to use stale cache as a fallback, but only if it looks complete.
                    val staleEntity = localDataSource.getByHwModel(hwModel)
                    if (staleEntity != null && !staleEntity.isIncomplete()) {
                        Timber.d("DeviceHardwareRepository: using stale cached device hardware for hwModel=%d", hwModel)
                        return@withContext Result.success(staleEntity.asExternalModel())
                    }

                    // 4. Fallback to bundled JSON if cache is empty or incomplete
                    Timber.d(
                        "DeviceHardwareRepository: cache %s for hwModel=%d, falling back to bundled JSON asset",
                        if (staleEntity == null) "empty" else "incomplete",
                        hwModel,
                    )
                    return@withContext loadFromBundledJson(hwModel)
                }
        }

    private suspend fun loadFromBundledJson(hwModel: Int): Result<DeviceHardware?> = runCatching {
        Timber.d("DeviceHardwareRepository: loading device hardware from bundled JSON for hwModel=%d", hwModel)
        val jsonHardware = jsonDataSource.loadDeviceHardwareFromJsonAsset()
        Timber.d("DeviceHardwareRepository: bundled JSON returned %d device hardware entries", jsonHardware.size)

        localDataSource.insertAllDeviceHardware(jsonHardware)
        val fromDb = localDataSource.getByHwModel(hwModel)?.asExternalModel()
        Timber.d(
            "DeviceHardwareRepository: lookup after JSON load for hwModel=%d %s",
            hwModel,
            if (fromDb != null) "succeeded" else "returned null",
        )
        fromDb
    }

    /** Returns true if the cached entity is missing important fields and should be refreshed. */
    private fun DeviceHardwareEntity.isIncomplete(): Boolean =
        displayName.isBlank() ||
            platformioTarget.isBlank() ||
            images == null ||
            images.isEmpty()

    /**
     * Extension function to check if the cached entity is stale.
     *
     * We treat entries with missing critical fields (e.g., no images or target) as stale so that they can be
     * automatically healed from newer JSON snapshots even if their timestamp is recent.
     */
    private fun DeviceHardwareEntity.isStale(): Boolean =
        isIncomplete() || (System.currentTimeMillis() - this.lastUpdated) > CACHE_EXPIRATION_TIME_MS

    companion object {
        private val CACHE_EXPIRATION_TIME_MS = TimeUnit.DAYS.toMillis(1)
    }
}
