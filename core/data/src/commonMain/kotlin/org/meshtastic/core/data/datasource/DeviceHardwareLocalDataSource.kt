/*
 * Copyright (c) 2026 Meshtastic LLC
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

import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.DeviceHardwareEntity
import org.meshtastic.core.database.entity.asEntity
import org.meshtastic.core.model.NetworkDeviceHardware

@Single
class DeviceHardwareLocalDataSource(private val dbManager: DatabaseProvider) {
    suspend fun insertAllDeviceHardware(deviceHardware: List<NetworkDeviceHardware>) {
        dbManager.withDb { it.deviceHardwareDao().insertAll(deviceHardware.map { hw -> hw.asEntity() }) }
    }

    suspend fun deleteAllDeviceHardware() {
        dbManager.withDb { it.deviceHardwareDao().deleteAll() }
    }

    suspend fun getByHwModel(hwModel: Int): List<DeviceHardwareEntity> =
        dbManager.withDb { it.deviceHardwareDao().getByHwModel(hwModel) }.orEmpty()

    suspend fun getByTarget(target: String): DeviceHardwareEntity? =
        dbManager.withDb { it.deviceHardwareDao().getByTarget(target) }

    suspend fun getByModelAndTarget(hwModel: Int, target: String): DeviceHardwareEntity? =
        dbManager.withDb { it.deviceHardwareDao().getByModelAndTarget(hwModel, target) }

    suspend fun hasAnyEntries(): Boolean = dbManager.withDb { it.deviceHardwareDao().count() > 0 } ?: false

    /** All known `platformioTarget` values — used to determine which msh.to links are vendor links. */
    suspend fun getAllTargets(): List<String> = dbManager.withDb { it.deviceHardwareDao().getAllTargets() }.orEmpty()
}
