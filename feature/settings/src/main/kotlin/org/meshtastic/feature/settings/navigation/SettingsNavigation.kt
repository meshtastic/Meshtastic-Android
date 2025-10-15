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

import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
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

        ConfigRoute.entries.forEach { entry ->
            composable(entry.route::class) { backStackEntry ->
                val parentEntry =
                    remember(backStackEntry) { navController.getBackStackEntry(SettingsRoutes.SettingsGraph::class) }
                val viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry)

                when (entry) {
                    ConfigRoute.USER -> UserConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.CHANNELS -> ChannelConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.DEVICE -> DeviceConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.POSITION -> PositionConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.POWER -> PowerConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.NETWORK -> NetworkConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.DISPLAY -> DisplayConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.LORA -> LoRaConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.BLUETOOTH -> BluetoothConfigScreen(viewModel, onBack = navController::popBackStack)

                    ConfigRoute.SECURITY -> SecurityConfigScreen(viewModel, onBack = navController::popBackStack)
                }
            }
        }

        ModuleRoute.entries.forEach { entry ->
            composable(entry.route::class) { backStackEntry ->
                val parentEntry =
                    remember(backStackEntry) { navController.getBackStackEntry<SettingsRoutes.SettingsGraph>() }
                val viewModel = hiltViewModel<RadioConfigViewModel>(parentEntry)

                when (entry) {
                    ModuleRoute.MQTT -> MQTTConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.SERIAL -> SerialConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.EXT_NOTIFICATION ->
                        ExternalNotificationConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.STORE_FORWARD ->
                        StoreForwardConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.RANGE_TEST -> RangeTestConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.TELEMETRY -> TelemetryConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.CANNED_MESSAGE ->
                        CannedMessageConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.AUDIO -> AudioConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.REMOTE_HARDWARE ->
                        RemoteHardwareConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.NEIGHBOR_INFO ->
                        NeighborInfoConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.AMBIENT_LIGHTING ->
                        AmbientLightingConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.DETECTION_SENSOR ->
                        DetectionSensorConfigScreen(viewModel, onBack = navController::popBackStack)

                    ModuleRoute.PAXCOUNTER -> PaxcounterConfigScreen(viewModel, onBack = navController::popBackStack)
                }
            }
        }

        composable<SettingsRoutes.DebugPanel>(
            deepLinks =
            listOf(navDeepLink<SettingsRoutes.DebugPanel>(basePath = "$DEEP_LINK_BASE_URI/settings/debug_panel")),
        ) {
            DebugScreen(onNavigateUp = navController::navigateUp)
        }
    }
}
