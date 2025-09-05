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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.MeshProtos.DeviceMetadata
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.debug.DebugScreen
import com.geeksville.mesh.ui.settings.SettingsScreen
import com.geeksville.mesh.ui.settings.radio.CleanNodeDatabaseScreen
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import com.geeksville.mesh.ui.settings.radio.components.AmbientLightingConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.AudioConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.BluetoothConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.CannedMessageConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.ChannelConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.DetectionSensorConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.DeviceConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.DisplayConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.ExternalNotificationConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.LoRaConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.MQTTConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.NeighborInfoConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.NetworkConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.PaxcounterConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.PositionConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.PowerConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.RangeTestConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.RemoteHardwareConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.SecurityConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.SerialConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.StoreForwardConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.TelemetryConfigScreen
import com.geeksville.mesh.ui.settings.radio.components.UserConfigScreen
import kotlinx.serialization.Serializable

sealed class SettingsRoutes {
    @Serializable data class SettingsGraph(val destNum: Int? = null) : Graph

    @Serializable data class Settings(val destNum: Int? = null) : Route

    // region radio Config Routes

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

    // endregion

    // region module config routes

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

    // endregion

    // region advanced config routes

    @Serializable data object CleanNodeDb : Route

    @Serializable data object DebugPanel : Route

    // endregion
}

fun getNavRouteFrom(routeName: String): Route? =
    ConfigRoute.entries.find { it.name == routeName }?.route ?: ModuleRoute.entries.find { it.name == routeName }?.route

