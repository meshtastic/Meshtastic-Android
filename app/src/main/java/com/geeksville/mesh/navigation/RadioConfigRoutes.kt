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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.radioconfig.CleanNodeDatabaseScreen
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
import kotlinx.serialization.Serializable

sealed class RadioConfigRoutes {
    @Serializable data class RadioConfigGraph(val destNum: Int? = null) : Graph

    @Serializable data class RadioConfig(val destNum: Int? = null) : Route

    @Serializable data object User : Route

    @Serializable data object ChannelConfig : Route

    @Serializable data object Device : Route

    @Serializable data object Position : Route

    @Serializable data object Power : Route

    @Serializable data object Network : Route

    @Serializable data object Display : Route

    @Serializable data object LoRa : Route

    @Serializable data object Bluetooth : Route

    @Serializable data object Security : Route

    @Serializable data object MQTT : Route

    @Serializable data object Serial : Route

    @Serializable data object ExtNotification : Route

    @Serializable data object StoreForward : Route

    @Serializable data object RangeTest : Route

    @Serializable data object Telemetry : Route

    @Serializable data object CannedMessage : Route

    @Serializable data object Audio : Route

    @Serializable data object RemoteHardware : Route

    @Serializable data object NeighborInfo : Route

    @Serializable data object AmbientLighting : Route

    @Serializable data object DetectionSensor : Route

    @Serializable data object Paxcounter : Route

    @Serializable data object CleanNodeDb : Route
}

fun getNavRouteFrom(routeName: String): Route? =
    ConfigRoute.entries.find { it.name == routeName }?.route ?: ModuleRoute.entries.find { it.name == routeName }?.route

@Suppress("LongMethod")
fun NavGraphBuilder.radioConfigGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<RadioConfigRoutes.RadioConfigGraph>(startDestination = RadioConfigRoutes.RadioConfig()) {
        composable<RadioConfigRoutes.RadioConfig>(
            deepLinks =
            listOf(navDeepLink<RadioConfigRoutes.RadioConfig>(basePath = "$DEEP_LINK_BASE_URI/radio_config")),
        ) { backStackEntry ->
            val parentEntry =
                remember(backStackEntry) { navController.getBackStackEntry(RadioConfigRoutes.RadioConfigGraph::class) }
            RadioConfigScreen(uiViewModel = uiViewModel, viewModel = hiltViewModel(parentEntry)) {
                navController.navigate(it) { popUpTo(RadioConfigRoutes.RadioConfig()) { inclusive = false } }
            }
        }
        composable<RadioConfigRoutes.CleanNodeDb>(
            deepLinks =
            listOf(
                navDeepLink<RadioConfigRoutes.CleanNodeDb>(
                    basePath = "$DEEP_LINK_BASE_URI/radio_config/clean_node_db",
                ),
            ),
        ) {
            CleanNodeDatabaseScreen()
        }
        configRoutesScreens(navController)
        moduleRoutesScreens(navController)
    }
}

/**
 * Helper to define a composable route for a radio configuration screen within the radio config graph.
 *
 * This function simplifies adding screens by handling common tasks like:
 * - Setting up deep links based on the route's name.
 * - Retrieving the parent [NavBackStackEntry] for the [RadioConfigRoutes.RadioConfigGraph].
 * - Providing the [RadioConfigViewModel] scoped to the parent graph, which the [screenContent] will use.
 *
 * @param R The type of the [Route] object, must be serializable.
 * @param navController The [NavHostController] for navigation.
 * @param routeNameString The string name of the route (from the enum entry's name) used for deep link paths.
 * @param screenContent A lambda that defines the composable content for the screen. It receives the parent-scoped
 *   [RadioConfigViewModel].
 */
private inline fun <reified R : Route> NavGraphBuilder.addRadioConfigScreenComposable(
    navController: NavHostController,
    routeNameString: String,
    crossinline screenContent: @Composable (viewModel: RadioConfigViewModel) -> Unit,
) {
    composable<R>(
        deepLinks =
        listOf(
            navDeepLink<R>(basePath = "$DEEP_LINK_BASE_URI/radio_config/{destNum}/${routeNameString.lowercase()}"),
            navDeepLink<R>(basePath = "$DEEP_LINK_BASE_URI/radio_config/${routeNameString.lowercase()}"),
        ),
    ) { backStackEntry ->
        val parentEntry =
            remember(backStackEntry) { navController.getBackStackEntry(RadioConfigRoutes.RadioConfigGraph::class) }
        val viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry)
        screenContent(viewModel)
    }
}

