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
import com.geeksville.mesh.api.ApiService
import com.geeksville.mesh.database.dao.DeviceRegistrationDao
import com.geeksville.mesh.database.entity.DeviceRegistrationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class DeviceRegistrationRepository @Inject constructor(
    private val apiService: ApiService,
    private val deviceRegistrationDaoLazy: dagger.Lazy<DeviceRegistrationDao>,
) {
    companion object {
        // Define the cache expiration time (e.g., 1 hour)
        private const val CACHE_EXPIRATION_TIME_MS = 60 * 60 * 1000L
    }

    private val deviceRegistrationDao by lazy {
        deviceRegistrationDaoLazy.get()
    }

    /**
     * Checks if a device is registered, first looking in the cache,
     * then the network if the cache is empty or the refresh is true.
     * If the cache is used and there is an error, the function will
     * return null to indicate that we don't know if the device is
     * registered.
     */
    suspend fun isDeviceRegistered(deviceId: String, refresh: Boolean = false): Boolean? {
        return withContext(Dispatchers.IO) {
            // Check the cache first
            if (!refresh) {
                val cachedRegistration = deviceRegistrationDao.getRegistration(deviceId)
                if (cachedRegistration != null) {
                    // Check if the cache is expired
                    if (!isCacheExpired(cachedRegistration.lastUpdated)) {
                        return@withContext cachedRegistration.isRegistered
                    }
                }
            } else {
                invalidateCache(deviceId)
            }
            // If cache miss or expired, check the server
            try {
                // TODO: Replace with actual API call to check registration
                val response = apiService.checkDeviceRegistration(deviceId)
                // val isRegistered = response.isSuccessful
                if (response.isSuccessful) {
                    val isRegistered = true // Assuming the API returns a Boolean
                    // Update the cache
                    deviceRegistrationDao.insert(
                        DeviceRegistrationEntity(
                            deviceId = deviceId,
                            isRegistered = isRegistered
                        )
                    )
                    return@withContext isRegistered
                } else {
                    warn("Error checking device registration: ${response.code()}")
                    return@withContext null
                }
            } catch (e: IOException) {
                // return cached data if available or null if not
                warn("Error checking device registration: ${e.message}")
                val cachedRegistration = deviceRegistrationDao.getRegistration(deviceId)
                if (cachedRegistration != null) {
                    return@withContext cachedRegistration.isRegistered
                }
                return@withContext null
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
            val registration = deviceRegistrationDao.getRegistration(deviceId)
            registration?.let {
                deviceRegistrationDao.delete(deviceId)
            }
        }
    }
}
