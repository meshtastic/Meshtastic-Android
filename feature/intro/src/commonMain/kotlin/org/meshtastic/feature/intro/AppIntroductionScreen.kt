/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.intro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
import org.meshtastic.core.ui.util.PermissionUiState
import org.meshtastic.core.ui.util.isBluetoothSupported
import org.meshtastic.core.ui.util.rememberBluetoothPermissionState
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.core.ui.util.rememberNotificationPermissionState

/**
 * Main application introduction screen. This Composable hosts the navigation flow and hoists the permission states.
 *
 * @param onDone Callback invoked when the introduction flow is completed.
 * @param viewModel ViewModel for tracking the introduction flow state.
 */
@Composable
fun AppIntroductionScreen(onDone: () -> Unit, viewModel: IntroViewModel) {
    val platform = rememberIntroPlatform()
    // A notification state that is not runtime-gated (Android before 13) has nothing to configure, so it stays null
    // and the flow skips the notification ask.
    val notificationPermissionState = rememberNotificationPermissionState().takeIf { it.isRuntimeGated }

    // Deliberately not remembered: rememberXxxPermissionState returns a fresh PermissionUiState carrying the current
    // status on every composition, so caching this wrapper would pin the intro screens to a stale status.
    val permissions =
        IntroPermissionStates(
            bluetooth = rememberBluetoothPermissionState(),
            location = rememberLocationPermissionState(),
            notification = notificationPermissionState,
            bluetoothRequiresLocation = platform.bluetoothRequiresLocation,
            bluetoothSupported = isBluetoothSupported(),
            inBrowser = platform.inBrowser,
            criticalAlertsConfigurable = platform.settingsNavigator != null,
        )
    val backStack = rememberNavBackStack(IntroSavedStateConfig, Welcome)

    CompositionLocalProvider(
        LocalIntroPermissions provides permissions,
        LocalIntroSettingsNavigator provides (platform.settingsNavigator ?: NoIntroSettingsNavigator),
    ) {
        MeshtasticNavDisplay(
            backStack = backStack,
            entryProvider = entryProvider { introGraph(backStack = backStack, viewModel = viewModel, onDone = onDone) },
        )
    }
}

/** [IntroPermissions] holding the hoisted states verbatim, so each screen sees the full status it recovers from. */
internal class IntroPermissionStates(
    override val bluetooth: PermissionUiState,
    override val location: PermissionUiState,
    override val notification: PermissionUiState?,
    override val bluetoothRequiresLocation: Boolean,
    override val bluetoothSupported: Boolean,
    override val inBrowser: Boolean,
    override val criticalAlertsConfigurable: Boolean,
) : IntroPermissions

private object NoIntroSettingsNavigator : IntroSettingsNavigator {
    override fun openAppSettings() = Unit

    override fun openCriticalAlertsSettings() = Unit
}
