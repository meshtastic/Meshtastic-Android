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

package com.geeksville.mesh.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.geeksville.mesh.network.model.NetworkDeviceRegistration

@Entity(tableName = "device_registration")
data class DeviceRegistrationEntity(
    @PrimaryKey val deviceId: String,
    @ColumnInfo(name = "is_registered") val isRegistered: Boolean,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)

fun DeviceRegistrationEntity.asExternalModel() = DeviceRegistration(
    deviceId = deviceId,
    isRegistered = isRegistered,
    lastUpdated = lastUpdated,
)

fun NetworkDeviceRegistration.asEntity(deviceId: String) = DeviceRegistrationEntity(
    deviceId = deviceId,
    isRegistered = registered,
    lastUpdated = System.currentTimeMillis(),
)

data class DeviceRegistration(
    val deviceId: String,
    val isRegistered: Boolean,
    val lastUpdated: Long,
)
