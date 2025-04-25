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

package com.geeksville.mesh.network

import com.geeksville.mesh.network.model.NetworkDeviceRegistration
import com.geeksville.mesh.network.retrofit.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeviceRegistrationRemoteDataSource @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun checkDeviceRegistration(deviceId: String): NetworkDeviceRegistration = withContext(Dispatchers.IO) {
        NetworkDeviceRegistration(apiService.checkDeviceRegistration(deviceId).isSuccessful)
    }
}
