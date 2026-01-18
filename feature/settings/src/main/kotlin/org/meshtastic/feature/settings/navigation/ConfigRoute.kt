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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.bluetooth
import org.meshtastic.core.strings.channels
import org.meshtastic.core.strings.device
import org.meshtastic.core.strings.display
import org.meshtastic.core.strings.lora
import org.meshtastic.core.strings.network
import org.meshtastic.core.strings.position
import org.meshtastic.core.strings.power
import org.meshtastic.core.strings.security
import org.meshtastic.core.strings.user
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.DeviceMetadata

enum class ConfigRoute(val title: StringResource, val route: Route, val icon: ImageVector?, val type: Int = 0) {
    USER(Res.string.user, SettingsRoutes.User, Icons.Default.Person, 0),
    CHANNELS(Res.string.channels, SettingsRoutes.ChannelConfig, Icons.AutoMirrored.Default.List, 0),
    DEVICE(Res.string.device, SettingsRoutes.Device, Icons.Default.Router, AdminMessage.ConfigType.DEVICE_CONFIG.value),
    POSITION(
        Res.string.position,
        SettingsRoutes.Position,
        Icons.Default.LocationOn,
        AdminMessage.ConfigType.POSITION_CONFIG.value,
    ),
    POWER(Res.string.power, SettingsRoutes.Power, Icons.Default.Power, AdminMessage.ConfigType.POWER_CONFIG.value),
    NETWORK(
        Res.string.network,
        SettingsRoutes.Network,
        Icons.Default.Wifi,
        AdminMessage.ConfigType.NETWORK_CONFIG.value,
    ),
    DISPLAY(
        Res.string.display,
        SettingsRoutes.Display,
        Icons.Default.DisplaySettings,
        AdminMessage.ConfigType.DISPLAY_CONFIG.value,
    ),
    LORA(Res.string.lora, SettingsRoutes.LoRa, Icons.Default.CellTower, AdminMessage.ConfigType.LORA_CONFIG.value),
    BLUETOOTH(
        Res.string.bluetooth,
        SettingsRoutes.Bluetooth,
        Icons.Default.Bluetooth,
        AdminMessage.ConfigType.BLUETOOTH_CONFIG.value,
    ),
    SECURITY(
        Res.string.security,
        SettingsRoutes.Security,
        Icons.Default.Security,
        AdminMessage.ConfigType.SECURITY_CONFIG.value,
    ),
    ;

    companion object {
        private fun filterExcludedFrom(metadata: DeviceMetadata?): List<ConfigRoute> = entries.filter {
            when {
                metadata == null -> true // Include all routes if metadata is null
                it == BLUETOOTH -> metadata.hasBluetooth == true
                it == NETWORK -> metadata.hasWifi == true || metadata.hasEthernet == true
                else -> true // Include all other routes by default
            }
        }

        val radioConfigRoutes = listOf(LORA, CHANNELS, SECURITY)

        fun deviceConfigRoutes(metadata: DeviceMetadata?): List<ConfigRoute> =
            filterExcludedFrom(metadata) - radioConfigRoutes
    }
}
