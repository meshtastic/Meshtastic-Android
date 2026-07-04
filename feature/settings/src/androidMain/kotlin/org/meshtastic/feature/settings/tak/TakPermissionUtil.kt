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
import androidx.compose.runtime.LaunchedEffect
import org.meshtastic.core.ui.util.isLocalNetworkPermissionGranted
import org.meshtastic.core.ui.util.rememberRequestLocalNetworkPermission

@Composable
actual fun TakPermissionHandler(isTakServerEnabled: Boolean, onPermissionResult: (Boolean) -> Unit) {
    // ACCESS_LOCAL_NETWORK runtime permission (Android 17 / API 37+) is required for the TAK Server's
    // localhost socket binding (127.0.0.1:8087). It is also required globally for NSD/mDNS device discovery
    // when targetSdk >= 37, and is requested up-front from the Connections screen, so it will usually
    // already be granted by the time the user enables TAK. This composable handles the standalone case
    // (e.g. user opens TAK settings before ever tapping the network-scan toggle).
    val isPermissionGranted = isLocalNetworkPermissionGranted()
    val requestPermission =
        rememberRequestLocalNetworkPermission(
            onGranted = { onPermissionResult(true) },
            onDenied = { onPermissionResult(false) },
        )

    // The launcher must run as a post-composition side effect — invoking it directly in the composition
    // body crashes with "Launcher has not been initialized" because the underlying
    // ActivityResultLauncherHolder is not linked to the activity until composition completes. Keying on
    // both inputs also guarantees we only re-prompt when state actually transitions, not on every
    // recomposition.
    LaunchedEffect(isTakServerEnabled, isPermissionGranted) {
        if (isTakServerEnabled && !isPermissionGranted) {
            requestPermission()
        }
    }
}
