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
package org.meshtastic.feature.settings.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_notifications
import org.meshtastic.core.resources.bluetooth_permission_rationale
import org.meshtastic.core.resources.bluetooth_permission_rationale_pre31
import org.meshtastic.core.resources.camera_permission
import org.meshtastic.core.resources.camera_permission_rationale
import org.meshtastic.core.resources.local_network_permission
import org.meshtastic.core.resources.local_network_permission_rationale
import org.meshtastic.core.resources.location_permission
import org.meshtastic.core.resources.location_permission_rationale
import org.meshtastic.core.resources.nearby_devices_permission
import org.meshtastic.core.resources.notification_permission_rationale
import org.meshtastic.core.resources.permission_camera_summary
import org.meshtastic.core.resources.permission_local_network_summary
import org.meshtastic.core.resources.permission_location_summary
import org.meshtastic.core.resources.permission_location_summary_pre31
import org.meshtastic.core.resources.permission_nearby_devices_summary
import org.meshtastic.core.resources.permission_notifications_summary
import org.meshtastic.core.resources.permission_state_allowed
import org.meshtastic.core.resources.permission_state_blocked
import org.meshtastic.core.resources.permission_state_denied
import org.meshtastic.core.resources.permission_state_not_applicable
import org.meshtastic.core.resources.permission_state_not_asked
import org.meshtastic.core.resources.permissions
import org.meshtastic.core.resources.permissions_all_allowed
import org.meshtastic.core.resources.permissions_need_attention
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.PermissionRationaleDialog
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.ExpandLess
import org.meshtastic.core.ui.icon.ExpandMore
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.LocationOn
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Notifications
import org.meshtastic.core.ui.icon.QrCodeScanner
import org.meshtastic.core.ui.util.PermissionGateAction
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState
import org.meshtastic.core.ui.util.bleScanRequiresLocationServices
import org.meshtastic.core.ui.util.permissionGateAction
import org.meshtastic.core.ui.util.rememberBluetoothPermissionState
import org.meshtastic.core.ui.util.rememberCameraPermissionState
import org.meshtastic.core.ui.util.rememberLocalNetworkPermissionState
import org.meshtastic.core.ui.util.rememberLocationPermissionState
import org.meshtastic.core.ui.util.rememberNotificationPermissionState

/**
 * One permission the app can ask for, as the permissions list needs to render it.
 *
 * @param titleRes The permission's name as the system presents it, so the row and the system screen agree.
 * @param summaryRes What the app does with it, in the user's terms.
 * @param rationaleRes The educational text shown before a re-request.
 */
private data class PermissionRow(
    val titleRes: StringResource,
    val summaryRes: StringResource,
    val rationaleRes: StringResource,
    val icon: ImageVector,
    val state: PermissionUiState,
)

/**
 * A settings surface listing every runtime permission the app uses, its current state, and a way back to it.
 *
 * This exists because the onboarding flow runs exactly once. Before it, a user who skipped or denied a screen had no
 * path back: the intro never returns, and the only recovery affordances lived inside the broken features themselves,
 * reachable only by walking into one and guessing. POST_NOTIFICATIONS was the worst case — it had precisely one request
 * site in the whole app, on a screen the user sees once, so declining it silently disabled every notification forever.
 *
 * Rows are actionable in the state the system allows: request while it will still prompt, an educational dialog first
 * on a re-request, and the app's settings page once it will not prompt at all. A granted row still opens settings,
 * because reviewing and revoking is as legitimate as granting.
 */
@Composable
internal fun ColumnScope.PermissionsSettingsContent() {
    val bluetooth = rememberBluetoothPermissionState()
    val location = rememberLocationPermissionState()
    val notifications = rememberNotificationPermissionState()
    val camera = rememberCameraPermissionState()
    val localNetwork = rememberLocalNetworkPermissionState()

    // Pre-Android-12 the BLE gate *is* ACCESS_FINE_LOCATION, so naming "Nearby devices" would point at a system screen
    // that does not exist on that device.
    val bluetoothIsLocation = bleScanRequiresLocationServices

    val rows =
        permissionRows(
            bluetooth = bluetooth,
            location = location,
            notifications = notifications,
            camera = camera,
            localNetwork = localNetwork,
            bluetoothIsLocation = bluetoothIsLocation,
        )

    // Rows that need nothing from the user are collapsed by default. Five rows reading "Allowed" is a wall every
    // healthy user scrolls past on every visit to pay for a recovery path a minority needs — so the section leads with
    // the one fact that matters and opens itself only when something is actually wrong.
    val needingAttention = rows.count { it.state.isRuntimeGated && !it.state.isGranted }
    var expanded by remember(needingAttention) { mutableStateOf(needingAttention > 0) }

    var pendingRationale by remember { mutableStateOf<PermissionRow?>(null) }

    pendingRationale?.let { row ->
        PermissionRationaleDialog(
            titleRes = row.titleRes,
            rationaleRes = row.rationaleRes,
            icon = row.icon,
            onConfirm = {
                pendingRationale = null
                row.state.request()
            },
            onDismiss = { pendingRationale = null },
        )
    }

    ExpressiveSection(title = stringResource(Res.string.permissions)) {
        ListItem(
            text = stringResource(Res.string.permissions),
            supportingText =
            if (needingAttention == 0) {
                stringResource(Res.string.permissions_all_allowed)
            } else {
                pluralStringResource(Res.plurals.permissions_need_attention, needingAttention, needingAttention)
            },
            supportingTextColor = if (needingAttention == 0) Color.Unspecified else MaterialTheme.colorScheme.error,
            leadingIcon = MeshtasticIcons.Lock,
            trailingIcon = if (expanded) MeshtasticIcons.ExpandLess else MeshtasticIcons.ExpandMore,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            rows.forEach { row -> PermissionListItem(row = row, onShowRationale = { pendingRationale = row }) }
        }
    }
}

