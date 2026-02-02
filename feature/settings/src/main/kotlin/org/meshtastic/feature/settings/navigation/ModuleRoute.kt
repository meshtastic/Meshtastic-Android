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
package org.meshtastic.feature.settings.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PermScanWifi
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.ambient_lighting
import org.meshtastic.core.strings.audio
import org.meshtastic.core.strings.canned_message
import org.meshtastic.core.strings.detection_sensor
import org.meshtastic.core.strings.external_notification
import org.meshtastic.core.strings.mqtt
import org.meshtastic.core.strings.neighbor_info
import org.meshtastic.core.strings.paxcounter
import org.meshtastic.core.strings.range_test
import org.meshtastic.core.strings.remote_hardware
import org.meshtastic.core.strings.serial
import org.meshtastic.core.strings.status_message
import org.meshtastic.core.strings.store_forward
import org.meshtastic.core.strings.telemetry
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.DeviceMetadata

enum class ModuleRoute(
    val title: StringResource,
    val route: Route,
    val icon: ImageVector?,
    val type: Int = 0,
    val isSupported: (Capabilities) -> Boolean = { true },
) {
    MQTT(Res.string.mqtt, SettingsRoutes.MQTT, Icons.Rounded.Cloud, AdminMessage.ModuleConfigType.MQTT_CONFIG.value),
    SERIAL(
        Res.string.serial,
        SettingsRoutes.Serial,
        Icons.Rounded.Usb,
        AdminMessage.ModuleConfigType.SERIAL_CONFIG.value,
    ),
    EXT_NOTIFICATION(
        Res.string.external_notification,
        SettingsRoutes.ExtNotification,
        Icons.Rounded.Notifications,
        AdminMessage.ModuleConfigType.EXTNOTIF_CONFIG.value,
    ),
    STORE_FORWARD(
        Res.string.store_forward,
        SettingsRoutes.StoreForward,
        Icons.AutoMirrored.Default.Forward,
        AdminMessage.ModuleConfigType.STOREFORWARD_CONFIG.value,
    ),
    RANGE_TEST(
        Res.string.range_test,
        SettingsRoutes.RangeTest,
        Icons.Rounded.Speed,
        AdminMessage.ModuleConfigType.RANGETEST_CONFIG.value,
    ),
    TELEMETRY(
        Res.string.telemetry,
        SettingsRoutes.Telemetry,
        Icons.Rounded.DataUsage,
        AdminMessage.ModuleConfigType.TELEMETRY_CONFIG.value,
    ),
    CANNED_MESSAGE(
        Res.string.canned_message,
        SettingsRoutes.CannedMessage,
        Icons.AutoMirrored.Default.Message,
        AdminMessage.ModuleConfigType.CANNEDMSG_CONFIG.value,
    ),
    AUDIO(
        Res.string.audio,
        SettingsRoutes.Audio,
        Icons.AutoMirrored.Default.VolumeUp,
        AdminMessage.ModuleConfigType.AUDIO_CONFIG.value,
    ),
    REMOTE_HARDWARE(
        Res.string.remote_hardware,
        SettingsRoutes.RemoteHardware,
        Icons.Rounded.SettingsRemote,
        AdminMessage.ModuleConfigType.REMOTEHARDWARE_CONFIG.value,
    ),
    NEIGHBOR_INFO(
        Res.string.neighbor_info,
        SettingsRoutes.NeighborInfo,
        Icons.Rounded.People,
        AdminMessage.ModuleConfigType.NEIGHBORINFO_CONFIG.value,
    ),
    AMBIENT_LIGHTING(
        Res.string.ambient_lighting,
        SettingsRoutes.AmbientLighting,
        Icons.Rounded.LightMode,
        AdminMessage.ModuleConfigType.AMBIENTLIGHTING_CONFIG.value,
    ),
    DETECTION_SENSOR(
        Res.string.detection_sensor,
        SettingsRoutes.DetectionSensor,
        Icons.Rounded.Sensors,
        AdminMessage.ModuleConfigType.DETECTIONSENSOR_CONFIG.value,
    ),
    PAXCOUNTER(
        Res.string.paxcounter,
        SettingsRoutes.Paxcounter,
        Icons.Rounded.PermScanWifi,
        AdminMessage.ModuleConfigType.PAXCOUNTER_CONFIG.value,
    ),
    STATUS_MESSAGE(
        Res.string.status_message,
        SettingsRoutes.StatusMessage,
        Icons.AutoMirrored.Default.Message,
        AdminMessage.ModuleConfigType.STATUSMESSAGE_CONFIG.value,
        isSupported = { it.supportsStatusMessage },
    ),
    ;

    val bitfield: Int
        get() = 1 shl ordinal

    companion object {
        fun filterExcludedFrom(metadata: DeviceMetadata?): List<ModuleRoute> {
            val capabilities = Capabilities(metadata?.firmware_version)
            return entries.filter {
                val excludedModules = metadata?.excluded_modules ?: 0
                val isExcluded = (excludedModules and it.bitfield) != 0
                !isExcluded && it.isSupported(capabilities)
            }
        }
    }
}
