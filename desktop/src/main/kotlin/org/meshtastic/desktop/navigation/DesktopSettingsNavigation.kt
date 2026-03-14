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
package org.meshtastic.desktop.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.desktop.ui.settings.DesktopDeviceConfigScreen
import org.meshtastic.desktop.ui.settings.DesktopExternalNotificationConfigScreen
import org.meshtastic.desktop.ui.settings.DesktopNetworkConfigScreen
import org.meshtastic.desktop.ui.settings.DesktopPositionConfigScreen
import org.meshtastic.desktop.ui.settings.DesktopSecurityConfigScreen
import org.meshtastic.desktop.ui.settings.DesktopSettingsScreen
import org.meshtastic.feature.settings.AboutScreen
import org.meshtastic.feature.settings.AdministrationScreen
import org.meshtastic.feature.settings.DeviceConfigurationScreen
import org.meshtastic.feature.settings.ModuleConfigurationScreen
import org.meshtastic.feature.settings.SettingsViewModel
import org.meshtastic.feature.settings.filter.FilterSettingsScreen
import org.meshtastic.feature.settings.filter.FilterSettingsViewModel
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseScreen
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseViewModel
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.AmbientLightingConfigScreen
import org.meshtastic.feature.settings.radio.component.AudioConfigScreen
import org.meshtastic.feature.settings.radio.component.BluetoothConfigScreen
import org.meshtastic.feature.settings.radio.component.CannedMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.DetectionSensorConfigScreen
import org.meshtastic.feature.settings.radio.component.DisplayConfigScreen
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen
import org.meshtastic.feature.settings.radio.component.MQTTConfigScreen
import org.meshtastic.feature.settings.radio.component.NeighborInfoConfigScreen
import org.meshtastic.feature.settings.radio.component.PaxcounterConfigScreen
import org.meshtastic.feature.settings.radio.component.PowerConfigScreen
import org.meshtastic.feature.settings.radio.component.RangeTestConfigScreen
import org.meshtastic.feature.settings.radio.component.RemoteHardwareConfigScreen
import org.meshtastic.feature.settings.radio.component.SerialConfigScreen
import org.meshtastic.feature.settings.radio.component.StatusMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.StoreForwardConfigScreen
import org.meshtastic.feature.settings.radio.component.TAKConfigScreen
import org.meshtastic.feature.settings.radio.component.TelemetryConfigScreen
import org.meshtastic.feature.settings.radio.component.TrafficManagementConfigScreen
import org.meshtastic.feature.settings.radio.component.UserConfigScreen
import kotlin.reflect.KClass

@Composable
private fun getRadioConfigViewModel(backStack: NavBackStack<NavKey>): RadioConfigViewModel {
    val viewModel = koinViewModel<RadioConfigViewModel>()
    LaunchedEffect(backStack) {
        val destNum =
            backStack.lastOrNull { it is SettingsRoutes.Settings }?.let { (it as SettingsRoutes.Settings).destNum }
                ?: backStack
                    .lastOrNull { it is SettingsRoutes.SettingsGraph }
                    ?.let { (it as SettingsRoutes.SettingsGraph).destNum }
        viewModel.initDestNum(destNum)
    }
    return viewModel
}

