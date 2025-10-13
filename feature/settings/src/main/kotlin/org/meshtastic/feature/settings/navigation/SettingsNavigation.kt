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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.strings.R
import org.meshtastic.feature.settings.SettingsScreen
import org.meshtastic.feature.settings.debugging.DebugScreen
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseScreen
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.AmbientLightingConfigScreen
import org.meshtastic.feature.settings.radio.component.AudioConfigScreen
import org.meshtastic.feature.settings.radio.component.BluetoothConfigScreen
import org.meshtastic.feature.settings.radio.component.CannedMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.DetectionSensorConfigScreen
import org.meshtastic.feature.settings.radio.component.DeviceConfigScreen
import org.meshtastic.feature.settings.radio.component.DisplayConfigScreen
import org.meshtastic.feature.settings.radio.component.ExternalNotificationConfigScreen
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen
import org.meshtastic.feature.settings.radio.component.MQTTConfigScreen
import org.meshtastic.feature.settings.radio.component.NeighborInfoConfigScreen
import org.meshtastic.feature.settings.radio.component.NetworkConfigScreen
import org.meshtastic.feature.settings.radio.component.PaxcounterConfigScreen
import org.meshtastic.feature.settings.radio.component.PositionConfigScreen
import org.meshtastic.feature.settings.radio.component.PowerConfigScreen
import org.meshtastic.feature.settings.radio.component.RangeTestConfigScreen
import org.meshtastic.feature.settings.radio.component.RemoteHardwareConfigScreen
import org.meshtastic.feature.settings.radio.component.SecurityConfigScreen
import org.meshtastic.feature.settings.radio.component.SerialConfigScreen
import org.meshtastic.feature.settings.radio.component.StoreForwardConfigScreen
import org.meshtastic.feature.settings.radio.component.TelemetryConfigScreen
import org.meshtastic.feature.settings.radio.component.UserConfigScreen
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.MeshProtos.DeviceMetadata
import kotlin.reflect.KClass

fun getNavRouteFrom(routeName: String): Route? =
    ConfigRoute.entries.find { it.name == routeName }?.route ?: ModuleRoute.entries.find { it.name == routeName }?.route

@Suppress("LongMethod")
fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    navigation<SettingsRoutes.SettingsGraph>(startDestination = SettingsRoutes.Settings()) {
        composable<SettingsRoutes.Settings>(
            deepLinks = listOf(navDeepLink<SettingsRoutes.Settings>(basePath = "$DEEP_LINK_BASE_URI/settings")),
        ) { backStackEntry ->
            val parentEntry =
                remember(backStackEntry) { navController.getBackStackEntry(SettingsRoutes.SettingsGraph::class) }
            SettingsScreen(
                viewModel = hiltViewModel(parentEntry),
                onClickNodeChip = {
                    navController.navigate(NodesRoutes.NodeDetailGraph(it)) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            ) {
                navController.navigate(it) { popUpTo(SettingsRoutes.Settings()) { inclusive = false } }
            }
        }
        composable<SettingsRoutes.CleanNodeDb>(
            deepLinks =
            listOf(
                navDeepLink<SettingsRoutes.CleanNodeDb>(
                    basePath = "$DEEP_LINK_BASE_URI/settings/radio/clean_node_db",
                ),
            ),
        ) {
            CleanNodeDatabaseScreen()
        }
        configRoutesScreens(navController)
        moduleRoutesScreens(navController)
        composable<SettingsRoutes.DebugPanel>(
            deepLinks =
            listOf(navDeepLink<SettingsRoutes.DebugPanel>(basePath = "$DEEP_LINK_BASE_URI/settings/debug_panel")),
        ) {
            DebugScreen(onNavigateUp = navController::navigateUp)
        }
    }
}

fun NavDestination.isConfigRoute(): Boolean =
    ConfigRoute.entries.any { hasRoute(it.route::class) } || ModuleRoute.entries.any { hasRoute(it.route::class) }

