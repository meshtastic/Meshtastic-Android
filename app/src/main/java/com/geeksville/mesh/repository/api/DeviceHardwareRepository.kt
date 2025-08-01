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
import com.geeksville.mesh.database.entity.asExternalModel
import com.geeksville.mesh.model.DeviceHardware
import com.geeksville.mesh.network.DeviceHardwareRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DeviceHardwareRepository
@Inject
constructor(
    private val apiDataSource: DeviceHardwareRemoteDataSource,
    private val localDataSource: DeviceHardwareLocalDataSource,
    private val jsonDataSource: DeviceHardwareJsonDataSource,
) {

    companion object {
        // 1 day
        private const val CACHE_EXPIRATION_TIME_MS = 24 * 60 * 60 * 1000L
    }

    suspend fun getDeviceHardwareByModel(hwModel: Int, refresh: Boolean = false): DeviceHardware? {
        return withContext(Dispatchers.IO) {
            if (refresh) {
                invalidateCache()
            } else {
                val cachedHardware = localDataSource.getByHwModel(hwModel)
                if (cachedHardware != null && !isCacheExpired(cachedHardware.lastUpdated)) {
                    val externalModel = cachedHardware.asExternalModel()
                    return@withContext externalModel
                }
            }
            try {
                val deviceHardware =
                    apiDataSource.getAllDeviceHardware() ?: throw IOException("empty response from server")
                localDataSource.insertAllDeviceHardware(deviceHardware)
                val cachedHardware = localDataSource.getByHwModel(hwModel)
                val externalModel = cachedHardware?.asExternalModel()
                return@withContext externalModel
            } catch (e: IOException) {
                warn("Failed to fetch device hardware from server: ${e.message}")
                var cachedHardware = localDataSource.getByHwModel(hwModel)
                if (cachedHardware != null) {
                    debug("Using stale cached device hardware")
                    return@withContext cachedHardware.asExternalModel()
                }
                localDataSource.insertAllDeviceHardware(jsonDataSource.loadDeviceHardwareFromJsonAsset())
                cachedHardware = localDataSource.getByHwModel(hwModel)
                val externalModel = cachedHardware?.asExternalModel()
                return@withContext externalModel
            }
        }
    }

    suspend fun invalidateCache() {
        localDataSource.deleteAllDeviceHardware()
    }

    /** Check if the cache is expired */
    private fun isCacheExpired(lastUpdated: Long): Boolean =
        System.currentTimeMillis() - lastUpdated > CACHE_EXPIRATION_TIME_MS
}
