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
package org.meshtastic.feature.settings.tak

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

private val SDK_INT_ANDROID_16 = Build.VERSION_CODES.BAKLAVA

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun TakPermissionHandler(isTakServerEnabled: Boolean, onPermissionResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= SDK_INT_ANDROID_16) {
        val permissionState =
            rememberPermissionState("android.permission.ACCESS_LOCAL_NETWORK") { granted ->
                // Callback fires after the system dialog is dismissed — report the result
                // directly so onPermissionResult is the single authority for grant/deny.
                if (isTakServerEnabled) onPermissionResult(granted)
            }

        LaunchedEffect(isTakServerEnabled) {
            if (isTakServerEnabled) {
                if (permissionState.status.isGranted) {
                    // Already granted — confirm immediately so the orchestrator may proceed.
                    onPermissionResult(true)
                } else {
                    // Show system dialog; result is delivered via the callback above.
                    permissionState.launchPermissionRequest()
                }
            }
        }
    } else {
        LaunchedEffect(isTakServerEnabled) { onPermissionResult(true) }
    }
}