@Suppress("LongMethod")
fun NavGraphBuilder.settingsGraph(navController: NavHostController, uiViewModel: UIViewModel) {
    navigation<SettingsRoutes.SettingsGraph>(startDestination = SettingsRoutes.Settings()) {
        composable<SettingsRoutes.Settings>(
            deepLinks = listOf(navDeepLink<SettingsRoutes.Settings>(basePath = "$DEEP_LINK_BASE_URI/settings")),
        ) { backStackEntry ->
            val parentEntry =
                remember(backStackEntry) { navController.getBackStackEntry(SettingsRoutes.SettingsGraph::class) }
            SettingsScreen(uiViewModel = uiViewModel, viewModel = hiltViewModel(parentEntry)) {
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
            DebugScreen()
        }
    }
}

/**
 * Helper to define a composable route for a radio configuration screen within the radio config graph.
 *
 * This function simplifies adding screens by handling common tasks like:
 * - Setting up deep links based on the route's name.
 * - Retrieving the parent [NavBackStackEntry] for the [SettingsRoutes.SettingsGraph].
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
            navDeepLink<R>(
                basePath = "$DEEP_LINK_BASE_URI/settings/radio/{destNum}/${routeNameString.lowercase()}",
            ),
            navDeepLink<R>(basePath = "$DEEP_LINK_BASE_URI/settings/radio/${routeNameString.lowercase()}"),
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
        when (entry.route) {
            is SettingsRoutes.User ->
                addRadioConfigScreenComposable<SettingsRoutes.User>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.ChannelConfig ->
                addRadioConfigScreenComposable<SettingsRoutes.ChannelConfig>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Device ->
                addRadioConfigScreenComposable<SettingsRoutes.Device>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.Position ->
                addRadioConfigScreenComposable<SettingsRoutes.Position>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Power ->
                addRadioConfigScreenComposable<SettingsRoutes.Power>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.Network ->
                addRadioConfigScreenComposable<SettingsRoutes.Network>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Display ->
                addRadioConfigScreenComposable<SettingsRoutes.Display>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.LoRa ->
                addRadioConfigScreenComposable<SettingsRoutes.LoRa>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.Bluetooth ->
                addRadioConfigScreenComposable<SettingsRoutes.Bluetooth>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Security ->
                addRadioConfigScreenComposable<SettingsRoutes.Security>(
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
            is SettingsRoutes.MQTT ->
                addRadioConfigScreenComposable<SettingsRoutes.MQTT>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.Serial ->
                addRadioConfigScreenComposable<SettingsRoutes.Serial>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.ExtNotification ->
                addRadioConfigScreenComposable<SettingsRoutes.ExtNotification>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.StoreForward ->
                addRadioConfigScreenComposable<SettingsRoutes.StoreForward>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.RangeTest ->
                addRadioConfigScreenComposable<SettingsRoutes.RangeTest>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Telemetry ->
                addRadioConfigScreenComposable<SettingsRoutes.Telemetry>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.CannedMessage ->
                addRadioConfigScreenComposable<SettingsRoutes.CannedMessage>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Audio ->
                addRadioConfigScreenComposable<SettingsRoutes.Audio>(navController, entry.name, entry.screenComposable)
            is SettingsRoutes.RemoteHardware ->
                addRadioConfigScreenComposable<SettingsRoutes.RemoteHardware>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.NeighborInfo ->
                addRadioConfigScreenComposable<SettingsRoutes.NeighborInfo>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.AmbientLighting ->
                addRadioConfigScreenComposable<SettingsRoutes.AmbientLighting>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.DetectionSensor ->
                addRadioConfigScreenComposable<SettingsRoutes.DetectionSensor>(
                    navController,
                    entry.name,
                    entry.screenComposable,
                )
            is SettingsRoutes.Paxcounter ->
                addRadioConfigScreenComposable<SettingsRoutes.Paxcounter>(
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
    USER(R.string.user, SettingsRoutes.User, Icons.Default.Person, 0, { vm -> UserConfigScreen(vm) }),
    CHANNELS(
        R.string.channels,
        SettingsRoutes.ChannelConfig,
        Icons.AutoMirrored.Default.List,
        0,
        { vm -> ChannelConfigScreen(vm) },
    ),
    DEVICE(
        R.string.device,
        SettingsRoutes.Device,
        Icons.Default.Router,
        AdminProtos.AdminMessage.ConfigType.DEVICE_CONFIG_VALUE,
        { vm -> DeviceConfigScreen(vm) },
    ),
    POSITION(
        R.string.position,
        SettingsRoutes.Position,
        Icons.Default.LocationOn,
        AdminProtos.AdminMessage.ConfigType.POSITION_CONFIG_VALUE,
        { vm -> PositionConfigScreen(vm) },
    ),
    POWER(
        R.string.power,
        SettingsRoutes.Power,
        Icons.Default.Power,
        AdminProtos.AdminMessage.ConfigType.POWER_CONFIG_VALUE,
        { vm -> PowerConfigScreen(vm) },
    ),
    NETWORK(
        R.string.network,
        SettingsRoutes.Network,
        Icons.Default.Wifi,
        AdminProtos.AdminMessage.ConfigType.NETWORK_CONFIG_VALUE,
        { vm -> NetworkConfigScreen(vm) },
    ),
    DISPLAY(
        R.string.display,
        SettingsRoutes.Display,
        Icons.Default.DisplaySettings,
        AdminProtos.AdminMessage.ConfigType.DISPLAY_CONFIG_VALUE,
        { vm -> DisplayConfigScreen(vm) },
    ),
    LORA(
        R.string.lora,
        SettingsRoutes.LoRa,
        Icons.Default.CellTower,
        AdminProtos.AdminMessage.ConfigType.LORA_CONFIG_VALUE,
        { vm -> LoRaConfigScreen(vm) },
    ),
    BLUETOOTH(
        R.string.bluetooth,
        SettingsRoutes.Bluetooth,
        Icons.Default.Bluetooth,
        AdminProtos.AdminMessage.ConfigType.BLUETOOTH_CONFIG_VALUE,
        { vm -> BluetoothConfigScreen(vm) },
    ),
    SECURITY(
        R.string.security,
        SettingsRoutes.Security,
        Icons.Default.Security,
        AdminProtos.AdminMessage.ConfigType.SECURITY_CONFIG_VALUE,
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
    MQTT(
        R.string.mqtt,
        SettingsRoutes.MQTT,
        Icons.Default.Cloud,
        AdminProtos.AdminMessage.ModuleConfigType.MQTT_CONFIG_VALUE,
        { vm -> MQTTConfigScreen(vm) },
    ),
    SERIAL(
        R.string.serial,
        SettingsRoutes.Serial,
        Icons.Default.Usb,
        AdminProtos.AdminMessage.ModuleConfigType.SERIAL_CONFIG_VALUE,
        { vm -> SerialConfigScreen(vm) },
    ),
    EXT_NOTIFICATION(
        R.string.external_notification,
        SettingsRoutes.ExtNotification,
        Icons.Default.Notifications,
        AdminProtos.AdminMessage.ModuleConfigType.EXTNOTIF_CONFIG_VALUE,
        { vm -> ExternalNotificationConfigScreen(vm) },
    ),
    STORE_FORWARD(
        R.string.store_forward,
        SettingsRoutes.StoreForward,
        Icons.AutoMirrored.Default.Forward,
        AdminProtos.AdminMessage.ModuleConfigType.STOREFORWARD_CONFIG_VALUE,
        { vm -> StoreForwardConfigScreen(vm) },
    ),
    RANGE_TEST(
        R.string.range_test,
        SettingsRoutes.RangeTest,
        Icons.Default.Speed,
        AdminProtos.AdminMessage.ModuleConfigType.RANGETEST_CONFIG_VALUE,
        { vm -> RangeTestConfigScreen(vm) },
    ),
    TELEMETRY(
        R.string.telemetry,
        SettingsRoutes.Telemetry,
        Icons.Default.DataUsage,
        AdminProtos.AdminMessage.ModuleConfigType.TELEMETRY_CONFIG_VALUE,
        { vm -> TelemetryConfigScreen(vm) },
    ),
    CANNED_MESSAGE(
        R.string.canned_message,
        SettingsRoutes.CannedMessage,
        Icons.AutoMirrored.Default.Message,
        AdminProtos.AdminMessage.ModuleConfigType.CANNEDMSG_CONFIG_VALUE,
        { vm -> CannedMessageConfigScreen(vm) },
    ),
    AUDIO(
        R.string.audio,
        SettingsRoutes.Audio,
        Icons.AutoMirrored.Default.VolumeUp,
        AdminProtos.AdminMessage.ModuleConfigType.AUDIO_CONFIG_VALUE,
        { vm -> AudioConfigScreen(vm) },
    ),
    REMOTE_HARDWARE(
        R.string.remote_hardware,
        SettingsRoutes.RemoteHardware,
        Icons.Default.SettingsRemote,
        AdminProtos.AdminMessage.ModuleConfigType.REMOTEHARDWARE_CONFIG_VALUE,
        { vm -> RemoteHardwareConfigScreen(vm) },
    ),
    NEIGHBOR_INFO(
        R.string.neighbor_info,
        SettingsRoutes.NeighborInfo,
        Icons.Default.People,
        AdminProtos.AdminMessage.ModuleConfigType.NEIGHBORINFO_CONFIG_VALUE,
        { vm -> NeighborInfoConfigScreen(vm) },
    ),
    AMBIENT_LIGHTING(
        R.string.ambient_lighting,
        SettingsRoutes.AmbientLighting,
        Icons.Default.LightMode,
        AdminProtos.AdminMessage.ModuleConfigType.AMBIENTLIGHTING_CONFIG_VALUE,
        { vm -> AmbientLightingConfigScreen(vm) },
    ),
    DETECTION_SENSOR(
        R.string.detection_sensor,
        SettingsRoutes.DetectionSensor,
        Icons.Default.Sensors,
        AdminProtos.AdminMessage.ModuleConfigType.DETECTIONSENSOR_CONFIG_VALUE,
        { vm -> DetectionSensorConfigScreen(vm) },
    ),
    PAXCOUNTER(
        R.string.paxcounter,
        SettingsRoutes.Paxcounter,
        Icons.Default.PermScanWifi,
        AdminProtos.AdminMessage.ModuleConfigType.PAXCOUNTER_CONFIG_VALUE,
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
