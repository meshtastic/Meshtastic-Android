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
package org.meshtastic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.meshtastic.core.database.entity.DeviceHardwareEntity

@Dao
interface DeviceHardwareDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deviceHardware: DeviceHardwareEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deviceHardware: List<DeviceHardwareEntity>)

    @Query("SELECT * FROM device_hardware WHERE hwModel = :hwModel")
    suspend fun getByHwModel(hwModel: Int): List<DeviceHardwareEntity>

    @Query("SELECT * FROM device_hardware WHERE platformio_target = :target")
    suspend fun getByTarget(target: String): DeviceHardwareEntity?

    @Query("SELECT * FROM device_hardware WHERE hwModel = :hwModel AND platformio_target = :target")
    suspend fun getByModelAndTarget(hwModel: Int, target: String): DeviceHardwareEntity?

    @Query("DELETE FROM device_hardware")
    suspend fun deleteAll()
}
