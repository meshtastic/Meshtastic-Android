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

private const val SDK_INT_ANDROID_16 = 37

@OptIn(ExperimentalPermissionsApi::class)
@Composable
actual fun TakPermissionHandler(isTakServerEnabled: Boolean, onPermissionResult: (Boolean) -> Unit) {
    if (Build.VERSION.SDK_INT >= SDK_INT_ANDROID_16) {
        val permissionState = rememberPermissionState("android.permission.ACCESS_LOCAL_NETWORK")

        LaunchedEffect(isTakServerEnabled) {
            if (isTakServerEnabled) {
                if (!permissionState.status.isGranted) {
                    permissionState.launchPermissionRequest()
                } else {
                    onPermissionResult(true)
                }
            }
        }

        LaunchedEffect(permissionState.status.isGranted) {
            if (isTakServerEnabled) {
                onPermissionResult(permissionState.status.isGranted)
            }
        }
    } else {
        LaunchedEffect(isTakServerEnabled) { onPermissionResult(true) }
    }
}
