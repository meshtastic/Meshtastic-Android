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

package com.geeksville.mesh.ui.radioconfig

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PermScanWifi
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.R
import com.geeksville.mesh.navigation.Route

@Suppress("MagicNumber")
// ModuleConfig (type = AdminProtos.AdminMessage.ModuleConfigType)
enum class ModuleRoute(@StringRes val title: Int, val route: Route, val icon: ImageVector?, val type: Int = 0) {
    MQTT(R.string.mqtt, Route.MQTT, Icons.Default.Cloud, 0),
    SERIAL(R.string.serial, Route.Serial, Icons.Default.Usb, 1),
    EXT_NOTIFICATION(R.string.external_notification, Route.ExtNotification, Icons.Default.Notifications, 2),
    STORE_FORWARD(R.string.store_forward, Route.StoreForward, Icons.AutoMirrored.Default.Forward, 3),
    RANGE_TEST(R.string.range_test, Route.RangeTest, Icons.Default.Speed, 4),
    TELEMETRY(R.string.telemetry, Route.Telemetry, Icons.Default.DataUsage, 5),
    CANNED_MESSAGE(R.string.canned_message, Route.CannedMessage, Icons.AutoMirrored.Default.Message, 6),
    AUDIO(R.string.audio, Route.Audio, Icons.AutoMirrored.Default.VolumeUp, 7),
    REMOTE_HARDWARE(R.string.remote_hardware, Route.RemoteHardware, Icons.Default.SettingsRemote, 8),
    NEIGHBOR_INFO(R.string.neighbor_info, Route.NeighborInfo, Icons.Default.People, 9),
    AMBIENT_LIGHTING(R.string.ambient_lighting, Route.AmbientLighting, Icons.Default.LightMode, 10),
    DETECTION_SENSOR(R.string.detection_sensor, Route.DetectionSensor, Icons.Default.Sensors, 11),
    PAXCOUNTER(R.string.paxcounter, Route.Paxcounter, Icons.Default.PermScanWifi, 12),
    ;

    val bitfield: Int get() = 1 shl ordinal

    companion object {
        fun filterExcludedFrom(metadata: DeviceMetadata?): List<ModuleRoute> = entries.filter {
            when (metadata) {
                null -> true
                else -> metadata.excludedModules and it.bitfield == 0
            }
        }
    }
}
