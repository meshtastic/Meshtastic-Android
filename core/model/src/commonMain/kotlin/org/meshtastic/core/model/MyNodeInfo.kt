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
package org.meshtastic.core.model

// MyNodeInfo sent via special protobuf from radio
data class MyNodeInfo(
    val myNodeNum: Int,
    val hasGPS: Boolean,
    val model: String?,
    val firmwareVersion: String?,
    val minAppVersion: Int,
    val hasWifi: Boolean,
    val deviceId: String?,
    val pioEnv: String? = null,
) {
    /** A human readable description of the software/hardware version */
    val firmwareString: String
        get() = "$model $firmwareVersion"
}