/**
 * The permission rows to show, in the order a user is most likely to need them.
 *
 * Split out of [PermissionsSettingsContent] so that composable stays about rendering and this stays about which
 * permissions exist on the platform actually running.
 */
@Composable
private fun permissionRows(
    bluetooth: PermissionUiState,
    location: PermissionUiState,
    notifications: PermissionUiState,
    camera: PermissionUiState,
    localNetwork: PermissionUiState,
    bluetoothIsLocation: Boolean,
): List<PermissionRow> = buildList {
    // Pre-Android-12 the Bluetooth gate *is* ACCESS_FINE_LOCATION. Two rows there would offer two controls for
    // one system grant and let them contradict each other on screen, so a single Location row stands for both.
    if (bluetoothIsLocation) {
        add(
            PermissionRow(
                titleRes = Res.string.location_permission,
                summaryRes = Res.string.permission_location_summary_pre31,
                rationaleRes = Res.string.bluetooth_permission_rationale_pre31,
                icon = MeshtasticIcons.LocationOn,
                state = location,
            ),
        )
    } else {
        add(
            PermissionRow(
                titleRes = Res.string.nearby_devices_permission,
                summaryRes = Res.string.permission_nearby_devices_summary,
                rationaleRes = Res.string.bluetooth_permission_rationale,
                icon = MeshtasticIcons.Bluetooth,
                state = bluetooth,
            ),
        )
        add(
            PermissionRow(
                titleRes = Res.string.location_permission,
                summaryRes = Res.string.permission_location_summary,
                rationaleRes = Res.string.location_permission_rationale,
                icon = MeshtasticIcons.LocationOn,
                state = location,
            ),
        )
    }
    add(
        PermissionRow(
            titleRes = Res.string.app_notifications,
            summaryRes = Res.string.permission_notifications_summary,
            rationaleRes = Res.string.notification_permission_rationale,
            icon = MeshtasticIcons.Notifications,
            state = notifications,
        ),
    )
    add(
        PermissionRow(
            titleRes = Res.string.camera_permission,
            summaryRes = Res.string.permission_camera_summary,
            rationaleRes = Res.string.camera_permission_rationale,
            icon = MeshtasticIcons.QrCodeScanner,
            state = camera,
        ),
    )
    add(
        PermissionRow(
            titleRes = Res.string.local_network_permission,
            summaryRes = Res.string.permission_local_network_summary,
            rationaleRes = Res.string.local_network_permission_rationale,
            icon = MeshtasticIcons.Language,
            state = localNetwork,
        ),
    )
}

/** The one-line state a permissions row reports, kept out of the row composable's complexity budget. */
private fun permissionStateLabel(status: PermissionStatus, gated: Boolean): StringResource = when {
    !gated -> Res.string.permission_state_not_applicable
    status == PermissionStatus.GRANTED -> Res.string.permission_state_allowed
    status == PermissionStatus.NOT_REQUESTED -> Res.string.permission_state_not_asked
    status == PermissionStatus.DENIED_CAN_RETRY -> Res.string.permission_state_denied
    else -> Res.string.permission_state_blocked
}

@Composable
private fun PermissionListItem(row: PermissionRow, onShowRationale: () -> Unit) {
    val state = row.state
    val gated = state.isRuntimeGated

    val statusRes = permissionStateLabel(state.status, gated)

    // Only a blocked permission is coloured. Denied is a choice the user made and is free to keep — colouring it red
    // would read as a scolding, which the permissions guidance explicitly warns against.
    val statusColor =
        if (gated && state.status == PermissionStatus.PERMANENTLY_DENIED) {
            MaterialTheme.colorScheme.error
        } else {
            Color.Unspecified
        }

    // A chevron promises in-app navigation. Every action here that is not a request leaves for the system settings
    // app, so those rows say so instead.
    val leavesTheApp =
        gated &&
            permissionGateAction(state.status) in
            setOf(PermissionGateAction.PROCEED, PermissionGateAction.OPEN_SETTINGS)

    ListItem(
        text = stringResource(row.titleRes),
        trailingIcon =
        if (!gated) {
            null
        } else if (leavesTheApp) {
            MeshtasticIcons.AppSettingsAlt
        } else {
            null
        },
        supportingText = "${stringResource(row.summaryRes)}\n${stringResource(statusRes)}",
        supportingTextColor = statusColor,
        leadingIcon = row.icon,
        enabled = gated,
        onClick =
        if (!gated) {
            null
        } else {
            {
                when (permissionGateAction(state.status)) {
                    // Already held. Settings is still the useful destination — this is where a user comes to check
                    // or
                    // revoke, not only to grant.
                    PermissionGateAction.PROCEED -> state.openAppSettings()

                    PermissionGateAction.REQUEST -> state.request()

                    PermissionGateAction.SHOW_RATIONALE -> onShowRationale()

                    PermissionGateAction.OPEN_SETTINGS -> state.openAppSettings()
                }
            }
        },
    )
}
