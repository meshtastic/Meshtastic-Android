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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.compose.AndroidFragment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.R
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.ChannelScreen
import com.geeksville.mesh.ui.ContactsScreen
import com.geeksville.mesh.ui.DebugScreen
import com.geeksville.mesh.ui.NodeDetailScreen
import com.geeksville.mesh.ui.NodeScreen
import com.geeksville.mesh.ui.QuickChatScreen
import com.geeksville.mesh.ui.SettingsFragment
import com.geeksville.mesh.ui.ShareScreen
import com.geeksville.mesh.ui.components.DeviceMetricsScreen
import com.geeksville.mesh.ui.components.EnvironmentMetricsScreen
import com.geeksville.mesh.ui.components.NodeMapScreen
import com.geeksville.mesh.ui.components.PositionLogScreen
import com.geeksville.mesh.ui.components.SignalMetricsScreen
import com.geeksville.mesh.ui.components.TracerouteLogScreen
import com.geeksville.mesh.ui.map.MapView
import com.geeksville.mesh.ui.message.MessageScreen
import com.geeksville.mesh.ui.radioconfig.RadioConfigScreen
import com.geeksville.mesh.ui.radioconfig.RadioConfigViewModel
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
import com.geeksville.mesh.util.UiText
import kotlinx.serialization.Serializable

enum class AdminRoute(@StringRes val title: Int) {
    REBOOT(R.string.reboot),
    SHUTDOWN(R.string.shutdown),
    FACTORY_RESET(R.string.factory_reset),
    NODEDB_RESET(R.string.nodedb_reset),
}

sealed interface Route {
    @Serializable
    data object Contacts : Route
    @Serializable
    data object Nodes : Route
    @Serializable
    data object Map : Route
    @Serializable
    data object Channels : Route
    @Serializable
    data object Settings : Route

    @Serializable
    data object DebugPanel : Route

    @Serializable
    data class Messages(val contactKey: String, val message: String = "") : Route
    @Serializable
    data object QuickChat : Route

    @Serializable
    data class Share(val message: String) : Route

    @Serializable
    data class RadioConfig(val destNum: Int? = null) : Route
    @Serializable
    data object User : Route
    @Serializable
    data object ChannelConfig : Route
    @Serializable
    data object Device : Route
    @Serializable
    data object Position : Route
    @Serializable
    data object Power : Route
    @Serializable
    data object Network : Route
    @Serializable
    data object Display : Route
    @Serializable
    data object LoRa : Route
    @Serializable
    data object Bluetooth : Route
    @Serializable
    data object Security : Route

    @Serializable
    data object MQTT : Route
    @Serializable
    data object Serial : Route
    @Serializable
    data object ExtNotification : Route
    @Serializable
    data object StoreForward : Route
    @Serializable
    data object RangeTest : Route
    @Serializable
    data object Telemetry : Route
    @Serializable
    data object CannedMessage : Route
    @Serializable
    data object Audio : Route
    @Serializable
    data object RemoteHardware : Route
    @Serializable
    data object NeighborInfo : Route
    @Serializable
    data object AmbientLighting : Route
    @Serializable
    data object DetectionSensor : Route
    @Serializable
    data object Paxcounter : Route

    @Serializable
    data class NodeDetail(val destNum: Int) : Route
    @Serializable
    data object DeviceMetrics : Route
    @Serializable
    data object NodeMap : Route
    @Serializable
    data object PositionLog : Route
    @Serializable
    data object EnvironmentMetrics : Route
    @Serializable
    data object SignalMetrics : Route
    @Serializable
    data object PowerMetrics : Route
    @Serializable
    data object TracerouteLog : Route
}

// Config (type = AdminProtos.AdminMessage.ConfigType)
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

/**
 * Generic sealed class defines each possible state of a response.
 */
sealed class ResponseState<out T> {
    data object Empty : ResponseState<Nothing>()
    data class Loading(var total: Int = 1, var completed: Int = 0) : ResponseState<Nothing>()
    data class Success<T>(val result: T) : ResponseState<T>()
    data class Error(val error: UiText) : ResponseState<Nothing>()

    fun isWaiting() = this !is Empty
}

@Suppress("LongMethod")
@Composable
fun NavGraph(
    model: UIViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.Contacts,
        modifier = modifier,
    ) {
        composable<Route.Contacts> {
            ContactsScreen(model, onNavigate = { navController.navigate(Route.Messages(it)) })
        }
        composable<Route.Nodes> {
            NodeScreen(
                model = model,
                navigateToMessages = { navController.navigate(Route.Messages(it)) },
                navigateToNodeDetails = { navController.navigate(Route.NodeDetail(it)) },
            )
        }
        composable<Route.Map> {
            MapView(model)
        }
        composable<Route.Channels> {
            ChannelScreen(model)
        }
        composable<Route.Settings> {
            AndroidFragment<SettingsFragment>(Modifier.fillMaxSize())
        }
        composable<Route.DebugPanel> {
            DebugScreen()
        }
        composable<Route.Messages> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.Messages>()
            MessageScreen(
                contactKey = args.contactKey,
                message = args.message,
                viewModel = model,
                navigateToMessages = { navController.navigate(Route.Messages(it)) },
                navigateToNodeDetails = { navController.navigate(Route.NodeDetail(it)) },
                onNavigateBack = navController::navigateUp
            )
        }
        composable<Route.QuickChat> {
            QuickChatScreen()
        }
        composable<Route.NodeDetail> {
            NodeDetailScreen { navController.navigate(route = it) }
        }
        composable<Route.DeviceMetrics> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            DeviceMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.NodeMap> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            NodeMapScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.PositionLog> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            PositionLogScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.EnvironmentMetrics> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            EnvironmentMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.SignalMetrics> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            SignalMetricsScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.TracerouteLog> {
            val parentEntry = remember { navController.getBackStackEntry<Route.NodeDetail>() }
            TracerouteLogScreen(hiltViewModel<MetricsViewModel>(parentEntry))
        }
        composable<Route.RadioConfig> {
            RadioConfigScreen { navController.navigate(route = it) }
        }
        composable<Route.User> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            UserConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.ChannelConfig> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            ChannelConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Device> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            DeviceConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Position> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            PositionConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Power> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            PowerConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Network> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            NetworkConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Display> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            DisplayConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.LoRa> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            LoRaConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Bluetooth> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            BluetoothConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Security> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            SecurityConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.MQTT> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            MQTTConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Serial> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            SerialConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.ExtNotification> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            ExternalNotificationConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.StoreForward> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            StoreForwardConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.RangeTest> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            RangeTestConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Telemetry> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            TelemetryConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.CannedMessage> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            CannedMessageConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Audio> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            AudioConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.RemoteHardware> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            RemoteHardwareConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.NeighborInfo> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            NeighborInfoConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.AmbientLighting> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            AmbientLightingConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.DetectionSensor> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            DetectionSensorConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Paxcounter> {
            val parentEntry = remember { navController.getBackStackEntry<Route.RadioConfig>() }
            PaxcounterConfigScreen(hiltViewModel<RadioConfigViewModel>(parentEntry))
        }
        composable<Route.Share> { backStackEntry ->
            val message = backStackEntry.toRoute<Route.Share>().message
            ShareScreen(model) {
                navController.navigate(Route.Messages(it, message)) {
                    popUpTo<Route.Share> { inclusive = true }
                }
            }
        }
    }
}
