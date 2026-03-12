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
@file:Suppress("Wrapping", "SpacingAroundColon")

package org.meshtastic.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.app.settings.AndroidDebugViewModel
import org.meshtastic.app.settings.AndroidRadioConfigViewModel
import org.meshtastic.app.settings.AndroidSettingsViewModel
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.feature.settings.AboutScreen
import org.meshtastic.feature.settings.AdministrationScreen
import org.meshtastic.feature.settings.DeviceConfigurationScreen
import org.meshtastic.feature.settings.ModuleConfigurationScreen
import org.meshtastic.feature.settings.SettingsScreen
import org.meshtastic.feature.settings.debugging.DebugScreen
import org.meshtastic.feature.settings.filter.FilterSettingsScreen
import org.meshtastic.feature.settings.filter.FilterSettingsViewModel
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseScreen
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseViewModel
import org.meshtastic.feature.settings.radio.channel.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.AmbientLightingConfigScreen
import org.meshtastic.feature.settings.radio.component.AudioConfigScreen
import org.meshtastic.feature.settings.radio.component.BluetoothConfigScreen
import org.meshtastic.feature.settings.radio.component.CannedMessageConfigScreen
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
import org.meshtastic.feature.settings.radio.component.StatusMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.StoreForwardConfigScreen
import org.meshtastic.feature.settings.radio.component.TAKConfigScreen
import org.meshtastic.feature.settings.radio.component.TelemetryConfigScreen
import org.meshtastic.feature.settings.radio.component.TrafficManagementConfigScreen
import org.meshtastic.feature.settings.radio.component.UserConfigScreen
import kotlin.reflect.KClass

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun EntryProviderScope<NavKey>.settingsGraph(backStack: NavBackStack<NavKey>) {
    entry<SettingsRoutes.SettingsGraph> {
        SettingsScreen(
            settingsViewModel = koinViewModel<AndroidSettingsViewModel>(),
            viewModel = koinViewModel<AndroidRadioConfigViewModel>(),
            onClickNodeChip = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
        ) {
            backStack.add(it)
        }
    }

    entry<SettingsRoutes.Settings> {
        SettingsScreen(
            settingsViewModel = koinViewModel<AndroidSettingsViewModel>(),
            viewModel = koinViewModel<AndroidRadioConfigViewModel>(),
            onClickNodeChip = { backStack.add(NodesRoutes.NodeDetailGraph(it)) },
        ) {
            backStack.add(it)
        }
    }

    entry<SettingsRoutes.DeviceConfiguration> {
        DeviceConfigurationScreen(
            viewModel = koinViewModel<AndroidRadioConfigViewModel>(),
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { route -> backStack.add(route) },
        )
    }

    entry<SettingsRoutes.ModuleConfiguration> {
        val settingsViewModel: AndroidSettingsViewModel = koinViewModel()
        val excludedModulesUnlocked by settingsViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
        ModuleConfigurationScreen(
            viewModel = koinViewModel<AndroidRadioConfigViewModel>(),
            excludedModulesUnlocked = excludedModulesUnlocked,
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { route -> backStack.add(route) },
        )
    }

    entry<SettingsRoutes.Administration> {
        AdministrationScreen(
            viewModel = koinViewModel<AndroidRadioConfigViewModel>(),
            onBack = { backStack.removeLastOrNull() },
        )
    }

    entry<SettingsRoutes.CleanNodeDb> {
        val viewModel: CleanNodeDatabaseViewModel = koinViewModel()
        CleanNodeDatabaseScreen(viewModel = viewModel)
    }

    ConfigRoute.entries.forEach { routeInfo ->
        configComposable(routeInfo.route::class) { viewModel ->
            LaunchedEffect(Unit) { viewModel.setResponseStateLoading(routeInfo) }
            when (routeInfo) {
                ConfigRoute.USER -> UserConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.CHANNELS -> ChannelConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.DEVICE -> DeviceConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.POSITION -> PositionConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.POWER -> PowerConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.NETWORK -> NetworkConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.DISPLAY -> DisplayConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.LORA -> LoRaConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.BLUETOOTH -> BluetoothConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.SECURITY -> SecurityConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
            }
        }
    }

    ModuleRoute.entries.forEach { routeInfo ->
        configComposable(routeInfo.route::class) { viewModel ->
            LaunchedEffect(Unit) { viewModel.setResponseStateLoading(routeInfo) }
            when (routeInfo) {
                ModuleRoute.MQTT -> MQTTConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.SERIAL -> SerialConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.EXT_NOTIFICATION ->
                    ExternalNotificationConfigScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.STORE_FORWARD ->
                    StoreForwardConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.RANGE_TEST -> RangeTestConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.TELEMETRY -> TelemetryConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.CANNED_MESSAGE ->
                    CannedMessageConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.AUDIO -> AudioConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.REMOTE_HARDWARE ->
                    RemoteHardwareConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.NEIGHBOR_INFO ->
                    NeighborInfoConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.AMBIENT_LIGHTING ->
                    AmbientLightingConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.DETECTION_SENSOR ->
                    DetectionSensorConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.PAXCOUNTER -> PaxcounterConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.STATUS_MESSAGE ->
                    StatusMessageConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.TRAFFIC_MANAGEMENT ->
                    TrafficManagementConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.TAK -> TAKConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
            }
        }
    }

    entry<SettingsRoutes.DebugPanel> {
        val viewModel: AndroidDebugViewModel = koinViewModel()
        DebugScreen(viewModel = viewModel, onNavigateUp = { backStack.removeLastOrNull() })
    }

    entry<SettingsRoutes.About> {
        val context = androidx.compose.ui.platform.LocalContext.current
        AboutScreen(
            onNavigateUp = { backStack.removeLastOrNull() },
            jsonProvider = {
                context.resources.openRawResource(org.meshtastic.app.R.raw.aboutlibraries).bufferedReader().readText()
            },
        )
    }

    entry<SettingsRoutes.FilterSettings> {
        val viewModel: FilterSettingsViewModel = koinViewModel()
        FilterSettingsScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
    }
}

fun <R : Route> EntryProviderScope<NavKey>.configComposable(
    route: KClass<R>,
    content: @Composable (AndroidRadioConfigViewModel) -> Unit,
) {
    addEntryProvider(route) { content(koinViewModel<AndroidRadioConfigViewModel>()) }
}

inline fun <reified R : Route> EntryProviderScope<NavKey>.configComposable(
    noinline content: @Composable (AndroidRadioConfigViewModel) -> Unit,
) {
    entry<R> { content(koinViewModel<AndroidRadioConfigViewModel>()) }
}
