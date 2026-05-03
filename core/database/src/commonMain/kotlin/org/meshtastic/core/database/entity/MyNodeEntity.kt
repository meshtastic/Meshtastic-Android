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
import androidx.room3.PrimaryKey
import org.meshtastic.core.model.MyNodeInfo

@Entity(tableName = "my_node")
@Suppress("LongParameterList")
open class MyNodeEntity(
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
    val pioEnv: String? = null,
) {
    /** A human readable description of the software/hardware version */
    val firmwareString: String
        get() = "$model $firmwareVersion"

    open fun toMyNodeInfo() = MyNodeInfo(
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
        pioEnv = pioEnv,
    )
}
