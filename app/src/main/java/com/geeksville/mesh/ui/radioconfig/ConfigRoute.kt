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
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.R
import com.geeksville.mesh.navigation.Route

@Suppress("MagicNumber")
// Config (type = AdminProtos.AdminMessage.ConfigType)
enum class ConfigRoute(@StringRes val title: Int, val route: Route, val icon: ImageVector?, val type: Int = 0) {
    USER(R.string.user, Route.User, Icons.Default.Person, 0),
    CHANNELS(R.string.channels, Route.ChannelConfig, Icons.AutoMirrored.Default.List, 0),
    DEVICE(R.string.device, Route.Device, Icons.Default.Router, 0),
    POSITION(R.string.position, Route.Position, Icons.Default.LocationOn, 1),
    POWER(R.string.power, Route.Power, Icons.Default.Power, 2),
    NETWORK(R.string.network, Route.Network, Icons.Default.Wifi, 3),
    DISPLAY(R.string.display, Route.Display, Icons.Default.DisplaySettings, 4),
    LORA(R.string.lora, Route.LoRa, Icons.Default.CellTower, 5),
    BLUETOOTH(R.string.bluetooth, Route.Bluetooth, Icons.Default.Bluetooth, 6),
    SECURITY(R.string.security, Route.Security, Icons.Default.Security, 7),
    ;

    companion object {
        fun filterExcludedFrom(metadata: DeviceMetadata?): List<ConfigRoute> = entries.filter {
            when {
                metadata == null -> true
                it == BLUETOOTH -> metadata.hasBluetooth
                it == NETWORK -> metadata.hasWifi || metadata.hasEthernet
                else -> true // Include all other routes by default
            }
        }
    }
}
