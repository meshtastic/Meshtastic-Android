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

package com.geeksville.mesh

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// MyNodeInfo sent via special protobuf from radio
@Parcelize
data class MyNodeInfo(
        val myNodeNum: Int,
        val hasGPS: Boolean,
        val model: String?,
        val firmwareVersion: String?,
        val couldUpdate: Boolean, // this application contains a software load we _could_ install if you want
        val shouldUpdate: Boolean, // this device has old firmware
        val currentPacketId: Long,
        val messageTimeoutMsec: Int,
        val minAppVersion: Int,
        val maxChannels: Int,
        val hasWifi: Boolean,
        val channelUtilization: Float,
        val airUtilTx: Float,
        val deviceId: String?,
) : Parcelable {
        /** A human readable description of the software/hardware version */
        val firmwareString: String get() = "$model $firmwareVersion"
}