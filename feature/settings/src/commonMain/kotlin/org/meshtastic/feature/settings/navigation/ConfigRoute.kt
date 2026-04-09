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

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.device
import org.meshtastic.core.resources.display
import org.meshtastic.core.resources.lora
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.position
import org.meshtastic.core.resources.power
import org.meshtastic.core.resources.security
import org.meshtastic.core.resources.user
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.CellTower
import org.meshtastic.core.ui.icon.ConfigChannels
import org.meshtastic.core.ui.icon.DisplaySettings
import org.meshtastic.core.ui.icon.HardwareModel
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.PowerSupply
import org.meshtastic.core.ui.icon.SecurityShield
import org.meshtastic.core.ui.icon.Wifi
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.DeviceMetadata

enum class ConfigRoute(val title: StringResource, val route: Route, val icon: ImageVector? = null, val type: Int = 0) {
    USER(Res.string.user, SettingsRoutes.User, MeshtasticIcons.Person, 0),
    CHANNELS(Res.string.channels, SettingsRoutes.ChannelConfig, MeshtasticIcons.ConfigChannels, 0),
    DEVICE(
        Res.string.device,
        SettingsRoutes.Device,
        MeshtasticIcons.HardwareModel,
        AdminMessage.ConfigType.DEVICE_CONFIG.value,
    ),
    POSITION(
        Res.string.position,
        SettingsRoutes.Position,
        MeshtasticIcons.LocationOn,
        AdminMessage.ConfigType.POSITION_CONFIG.value,
    ),
    POWER(
        Res.string.power,
        SettingsRoutes.Power,
        MeshtasticIcons.PowerSupply,
        AdminMessage.ConfigType.POWER_CONFIG.value,
    ),
    NETWORK(
        Res.string.network,
        SettingsRoutes.Network,
        MeshtasticIcons.Wifi,
        AdminMessage.ConfigType.NETWORK_CONFIG.value,
    ),
    DISPLAY(
        Res.string.display,
        SettingsRoutes.Display,
        MeshtasticIcons.DisplaySettings,
        AdminMessage.ConfigType.DISPLAY_CONFIG.value,
    ),
    LORA(Res.string.lora, SettingsRoutes.LoRa, MeshtasticIcons.CellTower, AdminMessage.ConfigType.LORA_CONFIG.value),
    BLUETOOTH(
        Res.string.bluetooth,
        SettingsRoutes.Bluetooth,
        MeshtasticIcons.Bluetooth,
        AdminMessage.ConfigType.BLUETOOTH_CONFIG.value,
    ),
    SECURITY(
        Res.string.security,
        SettingsRoutes.Security,
        MeshtasticIcons.SecurityShield,
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

        val radioConfigRoutes = listOf(USER, LORA, CHANNELS, SECURITY)

        fun deviceConfigRoutes(metadata: DeviceMetadata?): List<ConfigRoute> =
            filterExcludedFrom(metadata) - radioConfigRoutes
    }
}