/**
 * Helper to define a composable route for a radio configuration screen within the radio config graph.
 *
 * This function simplifies adding screens by handling common tasks like:
 * - Setting up deep links based on the route's name.
 * - Retrieving the parent [NavBackStackEntry] for the [SettingsRoutes.SettingsGraph].
 * - Providing the [RadioConfigViewModel] scoped to the parent graph, which the [screenContent] will use.
 *
 * @param route The [Route] class.
 * @param navController The [NavHostController] for navigation.
 * @param routeNameString The string name of the route (from the enum entry's name) used for deep link paths.
 * @param screenContent A lambda that defines the composable content for the screen. It receives the parent-scoped
 *   [RadioConfigViewModel].
 */
private fun <R : Route> NavGraphBuilder.addRadioConfigScreenComposable(
    route: KClass<R>,
    navController: NavHostController,
    routeNameString: String,
    screenContent: @Composable (viewModel: RadioConfigViewModel) -> Unit,
) {
    composable(
        route = route,
        deepLinks =
        listOf(
            navDeepLink(
                route = route,
                basePath = "$DEEP_LINK_BASE_URI/settings/radio/{destNum}/${routeNameString.lowercase()}",
            ) {},
            navDeepLink(
                route = route,
                basePath = "$DEEP_LINK_BASE_URI/settings/radio/${routeNameString.lowercase()}",
            ) {},
        ),
    ) { backStackEntry ->
        val parentEntry =
            remember(backStackEntry) { navController.getBackStackEntry(SettingsRoutes.SettingsGraph::class) }
        val viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry)
        screenContent(viewModel)
    }
}

@Suppress("LongMethod")
private fun NavGraphBuilder.configRoutesScreens(navController: NavHostController) {
    ConfigRoute.entries.forEach { entry ->
        when (entry) {
            ConfigRoute.USER ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    UserConfigScreen(navController, it)
                }

            ConfigRoute.CHANNELS ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    ChannelConfigScreen(navController, it)
                }

            ConfigRoute.DEVICE ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    DeviceConfigScreen(navController, it)
                }

            ConfigRoute.POSITION ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    PositionConfigScreen(navController, it)
                }

            ConfigRoute.POWER ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    PowerConfigScreen(navController, it)
                }

            ConfigRoute.NETWORK ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    NetworkConfigScreen(navController, it)
                }

            ConfigRoute.DISPLAY ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    DisplayConfigScreen(navController, it)
                }

            ConfigRoute.LORA ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    LoRaConfigScreen(navController, it)
                }

            ConfigRoute.BLUETOOTH ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    BluetoothConfigScreen(navController, it)
                }

            ConfigRoute.SECURITY ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    SecurityConfigScreen(navController, it)
                }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun NavGraphBuilder.moduleRoutesScreens(navController: NavHostController) {
    ModuleRoute.entries.forEach { entry ->
        when (entry) {
            ModuleRoute.MQTT ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    MQTTConfigScreen(navController, it)
                }

            ModuleRoute.SERIAL ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    SerialConfigScreen(navController, it)
                }

            ModuleRoute.EXT_NOTIFICATION ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    ExternalNotificationConfigScreen(navController, it)
                }

            ModuleRoute.STORE_FORWARD ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    StoreForwardConfigScreen(navController, it)
                }

            ModuleRoute.RANGE_TEST ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    RangeTestConfigScreen(navController, it)
                }

            ModuleRoute.TELEMETRY ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    TelemetryConfigScreen(navController, it)
                }

            ModuleRoute.CANNED_MESSAGE ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    CannedMessageConfigScreen(navController, it)
                }

            ModuleRoute.AUDIO ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    AudioConfigScreen(navController, it)
                }

            ModuleRoute.REMOTE_HARDWARE ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    RemoteHardwareConfigScreen(navController, it)
                }

            ModuleRoute.NEIGHBOR_INFO ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    NeighborInfoConfigScreen(navController, it)
                }

            ModuleRoute.AMBIENT_LIGHTING ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    AmbientLightingConfigScreen(navController, it)
                }

            ModuleRoute.DETECTION_SENSOR ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    DetectionSensorConfigScreen(navController, it)
                }

            ModuleRoute.PAXCOUNTER ->
                addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
                    PaxcounterConfigScreen(navController, it)
                }
        }
    }
}

