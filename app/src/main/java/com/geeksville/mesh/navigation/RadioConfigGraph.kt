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

package com.geeksville.mesh.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PermScanWifi
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.radioconfig.RadioConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.AmbientLightingConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.AudioConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.BluetoothConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.CannedMessageConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.ChannelConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.DetectionSensorConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.DeviceConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.DisplayConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.ExternalNotificationConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.LoRaConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.MQTTConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.NeighborInfoConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.NetworkConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.PaxcounterConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.PositionConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.PowerConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.RangeTestConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.RemoteHardwareConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.SecurityConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.SerialConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.StoreForwardConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.TelemetryConfigScreen
import com.geeksville.mesh.ui.radioconfig.components.UserConfigScreen

fun NavGraphBuilder.radioConfigGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<Graph.RadioConfigGraph>(
        startDestination = Route.RadioConfig(),
    ) {
        composable<Route.RadioConfig> { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<Graph.RadioConfigGraph>()
            }
            RadioConfigScreen(
                uiViewModel = uiViewModel,
                viewModel = hiltViewModel(parentEntry)
            ) {
                navController.navigate(it) {
                    popUpTo(Route.RadioConfig()) {
                        inclusive = false
                    }
                }
            }
        }
        configRoutes(navController)
        moduleRoutes(navController)
    }
}

private fun NavGraphBuilder.configRoutes(
    navController: NavHostController,
) {
    ConfigRoute.entries.forEach { configRoute ->
        composable(configRoute.route::class) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<Graph.RadioConfigGraph>()
            }
            when (configRoute) {
                ConfigRoute.USER -> UserConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.CHANNELS -> ChannelConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.DEVICE -> DeviceConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.POSITION -> PositionConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.POWER -> PowerConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.NETWORK -> NetworkConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.DISPLAY -> DisplayConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.LORA -> LoRaConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.BLUETOOTH -> BluetoothConfigScreen(hiltViewModel(parentEntry))
                ConfigRoute.SECURITY -> SecurityConfigScreen(hiltViewModel(parentEntry))
            }
        }
    }
}

@Suppress("CyclomaticComplexMethod")
private fun NavGraphBuilder.moduleRoutes(
    navController: NavHostController,
) {
    ModuleRoute.entries.forEach { moduleRoute ->
        composable(moduleRoute.route::class) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry<Graph.RadioConfigGraph>()
            }
            when (moduleRoute) {
                ModuleRoute.MQTT -> MQTTConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.SERIAL -> SerialConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.EXT_NOTIFICATION -> ExternalNotificationConfigScreen(
                    hiltViewModel(parentEntry)
                )

                ModuleRoute.STORE_FORWARD -> StoreForwardConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.RANGE_TEST -> RangeTestConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.TELEMETRY -> TelemetryConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.CANNED_MESSAGE -> CannedMessageConfigScreen(
                    hiltViewModel(parentEntry)
                )

                ModuleRoute.AUDIO -> AudioConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.REMOTE_HARDWARE -> RemoteHardwareConfigScreen(
                    hiltViewModel(parentEntry)
                )

                ModuleRoute.NEIGHBOR_INFO -> NeighborInfoConfigScreen(hiltViewModel(parentEntry))
                ModuleRoute.AMBIENT_LIGHTING -> AmbientLightingConfigScreen(
                    hiltViewModel(parentEntry)
                )

                ModuleRoute.DETECTION_SENSOR -> DetectionSensorConfigScreen(
                    hiltViewModel(parentEntry)
                )

                ModuleRoute.PAXCOUNTER -> PaxcounterConfigScreen(hiltViewModel(parentEntry))
            }
        }
    }
}

// Config (type = AdminProtos.AdminMessage.ConfigType)
@Suppress("MagicNumber")
enum class ConfigRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
    val type: Int = 0
) {
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

// ModuleConfig (type = AdminProtos.AdminMessage.ModuleConfigType)
@Suppress("MagicNumber")
enum class ModuleRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
    val type: Int = 0
) {
    MQTT(R.string.mqtt, Route.MQTT, Icons.Default.Cloud, 0),
    SERIAL(R.string.serial, Route.Serial, Icons.Default.Usb, 1),
    EXT_NOTIFICATION(
        R.string.external_notification,
        Route.ExtNotification,
        Icons.Default.Notifications,
        2
    ),
    STORE_FORWARD(
        R.string.store_forward,
        Route.StoreForward,
        Icons.AutoMirrored.Default.Forward,
        3
    ),
    RANGE_TEST(R.string.range_test, Route.RangeTest, Icons.Default.Speed, 4),
    TELEMETRY(R.string.telemetry, Route.Telemetry, Icons.Default.DataUsage, 5),
    CANNED_MESSAGE(
        R.string.canned_message,
        Route.CannedMessage,
        Icons.AutoMirrored.Default.Message,
        6
    ),
    AUDIO(R.string.audio, Route.Audio, Icons.AutoMirrored.Default.VolumeUp, 7),
    REMOTE_HARDWARE(
        R.string.remote_hardware,
        Route.RemoteHardware,
        Icons.Default.SettingsRemote,
        8
    ),
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
