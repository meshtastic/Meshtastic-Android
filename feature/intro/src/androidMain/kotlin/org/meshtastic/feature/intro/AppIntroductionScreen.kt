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

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.rememberNavBackStack
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import org.meshtastic.core.ui.component.MeshtasticNavDisplay

/**
 * Main application introduction screen. This Composable hosts the navigation flow and hoists the permission states.
 *
 * @param onDone Callback invoked when the introduction flow is completed.
 * @param viewModel ViewModel for tracking the introduction flow state.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AppIntroductionScreen(onDone: () -> Unit, viewModel: IntroViewModel) {
    val notificationPermissionState: PermissionState? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            null
        }

    val locationPermissions =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    val locationPermissionState = rememberMultiplePermissionsState(permissions = locationPermissions)

    val bluetoothPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // On older versions, location permission is used for scanning.
            emptyList()
        }
    val bluetoothPermissionState = rememberMultiplePermissionsState(permissions = bluetoothPermissions)

    val backStack = rememberNavBackStack(Welcome)

    MeshtasticNavDisplay(
        backStack = backStack,
        entryProvider =
        introNavGraph(
            backStack = backStack,
            viewModel = viewModel,
            notificationPermissionState = notificationPermissionState,
            bluetoothPermissionState = bluetoothPermissionState,
            locationPermissionState = locationPermissionState,
            onDone = onDone,
        ),
    )
}