@Suppress("MagicNumber")
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

@Suppress("MagicNumber")
enum class ModuleRoute(@StringRes val title: Int, val route: Route, val icon: ImageVector?, val type: Int = 0) {
    MQTT(
        R.string.mqtt,
        SettingsRoutes.MQTT,
        Icons.Default.Cloud,
        AdminProtos.AdminMessage.ModuleConfigType.MQTT_CONFIG_VALUE,
    ),
    SERIAL(
        R.string.serial,
        SettingsRoutes.Serial,
        Icons.Default.Usb,
        AdminProtos.AdminMessage.ModuleConfigType.SERIAL_CONFIG_VALUE,
    ),
    EXT_NOTIFICATION(
        R.string.external_notification,
        SettingsRoutes.ExtNotification,
        Icons.Default.Notifications,
        AdminProtos.AdminMessage.ModuleConfigType.EXTNOTIF_CONFIG_VALUE,
    ),
    STORE_FORWARD(
        R.string.store_forward,
        SettingsRoutes.StoreForward,
        Icons.AutoMirrored.Default.Forward,
        AdminProtos.AdminMessage.ModuleConfigType.STOREFORWARD_CONFIG_VALUE,
    ),
    RANGE_TEST(
        R.string.range_test,
        SettingsRoutes.RangeTest,
        Icons.Default.Speed,
        AdminProtos.AdminMessage.ModuleConfigType.RANGETEST_CONFIG_VALUE,
    ),
    TELEMETRY(
        R.string.telemetry,
        SettingsRoutes.Telemetry,
        Icons.Default.DataUsage,
        AdminProtos.AdminMessage.ModuleConfigType.TELEMETRY_CONFIG_VALUE,
    ),
    CANNED_MESSAGE(
        R.string.canned_message,
        SettingsRoutes.CannedMessage,
        Icons.AutoMirrored.Default.Message,
        AdminProtos.AdminMessage.ModuleConfigType.CANNEDMSG_CONFIG_VALUE,
    ),
    AUDIO(
        R.string.audio,
        SettingsRoutes.Audio,
        Icons.AutoMirrored.Default.VolumeUp,
        AdminProtos.AdminMessage.ModuleConfigType.AUDIO_CONFIG_VALUE,
    ),
    REMOTE_HARDWARE(
        R.string.remote_hardware,
        SettingsRoutes.RemoteHardware,
        Icons.Default.SettingsRemote,
        AdminProtos.AdminMessage.ModuleConfigType.REMOTEHARDWARE_CONFIG_VALUE,
    ),
    NEIGHBOR_INFO(
        R.string.neighbor_info,
        SettingsRoutes.NeighborInfo,
        Icons.Default.People,
        AdminProtos.AdminMessage.ModuleConfigType.NEIGHBORINFO_CONFIG_VALUE,
    ),
    AMBIENT_LIGHTING(
        R.string.ambient_lighting,
        SettingsRoutes.AmbientLighting,
        Icons.Default.LightMode,
        AdminProtos.AdminMessage.ModuleConfigType.AMBIENTLIGHTING_CONFIG_VALUE,
    ),
    DETECTION_SENSOR(
        R.string.detection_sensor,
        SettingsRoutes.DetectionSensor,
        Icons.Default.Sensors,
        AdminProtos.AdminMessage.ModuleConfigType.DETECTIONSENSOR_CONFIG_VALUE,
    ),
    PAXCOUNTER(
        R.string.paxcounter,
        SettingsRoutes.Paxcounter,
        Icons.Default.PermScanWifi,
        AdminProtos.AdminMessage.ModuleConfigType.PAXCOUNTER_CONFIG_VALUE,
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
