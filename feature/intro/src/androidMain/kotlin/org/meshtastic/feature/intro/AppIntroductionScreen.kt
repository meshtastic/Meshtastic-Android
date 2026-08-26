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

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import org.meshtastic.core.ui.component.MeshtasticNavDisplay
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
    val context = LocalContext.current

    // Pre-Android 13 has no runtime notification permission, so there is nothing to configure — keep it null so the
    // intro flow can skip the notification screen entirely. SDK_INT is constant per process, so the conditional call
    // is recomposition-safe.
    val notificationPermissionState =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) rememberNotificationPermissionState() else null

    val locationPermissionState = rememberLocationPermissionState()
    val bluetoothPermissionState = rememberBluetoothPermissionState()
    // Pre-Android 12 the BLE scan is gated by ACCESS_FINE_LOCATION, which rememberBluetoothPermissionState already
    // reflects. The Bluetooth screen needs to know so its copy names the permission the user will actually be shown.
    val bluetoothRequiresLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    // Deliberately not remembered: rememberRuntimePermissionState returns a fresh PermissionUiState carrying the
    // current status on every composition, so caching this wrapper would pin the intro screens to a stale status —
    // exactly the bug that left the primary button inert after a denial.
    val permissions =
        AndroidIntroPermissions(
            bluetooth = bluetoothPermissionState,
            location = locationPermissionState,
            notification = notificationPermissionState,
            bluetoothRequiresLocation = bluetoothRequiresLocation,
        )
    val settingsNavigator = remember(context) { AndroidIntroSettingsNavigator(context) }
    val backStack = rememberNavBackStack(Welcome)

    CompositionLocalProvider(
        LocalIntroPermissions provides permissions,
        LocalIntroSettingsNavigator provides settingsNavigator,
    ) {
        MeshtasticNavDisplay(
            backStack = backStack,
            entryProvider = entryProvider { introGraph(backStack = backStack, viewModel = viewModel, onDone = onDone) },
        )
    }
}
