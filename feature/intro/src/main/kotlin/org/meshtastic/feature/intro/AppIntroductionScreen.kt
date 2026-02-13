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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import no.nordicsemi.android.common.permissions.ble.RequireBluetooth
import no.nordicsemi.android.common.permissions.ble.RequireLocation
import no.nordicsemi.android.common.permissions.notification.RequestNotificationPermission

/**
 * Composable function for the main application introduction screen. This screen guides the user through initial setup
 * steps like granting permissions.
 *
 * @param onDone Callback invoked when the introduction flow is completed.
 */
@Suppress("LongMethod")
@Composable
fun AppIntroductionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val backStack = rememberNavBackStack(Welcome)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
        entryProvider {
            entry<Welcome> { WelcomeScreen(onGetStarted = { backStack.add(Notifications) }) }

            entry<Notifications> {
                RequestNotificationPermission { canShowNotifications ->
                    NotificationsScreen(
                        showNextButton = canShowNotifications == true,
                        onSkip = {
                            // Skip this screen and the Critical Alerts screen. Proceed to Bluetooth screen.
                            backStack.add(Bluetooth)
                        },
                        onConfigure = {
                            if (canShowNotifications == true) {
                                backStack.add(CriticalAlerts)
                            }
                            // Else: RequestNotificationPermission internally handles the request UI.
                        },
                    )
                }
            }

            entry<CriticalAlerts> {
                CriticalAlertsScreen(
                    onSkip = { backStack.add(Bluetooth) },
                    onConfigure = {
                        val intent =
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                putExtra(Settings.EXTRA_CHANNEL_ID, "my_alerts")
                            }
                        context.startActivity(intent)
                        backStack.add(Bluetooth)
                    },
                )
            }

            entry<Bluetooth> { RequireBluetooth { backStack.add(Location) } }

            entry<Location> {
                RequireLocation { isLocationRequiredAndDisabled ->
                    LocationScreen(
                        showNextButton = !isLocationRequiredAndDisabled,
                        onSkip = onDone,
                        onConfigure = {
                            if (!isLocationRequiredAndDisabled) {
                                onDone()
                            }
                            // Else: RequireLocation internally handles the request UI.
                        },
                    )
                }
            }
        },
    )
}

@Serializable private data object Welcome : NavKey

@Serializable private data object Notifications : NavKey

@Serializable private data object CriticalAlerts : NavKey

@Serializable private data object Bluetooth : NavKey

@Serializable private data object Location : NavKey
