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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
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
        addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
            when (entry) {
                ConfigRoute.USER -> UserConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.CHANNELS -> ChannelConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.DEVICE -> DeviceConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.POSITION -> PositionConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.POWER -> PowerConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.NETWORK -> NetworkConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.DISPLAY -> DisplayConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.LORA -> LoRaConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.BLUETOOTH -> BluetoothConfigScreen(it, onBack = navController::popBackStack)

                ConfigRoute.SECURITY -> SecurityConfigScreen(it, onBack = navController::popBackStack)
            }
        }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun NavGraphBuilder.moduleRoutesScreens(navController: NavHostController) {
    ModuleRoute.entries.forEach { entry ->
        addRadioConfigScreenComposable(entry.route::class, navController, entry.name) {
            when (entry) {
                ModuleRoute.MQTT -> MQTTConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.SERIAL -> SerialConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.EXT_NOTIFICATION ->
                    ExternalNotificationConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.STORE_FORWARD -> StoreForwardConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.RANGE_TEST -> RangeTestConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.TELEMETRY -> TelemetryConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.CANNED_MESSAGE -> CannedMessageConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.AUDIO -> AudioConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.REMOTE_HARDWARE -> RemoteHardwareConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.NEIGHBOR_INFO -> NeighborInfoConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.AMBIENT_LIGHTING -> AmbientLightingConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.DETECTION_SENSOR -> DetectionSensorConfigScreen(it, onBack = navController::popBackStack)

                ModuleRoute.PAXCOUNTER -> PaxcounterConfigScreen(it, onBack = navController::popBackStack)
            }
        }
    }
}
