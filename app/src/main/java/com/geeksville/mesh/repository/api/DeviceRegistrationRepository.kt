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
import com.geeksville.mesh.database.entity.DeviceRegistration
import com.geeksville.mesh.database.entity.asEntity
import com.geeksville.mesh.database.entity.asExternalModel
import com.geeksville.mesh.network.DeviceRegistrationRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DeviceRegistrationRepository @Inject constructor(
    private val remoteDataSource: DeviceRegistrationRemoteDataSource,
    private val localDataSource: DeviceRegistrationLocalDataSource,
) {
    companion object {
        // 1 hour
        private const val CACHE_EXPIRATION_TIME_MS = 60 * 60 * 1000L
    }
    suspend fun isDeviceRegistered(
        deviceId: String,
        refresh: Boolean = false
    ): DeviceRegistration? {
        return withContext(Dispatchers.IO) {
            if (refresh) {
                debug("Invalidating cache for device registration")
                invalidateCache(deviceId)
            } else {
                val cachedRegistration = localDataSource.getRegistration(deviceId)
                if (cachedRegistration != null && !isCacheExpired(cachedRegistration.lastUpdated)) {
                    debug("Using cached device registration")
                    return@withContext cachedRegistration.asExternalModel()
                }
            }
            try {
                debug("Fetching device registration from server")
                localDataSource.insert(
                    remoteDataSource.checkDeviceRegistration(deviceId).asEntity(deviceId)
                )
                return@withContext localDataSource.getRegistration(deviceId)?.asExternalModel()
            } catch (e: IOException) {
                warn("Failed to fetch device registration from server: ${e.message}")
                return@withContext localDataSource.getRegistration(deviceId)?.asExternalModel()
            }
        }
    }

    /**
     * Check if the cache is expired
     */
    private fun isCacheExpired(lastUpdated: Long): Boolean {
        return System.currentTimeMillis() - lastUpdated > CACHE_EXPIRATION_TIME_MS
    }

    /**
     * Invalidates the cache for a device registration.
     */
    suspend fun invalidateCache(deviceId: String) {
        withContext(Dispatchers.IO) {
            val registration = localDataSource.getRegistration(deviceId)
            registration?.let {
                localDataSource.delete(deviceId)
            }
        }
    }
}
