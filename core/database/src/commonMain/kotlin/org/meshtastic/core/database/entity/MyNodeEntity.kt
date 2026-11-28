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
package org.meshtastic.core.database.entity

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import org.meshtastic.core.model.MyNodeInfo

@Entity(tableName = "my_node")
open class MyNodeEntity(
    @PrimaryKey(autoGenerate = false) val myNodeNum: Int,
    val model: String?,
    val firmwareVersion: String?,
    val minAppVersion: Int,
    val hasWifi: Boolean,
    val deviceId: String? = DEVICE_ID_UNKNOWN,
    val pioEnv: String? = null,
) {
    companion object {
        /** Placeholder for hardware that doesn't report a factory-burned device id. */
        const val DEVICE_ID_UNKNOWN = "unknown"
    }

    /** A human readable description of the software/hardware version */
    @Ignore
    val firmwareString: String
        get() = "$model $firmwareVersion"

    open fun toMyNodeInfo() = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = false,
        model = model,
        firmwareVersion = firmwareVersion,
        minAppVersion = minAppVersion,
        hasWifi = hasWifi,
        deviceId = deviceId,
        pioEnv = pioEnv,
    )
}
