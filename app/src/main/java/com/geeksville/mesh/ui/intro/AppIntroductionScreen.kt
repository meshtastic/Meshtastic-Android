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

package com.geeksville.mesh.ui.intro

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState

/**
 * Composable function for the main application introduction screen. This screen guides the user through initial setup
 * steps like granting permissions.
 *
 * @param onDone Callback invoked when the introduction flow is completed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod")
@Composable
fun AppIntroductionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val notificationPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    val locationPermissions =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationPermissionState = rememberMultiplePermissionsState(permissions = locationPermissions)

    NavHost(navController = navController, startDestination = IntroRoute.Welcome.route) {
        composable(IntroRoute.Welcome.route) {
            WelcomeScreen(onGetStarted = { navController.navigate(IntroRoute.Notifications.route) })
        }
        composable(IntroRoute.Notifications.route) {
            val notificationsAlreadyGranted = notificationPermissionState?.status?.isGranted ?: true
            NotificationsScreen(
                showNextButton = notificationsAlreadyGranted,
                onSkip = { navController.navigate(IntroRoute.Location.route) },
                onConfigure = {
                    if (notificationsAlreadyGranted) {
                        navController.navigate(IntroRoute.CriticalAlerts.route)
                    } else {
                        // For Android Tiramisu (API 33) and above, this requests POST_NOTIFICATIONS
                        // For lower versions, notificationPermissionState will be null, and this branch isn't taken.
                        notificationPermissionState.launchPermissionRequest()
                    }
                },
            )
        }
        composable(IntroRoute.CriticalAlerts.route) {
            CriticalAlertsScreen(
                onSkip = { navController.navigate(IntroRoute.Location.route) },
                onConfigure = {
                    // Intent to open the specific notification channel settings for "my_alerts"
                    // This allows the user to enable critical alerts if they were initially denied
                    // or to adjust settings for notifications that can bypass Do Not Disturb.
                    val intent =
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, "my_alerts")
                        }
                    context.startActivity(intent)
                    navController.navigate(IntroRoute.Location.route)
                },
            )
        }
        composable(IntroRoute.Location.route) {
            val locationAlreadyGranted = locationPermissionState.allPermissionsGranted
            LocationScreen(
                showNextButton = locationAlreadyGranted,
                onSkip = onDone, // Callback to signify completion of the intro flow
                onConfigure = {
                    if (locationAlreadyGranted) {
                        onDone() // Permissions already granted, proceed to finish
                    } else {
                        locationPermissionState.launchMultiplePermissionRequest()
                    }
                },
            )
        }
    }
}
