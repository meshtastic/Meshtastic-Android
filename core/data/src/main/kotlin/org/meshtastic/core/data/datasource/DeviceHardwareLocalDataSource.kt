/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.data.datasource

import dagger.Lazy
import kotlinx.coroutines.withContext
import org.meshtastic.core.database.dao.DeviceHardwareDao
import org.meshtastic.core.database.entity.DeviceHardwareEntity
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.NetworkDeviceHardware
import javax.inject.Inject

class DeviceHardwareLocalDataSource
@Inject
constructor(
    private val deviceHardwareDaoLazy: Lazy<DeviceHardwareDao>,
    private val dispatchers: CoroutineDispatchers,
) {
    private val deviceHardwareDao by lazy { deviceHardwareDaoLazy.get() }

    suspend fun insertAllDeviceHardware(deviceHardware: List<NetworkDeviceHardware>) =
        withContext(dispatchers.io) { deviceHardwareDao.insertAll(deviceHardware.map { it.asEntity() }) }

    suspend fun deleteAllDeviceHardware() = withContext(dispatchers.io) { deviceHardwareDao.deleteAll() }

    suspend fun getByHwModel(hwModel: Int): List<DeviceHardwareEntity> =
        withContext(dispatchers.io) { deviceHardwareDao.getByHwModel(hwModel) }

    suspend fun getByModelAndTarget(hwModel: Int, target: String): DeviceHardwareEntity? =
        withContext(dispatchers.io) { deviceHardwareDao.getByModelAndTarget(hwModel, target) }
}
