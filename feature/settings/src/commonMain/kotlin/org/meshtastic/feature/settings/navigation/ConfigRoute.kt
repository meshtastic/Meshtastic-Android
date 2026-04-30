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
package org.meshtastic.feature.settings.navigation

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.device
import org.meshtastic.core.resources.display
import org.meshtastic.core.resources.ic_bluetooth
import org.meshtastic.core.resources.ic_cell_tower
import org.meshtastic.core.resources.ic_display_settings
import org.meshtastic.core.resources.ic_list
import org.meshtastic.core.resources.ic_location_on
import org.meshtastic.core.resources.ic_person
import org.meshtastic.core.resources.ic_power
import org.meshtastic.core.resources.ic_router
import org.meshtastic.core.resources.ic_security
import org.meshtastic.core.resources.ic_wifi
import org.meshtastic.core.resources.lora
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.position
import org.meshtastic.core.resources.power
import org.meshtastic.core.resources.security
import org.meshtastic.core.resources.user
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.DeviceMetadata

enum class ConfigRoute(
    val title: StringResource,
    val route: Route,
    val icon: DrawableResource? = null,
    val type: Int = 0,
) {
    USER(Res.string.user, SettingsRoute.User, Res.drawable.ic_person, 0),
    CHANNELS(Res.string.channels, SettingsRoute.ChannelConfig, Res.drawable.ic_list, 0),
    DEVICE(
        Res.string.device,
        SettingsRoute.Device,
        Res.drawable.ic_router,
        AdminMessage.ConfigType.DEVICE_CONFIG.value,
    ),
    POSITION(
        Res.string.position,
        SettingsRoute.Position,
        Res.drawable.ic_location_on,
        AdminMessage.ConfigType.POSITION_CONFIG.value,
    ),
    POWER(Res.string.power, SettingsRoute.Power, Res.drawable.ic_power, AdminMessage.ConfigType.POWER_CONFIG.value),
    NETWORK(
        Res.string.network,
        SettingsRoute.Network,
        Res.drawable.ic_wifi,
        AdminMessage.ConfigType.NETWORK_CONFIG.value,
    ),
    DISPLAY(
        Res.string.display,
        SettingsRoute.Display,
        Res.drawable.ic_display_settings,
        AdminMessage.ConfigType.DISPLAY_CONFIG.value,
    ),
    LORA(Res.string.lora, SettingsRoute.LoRa, Res.drawable.ic_cell_tower, AdminMessage.ConfigType.LORA_CONFIG.value),
    BLUETOOTH(
        Res.string.bluetooth,
        SettingsRoute.Bluetooth,
        Res.drawable.ic_bluetooth,
        AdminMessage.ConfigType.BLUETOOTH_CONFIG.value,
    ),
    SECURITY(
        Res.string.security,
        SettingsRoute.Security,
        Res.drawable.ic_security,
        AdminMessage.ConfigType.SECURITY_CONFIG.value,
    ),
    ;

    companion object {
        private fun filterExcludedFrom(metadata: DeviceMetadata?): List<ConfigRoute> = entries.filter {
            when {
                metadata == null -> true

                // Include all routes if metadata is null
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
