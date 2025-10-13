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

package org.meshtastic.feature.settings.navigation

import androidx.annotation.StringRes
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
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.R
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.MeshProtos.DeviceMetadata

enum class ConfigRoute(@StringRes val title: Int, val route: Route, val icon: ImageVector?, val type: Int = 0) {
    USER(R.string.user, SettingsRoutes.User, Icons.Default.Person, 0),
    CHANNELS(R.string.channels, SettingsRoutes.ChannelConfig, Icons.AutoMirrored.Default.List, 0),
    DEVICE(
        R.string.device,
        SettingsRoutes.Device,
        Icons.Default.Router,
        AdminProtos.AdminMessage.ConfigType.DEVICE_CONFIG_VALUE,
    ),
    POSITION(
        R.string.position,
        SettingsRoutes.Position,
        Icons.Default.LocationOn,
        AdminProtos.AdminMessage.ConfigType.POSITION_CONFIG_VALUE,
    ),
    POWER(
        R.string.power,
        SettingsRoutes.Power,
        Icons.Default.Power,
        AdminProtos.AdminMessage.ConfigType.POWER_CONFIG_VALUE,
    ),
    NETWORK(
        R.string.network,
        SettingsRoutes.Network,
        Icons.Default.Wifi,
        AdminProtos.AdminMessage.ConfigType.NETWORK_CONFIG_VALUE,
    ),
    DISPLAY(
        R.string.display,
        SettingsRoutes.Display,
        Icons.Default.DisplaySettings,
        AdminProtos.AdminMessage.ConfigType.DISPLAY_CONFIG_VALUE,
    ),
    LORA(
        R.string.lora,
        SettingsRoutes.LoRa,
        Icons.Default.CellTower,
        AdminProtos.AdminMessage.ConfigType.LORA_CONFIG_VALUE,
    ),
    BLUETOOTH(
        R.string.bluetooth,
        SettingsRoutes.Bluetooth,
        Icons.Default.Bluetooth,
        AdminProtos.AdminMessage.ConfigType.BLUETOOTH_CONFIG_VALUE,
    ),
    SECURITY(
        R.string.security,
        SettingsRoutes.Security,
        Icons.Default.Security,
        AdminProtos.AdminMessage.ConfigType.SECURITY_CONFIG_VALUE,
    ),
    ;

    companion object {
        private fun filterExcludedFrom(metadata: DeviceMetadata?): List<ConfigRoute> = entries.filter {
            when {
                metadata == null -> true // Include all routes if metadata is null
                it == BLUETOOTH -> metadata.hasBluetooth
                it == NETWORK -> metadata.hasWifi || metadata.hasEthernet
                else -> true // Include all other routes by default
            }
        }

        val radioConfigRoutes = listOf(LORA, CHANNELS, SECURITY)

        fun deviceConfigRoutes(metadata: DeviceMetadata?): List<ConfigRoute> =
            filterExcludedFrom(metadata) - radioConfigRoutes
    }
}
