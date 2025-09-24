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

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.meshtastic.core.model.MyNodeInfo

@Entity(tableName = "my_node")
data class MyNodeEntity(
    @PrimaryKey(autoGenerate = false) val myNodeNum: Int,
    val model: String?,
    val firmwareVersion: String?,
    val couldUpdate: Boolean, // this application contains a software load we _could_ install if you want
    val shouldUpdate: Boolean, // this device has old firmware
    val currentPacketId: Long,
    val messageTimeoutMsec: Int,
    val minAppVersion: Int,
    val maxChannels: Int,
    val hasWifi: Boolean,
    val deviceId: String? = "unknown",
) {
    /** A human readable description of the software/hardware version */
    val firmwareString: String
        get() = "$model $firmwareVersion"

    fun toMyNodeInfo() = MyNodeInfo(
        myNodeNum = myNodeNum,
        hasGPS = false,
        model = model,
        firmwareVersion = firmwareVersion,
        couldUpdate = couldUpdate,
        shouldUpdate = shouldUpdate,
        currentPacketId = currentPacketId,
        messageTimeoutMsec = messageTimeoutMsec,
        minAppVersion = minAppVersion,
        maxChannels = maxChannels,
        hasWifi = hasWifi,
        channelUtilization = 0f,
        airUtilTx = 0f,
        deviceId = deviceId,
    )
}
