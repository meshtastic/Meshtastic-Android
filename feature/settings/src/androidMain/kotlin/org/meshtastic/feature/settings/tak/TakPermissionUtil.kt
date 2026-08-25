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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.local_network_permission
import org.meshtastic.core.resources.local_network_permission_rationale
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.resources.tak_server_permission_blocked
import org.meshtastic.core.ui.component.MeshtasticDialog
import org.meshtastic.core.ui.component.PermissionRationaleDialog
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.PermissionGateAction
import org.meshtastic.core.ui.util.permissionGateAction
import org.meshtastic.core.ui.util.rememberLocalNetworkPermissionState

@Composable
actual fun TakPermissionHandler(isTakServerEnabled: Boolean, onPermissionResult: (Boolean) -> Unit) {
    // ACCESS_LOCAL_NETWORK (Android 17 / API 37+) is required for the TAK Server's localhost socket binding
    // (127.0.0.1:8087). It is also required for NSD/mDNS device discovery when targetSdk >= 37 and is requested from
    // the Connections screen, so it will usually already be granted by the time the user enables TAK. This composable
    // handles the standalone case — a user who opens TAK settings before ever tapping the network-scan toggle.
    val permission = rememberLocalNetworkPermissionState()
    val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)

    var showRationale by remember { mutableStateOf(false) }
    var showBlocked by remember { mutableStateOf(false) }

    // The launcher must run as a post-composition side effect — invoking it directly in the composition body crashes
    // with "Launcher has not been initialized". Keying on the status enum re-runs only on real transitions.
    LaunchedEffect(isTakServerEnabled, permission.status) {
        if (!isTakServerEnabled) return@LaunchedEffect
        when (permissionGateAction(permission.status)) {
            PermissionGateAction.PROCEED -> currentOnPermissionResult(true)

            PermissionGateAction.REQUEST -> permission.request()

            // Previously this disabled the server outright, same as a permanent denial. It is not the same: the
            // system will still prompt here, and the user has been given no reason to answer differently than last
            // time. Explain first, then let them decide.
            PermissionGateAction.SHOW_RATIONALE -> showRationale = true

            // Still disables the server — it genuinely cannot bind its socket — but no longer silently. Turning a
            // feature off and saying nothing leaves the user with a switch that will not stay on and no way to learn
            // why, which is exactly the dead end this whole change set is about.
            PermissionGateAction.OPEN_SETTINGS -> {
                currentOnPermissionResult(false)
                showBlocked = true
            }
        }
    }

    if (showRationale) {
        PermissionRationaleDialog(
            titleRes = Res.string.local_network_permission,
            rationaleRes = Res.string.local_network_permission_rationale,
            icon = MeshtasticIcons.Language,
            onConfirm = {
                showRationale = false
                permission.request()
            },
            onDismiss = {
                showRationale = false
                currentOnPermissionResult(false)
            },
        )
    }

    if (showBlocked) {
        MeshtasticDialog(
            titleRes = Res.string.local_network_permission,
            messageRes = Res.string.tak_server_permission_blocked,
            icon = MeshtasticIcons.AppSettingsAlt,
            confirmTextRes = Res.string.open_settings,
            onConfirm = {
                showBlocked = false
                permission.openAppSettings()
            },
            onDismiss = { showBlocked = false },
        )
    }
}