@Suppress("LongMethod")
private fun NavGraphBuilder.configRoutesScreens(navController: NavHostController) {
    ConfigRoute.entries.forEach { entry ->
        when (entry.route) {
            is RadioConfigRoutes.User ->
                addRadioConfigScreenComposable<RadioConfigRoutes.User>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.ChannelConfig ->
                addRadioConfigScreenComposable<RadioConfigRoutes.ChannelConfig>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Device ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Device>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Position ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Position>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Power ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Power>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Network ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Network>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Display ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Display>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.LoRa ->
                addRadioConfigScreenComposable<RadioConfigRoutes.LoRa>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Bluetooth ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Bluetooth>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Security ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Security>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            else -> Unit // Should not happen if ConfigRoute enum is exhaustive for this context
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun NavGraphBuilder.moduleRoutesScreens(navController: NavHostController) {
    ModuleRoute.entries.forEach { entry ->
        when (entry.route) {
            is RadioConfigRoutes.MQTT ->
                addRadioConfigScreenComposable<RadioConfigRoutes.MQTT>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Serial ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Serial>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.ExtNotification ->
                addRadioConfigScreenComposable<RadioConfigRoutes.ExtNotification>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.StoreForward ->
                addRadioConfigScreenComposable<RadioConfigRoutes.StoreForward>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.RangeTest ->
                addRadioConfigScreenComposable<RadioConfigRoutes.RangeTest>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Telemetry ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Telemetry>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.CannedMessage ->
                addRadioConfigScreenComposable<RadioConfigRoutes.CannedMessage>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Audio ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Audio>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.RemoteHardware ->
                addRadioConfigScreenComposable<RadioConfigRoutes.RemoteHardware>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.NeighborInfo ->
                addRadioConfigScreenComposable<RadioConfigRoutes.NeighborInfo>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.AmbientLighting ->
                addRadioConfigScreenComposable<RadioConfigRoutes.AmbientLighting>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.DetectionSensor ->
                addRadioConfigScreenComposable<RadioConfigRoutes.DetectionSensor>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is RadioConfigRoutes.Paxcounter ->
                addRadioConfigScreenComposable<RadioConfigRoutes.Paxcounter>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            else -> Unit // Should not happen if ModuleRoute enum is exhaustive for this context
        }
    }
}

@Suppress("MagicNumber")
enum class ConfigRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
    val type: Int = 0,
    val screenComposable: @Composable (viewModel: RadioConfigViewModel) -> Unit,
) {
    USER(R.string.user, RadioConfigRoutes.User, Icons.Default.Person, 0, { vm -> UserConfigScreen(vm) }),
    CHANNELS(
        R.string.channels,
        RadioConfigRoutes.ChannelConfig,
        Icons.AutoMirrored.Default.List,
        0,
        { vm -> ChannelConfigScreen(vm) },
    ),
    DEVICE(R.string.device, RadioConfigRoutes.Device, Icons.Default.Router, 0, { vm -> DeviceConfigScreen(vm) }),
    POSITION(
        R.string.position,
        RadioConfigRoutes.Position,
        Icons.Default.LocationOn,
        1,
        { vm -> PositionConfigScreen(vm) },
    ),
    POWER(R.string.power, RadioConfigRoutes.Power, Icons.Default.Power, 2, { vm -> PowerConfigScreen(vm) }),
    NETWORK(R.string.network, RadioConfigRoutes.Network, Icons.Default.Wifi, 3, { vm -> NetworkConfigScreen(vm) }),
    DISPLAY(
        R.string.display,
        RadioConfigRoutes.Display,
        Icons.Default.DisplaySettings,
        4,
        { vm -> DisplayConfigScreen(vm) },
    ),
    LORA(R.string.lora, RadioConfigRoutes.LoRa, Icons.Default.CellTower, 5, { vm -> LoRaConfigScreen(vm) }),
    BLUETOOTH(
        R.string.bluetooth,
        RadioConfigRoutes.Bluetooth,
        Icons.Default.Bluetooth,
        6,
        { vm -> BluetoothConfigScreen(vm) },
    ),
    SECURITY(
        R.string.security,
        RadioConfigRoutes.Security,
        Icons.Default.Security,
        7,
        { vm -> SecurityConfigScreen(vm) },
    ),
    ;

    companion object {
        fun filterExcludedFrom(metadata: DeviceMetadata?): List<ConfigRoute> = entries.filter {
            when {
                metadata == null -> true // Include all routes if metadata is null
                it == BLUETOOTH -> metadata.hasBluetooth
                it == NETWORK -> metadata.hasWifi || metadata.hasEthernet
                else -> true // Include all other routes by default
            }
        }
    }
}

@Suppress("MagicNumber")
enum class ModuleRoute(
    @StringRes val title: Int,
    val route: Route,
    val icon: ImageVector?,
    val type: Int = 0,
    val screenComposable: @Composable (viewModel: RadioConfigViewModel) -> Unit,
) {
    MQTT(R.string.mqtt, RadioConfigRoutes.MQTT, Icons.Default.Cloud, 0, { vm -> MQTTConfigScreen(vm) }),
    SERIAL(R.string.serial, RadioConfigRoutes.Serial, Icons.Default.Usb, 1, { vm -> SerialConfigScreen(vm) }),
    EXT_NOTIFICATION(
        R.string.external_notification,
        RadioConfigRoutes.ExtNotification,
        Icons.Default.Notifications,
        2,
        { vm -> ExternalNotificationConfigScreen(vm) },
    ),
    STORE_FORWARD(
        R.string.store_forward,
        RadioConfigRoutes.StoreForward,
        Icons.AutoMirrored.Default.Forward,
        3,
        { vm -> StoreForwardConfigScreen(vm) },
    ),
    RANGE_TEST(
        R.string.range_test,
        RadioConfigRoutes.RangeTest,
        Icons.Default.Speed,
        4,
        { vm -> RangeTestConfigScreen(vm) },
    ),
    TELEMETRY(
        R.string.telemetry,
        RadioConfigRoutes.Telemetry,
        Icons.Default.DataUsage,
        5,
        { vm -> TelemetryConfigScreen(vm) },
    ),
    CANNED_MESSAGE(
        R.string.canned_message,
        RadioConfigRoutes.CannedMessage,
        Icons.AutoMirrored.Default.Message,
        6,
        { vm -> CannedMessageConfigScreen(vm) },
    ),
    AUDIO(
        R.string.audio,
        RadioConfigRoutes.Audio,
        Icons.AutoMirrored.Default.VolumeUp,
        7,
        { vm -> AudioConfigScreen(vm) },
    ),
    REMOTE_HARDWARE(
        R.string.remote_hardware,
        RadioConfigRoutes.RemoteHardware,
        Icons.Default.SettingsRemote,
        8,
        { vm -> RemoteHardwareConfigScreen(vm) },
    ),
    NEIGHBOR_INFO(
        R.string.neighbor_info,
        RadioConfigRoutes.NeighborInfo,
        Icons.Default.People,
        9,
        { vm -> NeighborInfoConfigScreen(vm) },
    ),
    AMBIENT_LIGHTING(
        R.string.ambient_lighting,
        RadioConfigRoutes.AmbientLighting,
        Icons.Default.LightMode,
        10,
        { vm -> AmbientLightingConfigScreen(vm) },
    ),
    DETECTION_SENSOR(
        R.string.detection_sensor,
        RadioConfigRoutes.DetectionSensor,
        Icons.Default.Sensors,
        11,
        { vm -> DetectionSensorConfigScreen(vm) },
    ),
    PAXCOUNTER(
        R.string.paxcounter,
        RadioConfigRoutes.Paxcounter,
        Icons.Default.PermScanWifi,
        12,
        { vm -> PaxcounterConfigScreen(vm) },
    ),
    ;

    val bitfield: Int
        get() = 1 shl ordinal

    companion object {
        fun filterExcludedFrom(metadata: DeviceMetadata?): List<ModuleRoute> = entries.filter {
            when (metadata) {
                null -> true // Include all routes if metadata is null
                else -> metadata.excludedModules and it.bitfield == 0
            }
        }
    }
}
