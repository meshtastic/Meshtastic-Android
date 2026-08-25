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
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState

/** Navigation graph for the application introduction / onboarding flow. */
@Suppress("LongMethod")
internal fun EntryProviderScope<NavKey>.introGraph(
    backStack: NavBackStack<NavKey>,
    viewModel: IntroViewModel,
    onDone: () -> Unit,
) {
    fun navigateToNext(
        current: NavKey,
        permissionsGranted: Boolean = true,
        bluetoothRequiresLocation: Boolean = false,
    ) {
        val next = viewModel.getNextKey(current, permissionsGranted, bluetoothRequiresLocation)
        if (next != null) {
            backStack.add(next)
        } else {
            onDone()
        }
    }

    /** The one action behind every permission screen's primary button, resolved by [introPermissionAction]. */
    fun onPrimaryAction(state: PermissionUiState, current: NavKey, bluetoothRequiresLocation: Boolean = false) {
        when (introPermissionAction(state.status)) {
            IntroPermissionAction.ADVANCE ->
                navigateToNext(current, bluetoothRequiresLocation = bluetoothRequiresLocation)

            IntroPermissionAction.OPEN_SETTINGS -> state.openAppSettings()

            IntroPermissionAction.REQUEST -> state.request()
        }
    }

    entry<Welcome> { WelcomeScreen(onGetStarted = { navigateToNext(Welcome) }) }

    entry<Bluetooth> {
        val permissions = LocalIntroPermissions.current
        BluetoothScreen(
            status = permissions.bluetooth.status,
            requiresLocation = permissions.bluetoothRequiresLocation,
            onSkip = { navigateToNext(Bluetooth, bluetoothRequiresLocation = permissions.bluetoothRequiresLocation) },
            onPrimaryAction = {
                onPrimaryAction(
                    state = permissions.bluetooth,
                    current = Bluetooth,
                    bluetoothRequiresLocation = permissions.bluetoothRequiresLocation,
                )
            },
        )
    }

    entry<Location> {
        val permissions = LocalIntroPermissions.current
        LocationScreen(
            status = permissions.location.status,
            onSkip = { navigateToNext(Location) },
            onPrimaryAction = { onPrimaryAction(permissions.location, Location) },
        )
    }

    entry<Notifications> {
        val permissions = LocalIntroPermissions.current
        val notificationPermission = permissions.notification
        // Null means the platform doesn't gate notifications at runtime, so there is nothing to configure here.
        val status = notificationPermission?.status ?: PermissionStatus.GRANTED
        NotificationsScreen(
            status = status,
            onSkip = onDone,
            onPrimaryAction = {
                if (notificationPermission == null) {
                    navigateToNext(Notifications, permissionsGranted = true)
                } else {
                    onPrimaryAction(notificationPermission, Notifications)
                }
            },
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
