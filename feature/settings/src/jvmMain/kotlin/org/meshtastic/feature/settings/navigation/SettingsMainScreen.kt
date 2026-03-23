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
import org.meshtastic.feature.settings.DesktopSettingsScreen
import org.meshtastic.feature.settings.SettingsViewModel
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.DeviceConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.ExternalNotificationConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.PositionConfigScreenCommon
import org.meshtastic.feature.settings.radio.component.SecurityConfigScreenCommon

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
    DeviceConfigScreenCommon(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun PositionConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    PositionConfigScreenCommon(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun SecurityConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    SecurityConfigScreenCommon(viewModel = viewModel, onBack = onBack)
}

@Composable
actual fun ExternalNotificationConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    ExternalNotificationConfigScreenCommon(viewModel = viewModel, onBack = onBack)
}
