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

import com.geeksville.mesh.database.dao.DeviceRegistrationDao
import com.geeksville.mesh.database.entity.DeviceRegistrationEntity
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DeviceRegistrationLocalDataSource @Inject constructor(
    private val deviceRegistrationDaoLazy: Lazy<DeviceRegistrationDao>
) {
    private val deviceRegistrationDao by lazy {
        deviceRegistrationDaoLazy.get()
    }

    suspend fun insert(deviceRegistrationEntity: DeviceRegistrationEntity) =
        withContext(Dispatchers.IO) {
            deviceRegistrationDao.insert(deviceRegistrationEntity)
    }

    suspend fun update(deviceRegistrationEntity: DeviceRegistrationEntity) =
        withContext(Dispatchers.IO) {
            deviceRegistrationDao.update(deviceRegistrationEntity)
        }

    suspend fun getRegistration(deviceId: String) = withContext(Dispatchers.IO) {
        deviceRegistrationDao.getRegistration(deviceId)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        deviceRegistrationDao.deleteAll()
    }

    suspend fun delete(deviceId: String) = withContext(Dispatchers.IO) {
        deviceRegistrationDao.delete(deviceId)
    }
}
