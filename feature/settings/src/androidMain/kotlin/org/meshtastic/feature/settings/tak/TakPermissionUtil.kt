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

import androidx.compose.runtime.Composable
import org.meshtastic.core.ui.util.rememberRequestLocalNetworkPermission

@Composable
actual fun TakPermissionHandler(isTakServerEnabled: Boolean, onPermissionResult: (Boolean) -> Unit) {
    // ACCESS_LOCAL_NETWORK permission (Android 12+) is only required for TAK Server's localhost
    // socket binding (127.0.0.1:8087). It is NOT required for NSD mDNS service discovery
    // (which uses CHANGE_WIFI_MULTICAST_STATE). By gating the permission request to only when
    // TAK Server is explicitly enabled, casual users avoid the system dialog and the app works
    // without the permission if TAK is disabled.
    val requestPermission =
        rememberRequestLocalNetworkPermission(
            onGranted = { onPermissionResult(true) },
            onDenied = { onPermissionResult(false) },
        )

    if (isTakServerEnabled) {
        requestPermission()
    }
}
