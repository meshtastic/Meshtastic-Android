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

import com.geeksville.mesh.api.ApiService
import com.geeksville.mesh.database.dao.DeviceHardwareDao
import com.geeksville.mesh.database.entity.DeviceHardwareEntity
import com.geeksville.mesh.database.entity.NetworkDeviceHardware
import com.geeksville.mesh.database.entity.asEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeviceHardwareNetworkDataSource @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun getAllDeviceHardware(): List<NetworkDeviceHardware> = withContext(Dispatchers.IO) {
        apiService.getDeviceHardware().body() ?: emptyList()
    }
}

class DeviceHardwareLocalDataSource @Inject constructor(
    private val deviceHardwareDaoLazy: dagger.Lazy<DeviceHardwareDao>
) {
    private val deviceHardwareDao by lazy {
        deviceHardwareDaoLazy.get()
    }

    suspend fun insertAllDeviceHardware(deviceHardware: List<NetworkDeviceHardware>) =
        withContext(Dispatchers.IO) {
            deviceHardware.forEach { deviceHardware ->
                val entity = deviceHardware.asEntity()
                deviceHardwareDao.insert(entity)
            }
        }

    suspend fun deleteAllDeviceHardware() = withContext(Dispatchers.IO) {
        deviceHardwareDao.deleteAll()
    }

    suspend fun getByHwModel(hwModel: Int): DeviceHardwareEntity? = withContext(Dispatchers.IO) {
        val hwModel = deviceHardwareDao.getByHwModel(hwModel)
        hwModel
    }
}
