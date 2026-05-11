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

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Navigation graph for the application introduction / onboarding flow. */
@Suppress("LongMethod")
internal fun EntryProviderScope<NavKey>.introGraph(
    backStack: NavBackStack<NavKey>,
    viewModel: IntroViewModel,
    onDone: () -> Unit,
) {
    fun navigateToNext(current: NavKey, permissionsGranted: Boolean = true) {
        val next = viewModel.getNextKey(current, permissionsGranted)
        if (next != null) {
            backStack.add(next)
        } else {
            onDone()
        }
    }

    entry<Welcome> { WelcomeScreen(onGetStarted = { navigateToNext(Welcome) }) }

    entry<Bluetooth> {
        val permissions = LocalIntroPermissions.current
        val settingsNavigator = LocalIntroSettingsNavigator.current
        val isGranted = permissions.bluetooth.isGranted
        BluetoothScreen(
            showNextButton = isGranted,
            onSkip = { navigateToNext(Bluetooth) },
            onConfigure = {
                if (isGranted) {
                    navigateToNext(Bluetooth)
                } else {
                    permissions.bluetooth.launchRequest()
                }
            },
            onOpenSettings = { settingsNavigator.openAppSettings() },
        )
    }

    entry<Location> {
        val permissions = LocalIntroPermissions.current
        val settingsNavigator = LocalIntroSettingsNavigator.current
        val isGranted = permissions.location.isGranted
        LocationScreen(
            showNextButton = isGranted,
            onSkip = { navigateToNext(Location) },
            onConfigure = {
                if (isGranted) {
                    navigateToNext(Location)
                } else {
                    permissions.location.launchRequest()
                }
            },
            onOpenSettings = { settingsNavigator.openAppSettings() },
        )
    }

    entry<Notifications> {
        val permissions = LocalIntroPermissions.current
        val settingsNavigator = LocalIntroSettingsNavigator.current
        val notificationPermission = permissions.notification
        val isGranted = notificationPermission?.isGranted ?: true
        NotificationsScreen(
            showNextButton = isGranted,
            onSkip = onDone,
            onConfigure = {
                if (notificationPermission != null && !isGranted) {
                    notificationPermission.launchRequest()
                } else {
                    navigateToNext(Notifications, permissionsGranted = isGranted)
                }
            },
            onOpenSettings = { settingsNavigator.openAppSettings() },
        )
    }

    entry<CriticalAlerts> {
        val settingsNavigator = LocalIntroSettingsNavigator.current
        CriticalAlertsScreen(
            onSkip = onDone,
            onConfigure = {
                settingsNavigator.openCriticalAlertsSettings()
                onDone()
            },
        )
    }
}
