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
package org.meshtastic.feature.settings.navigation

import androidx.compose.runtime.Composable
import org.meshtastic.core.navigation.Route
import org.meshtastic.feature.settings.DesktopDeviceConfigScreen
import org.meshtastic.feature.settings.DesktopExternalNotificationConfigScreen
import org.meshtastic.feature.settings.DesktopNetworkConfigScreen
import org.meshtastic.feature.settings.DesktopPositionConfigScreen
import org.meshtastic.feature.settings.DesktopSecurityConfigScreen
import org.meshtastic.feature.settings.DesktopSettingsScreen
import org.meshtastic.feature.settings.SettingsViewModel
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
actual fun SettingsMainScreen(
    settingsViewModel: SettingsViewModel,
    radioConfigViewModel: RadioConfigViewModel,
    onClickNodeChip: (Int) -> Unit,
    onNavigate: (Route) -> Unit,
) {
    DesktopSettingsScreen(
        settingsViewModel = settingsViewModel,
        radioConfigViewModel = radioConfigViewModel,
        onNavigate = onNavigate,
    )
}

@Composable
actual fun DeviceConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    DesktopDeviceConfigScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun PositionConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    DesktopPositionConfigScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun NetworkConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    DesktopNetworkConfigScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun SecurityConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    DesktopSecurityConfigScreen(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun ExternalNotificationConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    DesktopExternalNotificationConfigScreen(viewModel = viewModel, onBack = onBack)
}
