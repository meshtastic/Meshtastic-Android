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

import com.geeksville.mesh.android.BuildUtils.warn
import com.geeksville.mesh.database.dao.DeviceHardwareDao
import com.geeksville.mesh.database.entity.asExternalModel
import com.geeksville.mesh.model.DeviceHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DeviceHardwareRepository @Inject constructor(
    private val apiDataSource: DeviceHardwareApiDataSource,
    private val deviceHardwareDaoLazy: dagger.Lazy<DeviceHardwareDao>
) {

    companion object {
        // Define the cache expiration time (e.g., 1 day)
        private const val CACHE_EXPIRATION_TIME_MS = 24 * 60 * 60 * 1000L
    }

    private val deviceHardwareDao by lazy {
        deviceHardwareDaoLazy.get()
    }

    suspend fun getAllDeviceHardware(refresh: Boolean = false): List<DeviceHardware>? {
        return withContext(Dispatchers.IO) {
            // Check the cache first
            if (!refresh) {
                val cachedHardware = deviceHardwareDao.getAll()
                if (cachedHardware.isNotEmpty()) {
                    if (!isCacheExpired(cachedHardware.first().lastUpdated)) {
                        return@withContext cachedHardware.map { it.asExternalModel() }
                    }
                }
            }

            // If cache miss or expired, check the server
            try {
                deviceHardwareDao.insertAll(apiDataSource.getAllDeviceHardware())
                return@withContext deviceHardwareDao.getAll().map { it.asExternalModel() }
            } catch (e: IOException) {
                warn("Failed to fetch device hardware from server: ${e.message}")
                // return cached data if available or null if not
                return@withContext deviceHardwareDao.getAll().map { it.asExternalModel() }
            }
        }
    }

    suspend fun getDeviceHardwareByModel(hwModel: Int, refresh: Boolean = false): DeviceHardware? {
        return withContext(Dispatchers.IO) {
            // Check the cache first
            if (!refresh) {
                val cachedHardware = deviceHardwareDao.getByHwModel(hwModel)
                if (cachedHardware != null) {
                    return@withContext cachedHardware.asExternalModel()
                }
            }

            // If cache miss, check the server
            try {
                deviceHardwareDao.insertAll(apiDataSource.getAllDeviceHardware())
                return@withContext deviceHardwareDao.getByHwModel(hwModel)?.asExternalModel()
            } catch (e: IOException) {
                warn("Failed to fetch device hardware from server: ${e.message}")
                // return cached data if available or null if not
                return@withContext deviceHardwareDao.getByHwModel(hwModel)?.asExternalModel()
            }
        }
    }

    suspend fun invalidateCache() {
        withContext(Dispatchers.IO) {
            deviceHardwareDao.deleteAll()
        }
    }

    /**
     * Check if the cache is expired
     */
    private fun isCacheExpired(lastUpdated: Long): Boolean {
        return System.currentTimeMillis() - lastUpdated > CACHE_EXPIRATION_TIME_MS
    }
}