/**
 * Registers real settings feature composables into the desktop navigation graph.
 *
 * Top-level settings screen is a desktop-specific composable since Android's [SettingsScreen] uses Android-only APIs.
 * All sub-screens (device config, module config, radio config, channels, etc.) use the shared commonMain composables
 * from `feature:settings`.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
fun EntryProviderScope<NavKey>.desktopSettingsGraph(backStack: NavBackStack<NavKey>) {
    // Top-level settings — desktop-specific screen (Android version uses Activity, permissions, etc.)
    entry<SettingsRoutes.SettingsGraph> {
        DesktopSettingsScreen(
            radioConfigViewModel = getRadioConfigViewModel(backStack),
            settingsViewModel = koinViewModel<SettingsViewModel>(),
            onNavigate = { route -> backStack.add(route) },
        )
    }

    entry<SettingsRoutes.Settings> {
        DesktopSettingsScreen(
            radioConfigViewModel = getRadioConfigViewModel(backStack),
            settingsViewModel = koinViewModel<SettingsViewModel>(),
            onNavigate = { route -> backStack.add(route) },
        )
    }

    // Device configuration — shared commonMain composable
    entry<SettingsRoutes.DeviceConfiguration> {
        DeviceConfigurationScreen(
            viewModel = getRadioConfigViewModel(backStack),
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { route -> backStack.add(route) },
        )
    }

    // Module configuration — shared commonMain composable
    entry<SettingsRoutes.ModuleConfiguration> {
        val settingsViewModel: SettingsViewModel = koinViewModel()
        val excludedModulesUnlocked by settingsViewModel.excludedModulesUnlocked.collectAsStateWithLifecycle()
        ModuleConfigurationScreen(
            viewModel = getRadioConfigViewModel(backStack),
            excludedModulesUnlocked = excludedModulesUnlocked,
            onBack = { backStack.removeLastOrNull() },
            onNavigate = { route -> backStack.add(route) },
        )
    }

    // Administration — shared commonMain composable
    entry<SettingsRoutes.Administration> {
        AdministrationScreen(viewModel = getRadioConfigViewModel(backStack), onBack = { backStack.removeLastOrNull() })
    }

    // Clean node database — shared commonMain composable
    entry<SettingsRoutes.CleanNodeDb> {
        val viewModel: CleanNodeDatabaseViewModel = koinViewModel()
        CleanNodeDatabaseScreen(viewModel = viewModel)
    }

    // Debug Panel — Desktop-specific basic log viewer
    entry<SettingsRoutes.DebugPanel> {
        val viewModel: org.meshtastic.feature.settings.debugging.DebugViewModel = koinViewModel()
        org.meshtastic.desktop.ui.settings.DesktopDebugScreen(
            viewModel = viewModel,
            onNavigateUp = { backStack.removeLastOrNull() },
        )
    }

    // Config routes — all from commonMain composables
    ConfigRoute.entries.forEach { routeInfo ->
        desktopConfigComposable(routeInfo.route::class, backStack) { viewModel ->
            LaunchedEffect(Unit) { viewModel.setResponseStateLoading(routeInfo) }
            when (routeInfo) {
                ConfigRoute.USER -> UserConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.CHANNELS -> ChannelConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.DEVICE -> DesktopDeviceConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.POSITION ->
                    DesktopPositionConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.POWER -> PowerConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.NETWORK -> DesktopNetworkConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.DISPLAY -> DisplayConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.LORA -> LoRaConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.BLUETOOTH -> BluetoothConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ConfigRoute.SECURITY ->
                    DesktopSecurityConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
            }
        }
    }

    // Module routes — all from commonMain composables
    ModuleRoute.entries.forEach { routeInfo ->
        desktopConfigComposable(routeInfo.route::class, backStack) { viewModel ->
            LaunchedEffect(Unit) { viewModel.setResponseStateLoading(routeInfo) }
            when (routeInfo) {
                ModuleRoute.MQTT -> MQTTConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.SERIAL -> SerialConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
                ModuleRoute.EXT_NOTIFICATION ->
                    DesktopExternalNotificationConfigScreen(viewModel, onBack = { backStack.removeLastOrNull() })
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

    // About — shared commonMain screen, per-platform library definitions loaded from JVM classpath
    entry<SettingsRoutes.About> {
        AboutScreen(
            onNavigateUp = { backStack.removeLastOrNull() },
            jsonProvider = { SettingsRoutes::class.java.getResource("/aboutlibraries.json")?.readText() ?: "" },
        )
    }

    // Filter settings — shared commonMain composable
    entry<SettingsRoutes.FilterSettings> {
        val viewModel: FilterSettingsViewModel = koinViewModel()
        FilterSettingsScreen(viewModel = viewModel, onBack = { backStack.removeLastOrNull() })
    }
}

/** Helper to register a config/module route entry with a [RadioConfigViewModel] scoped to that entry. */
fun <R : Route> EntryProviderScope<NavKey>.desktopConfigComposable(
    route: KClass<R>,
    backStack: NavBackStack<NavKey>,
    content: @Composable (RadioConfigViewModel) -> Unit,
) {
    addEntryProvider(route) { content(getRadioConfigViewModel(backStack)) }
}
