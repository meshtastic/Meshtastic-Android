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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import no.nordicsemi.android.common.permissions.ble.RequireBluetooth
import no.nordicsemi.android.common.permissions.ble.RequireLocation
import no.nordicsemi.android.common.permissions.notification.RequestNotificationPermission
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.permission_denied
import org.meshtastic.core.strings.permission_granted
import org.meshtastic.core.ui.util.showToast

/**
 * Composable function for the main application introduction screen. This screen guides the user through initial setup
 * steps like granting permissions.
 *
 * @param onDone Callback invoked when the introduction flow is completed.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod")
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
                var isConfiguring by remember { mutableStateOf(false) }

                if (isConfiguring) {
                    RequestNotificationPermission { canShowNotifications ->
                        LaunchedEffect(canShowNotifications) {
                            if (canShowNotifications == true) {
                                context.showToast(Res.string.permission_granted)
                            } else if (canShowNotifications == false) {
                                context.showToast(Res.string.permission_denied)
                            }
                        }

                        NotificationsScreen(
                            showNextButton = canShowNotifications == true,
                            onSkip = { backStack.add(Bluetooth) },
                            onConfigure = {
                                if (canShowNotifications == true) {
                                    backStack.add(CriticalAlerts)
                                }
                            },
                        )
                    }
                } else {
                    NotificationsScreen(
                        showNextButton = false,
                        onSkip = { backStack.add(Bluetooth) },
                        onConfigure = { isConfiguring = true },
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

            entry<Bluetooth> {
                var isConfiguring by remember { mutableStateOf(false) }

                if (isConfiguring) {
                    RequireBluetooth {
                        LaunchedEffect(Unit) {
                            context.showToast(Res.string.permission_granted)
                            backStack.add(Location)
                        }
                    }
                } else {
                    BluetoothScreen(
                        showNextButton = false,
                        onSkip = { backStack.add(Location) },
                        onConfigure = { isConfiguring = true },
                    )
                }
            }

            entry<Location> {
                var isConfiguring by remember { mutableStateOf(false) }

                if (isConfiguring) {
                    RequireLocation { isLocationRequiredAndDisabled ->
                        LaunchedEffect(isLocationRequiredAndDisabled) {
                            if (!isLocationRequiredAndDisabled) {
                                context.showToast(Res.string.permission_granted)
                            } else {
                                context.showToast(Res.string.permission_denied)
                            }
                        }

                        LocationScreen(
                            showNextButton = !isLocationRequiredAndDisabled,
                            onSkip = onDone,
                            onConfigure = {
                                if (!isLocationRequiredAndDisabled) {
                                    onDone()
                                }
                            },
                        )
                    }
                } else {
                    LocationScreen(showNextButton = false, onSkip = onDone, onConfigure = { isConfiguring = true })
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
