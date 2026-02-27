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
package org.meshtastic.feature.intro

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.serialization.Serializable

@Serializable internal data object Welcome : NavKey

@Serializable internal data object Bluetooth : NavKey

@Serializable internal data object Location : NavKey

@Serializable internal data object Notifications : NavKey

@Serializable internal data object CriticalAlerts : NavKey

/**
 * Provides the navigation graph for the application introduction flow. The flow follows the hierarchy of necessity:
 * Core Connection -> Shared Location -> Notifications.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
@Suppress("LongMethod")
internal fun introNavGraph(
    backStack: NavBackStack<NavKey>,
    @Suppress("unused") viewModel: IntroViewModel,
    notificationPermissionState: PermissionState?,
    bluetoothPermissionState: MultiplePermissionsState,
    locationPermissionState: MultiplePermissionsState,
    onDone: () -> Unit,
) = entryProvider {
    val context = LocalContext.current

    entry<Welcome> { WelcomeScreen(onGetStarted = { backStack.add(Bluetooth) }) }

    entry<Bluetooth> {
        val isGranted = bluetoothPermissionState.allPermissionsGranted
        BluetoothScreen(
            showNextButton = isGranted,
            onSkip = { backStack.add(Location) },
            onConfigure = {
                if (isGranted) {
                    backStack.add(Location)
                } else {
                    bluetoothPermissionState.launchMultiplePermissionRequest()
                }
            },
        )
    }

    entry<Location> {
        val isGranted = locationPermissionState.allPermissionsGranted
        LocationScreen(
            showNextButton = isGranted,
            onSkip = { backStack.add(Notifications) },
            onConfigure = {
                if (isGranted) {
                    backStack.add(Notifications)
                } else {
                    locationPermissionState.launchMultiplePermissionRequest()
                }
            },
        )
    }

    entry<Notifications> {
        val isGranted = notificationPermissionState?.status?.isGranted ?: true
        NotificationsScreen(
            showNextButton = isGranted,
            onSkip = onDone,
            onConfigure = {
                if (notificationPermissionState != null && !isGranted) {
                    notificationPermissionState.launchPermissionRequest()
                } else {
                    backStack.add(CriticalAlerts)
                }
            },
        )
    }

    entry<CriticalAlerts> {
        CriticalAlertsScreen(
            onSkip = onDone,
            onConfigure = {
                val intent =
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, "my_alerts")
                    }
                context.startActivity(intent)
                onDone()
            },
        )
    }
}
