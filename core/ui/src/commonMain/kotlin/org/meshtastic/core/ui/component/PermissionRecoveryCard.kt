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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.grant_permission
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.ErrorOutline
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState

/**
 * A reusable error-state card: an `errorContainer` message box plus one full-width recovery action. Generalizes the
 * compass warning/recovery pattern so any feature can present context plus a single corrective action (request a
 * permission, open Bluetooth/Wi-Fi/app settings, etc.).
 *
 * @param message the user-facing explanation of what is wrong.
 * @param actionLabel the recovery button label.
 * @param onAction invoked when the recovery button is tapped.
 * @param actionIcon optional leading icon for the recovery button.
 * @param onDismiss optional dismiss handler; when non-null a trailing close (×) button is shown so the user can retire
 *   a card that can't otherwise be cleared (e.g. a firmware recovery that persistently fails on an unflashable
 *   bootloader). Omit for cards that must not be dismissable, such as missing-permission prompts.
 * @param dismissContentDescription accessibility label for the dismiss button.
 */
@Composable
fun RecoveryCard(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    onDismiss: (() -> Unit)? = null,
    dismissContentDescription: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = MeshtasticIcons.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = MeshtasticIcons.Close,
                            contentDescription = dismissContentDescription,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
            if (actionIcon != null) {
                Icon(imageVector = actionIcon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = actionLabel)
        }
    }
}

/**
 * A [RecoveryCard] specialized for a missing runtime permission, presenting a context-correct recovery action:
 * - [PermissionStatus.NOT_REQUESTED] / [PermissionStatus.DENIED_CAN_RETRY] — shows a "Grant permission" button that
 *   re-launches the in-context request.
 * - [PermissionStatus.PERMANENTLY_DENIED] — shows an "Open settings" button (user-initiated recovery) because the
 *   system will no longer show the dialog.
 * - [PermissionStatus.GRANTED] — renders nothing.
 *
 * @param rationale a feature-specific explanation of why the permission is needed.
 */
@Composable
internal fun PermissionRecoveryCard(
    status: PermissionStatus,
    rationale: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == PermissionStatus.GRANTED) return

    if (status == PermissionStatus.PERMANENTLY_DENIED) {
        RecoveryCard(
            message = rationale,
            actionLabel = stringResource(Res.string.open_settings),
            onAction = onOpenSettings,
            modifier = modifier,
            actionIcon = MeshtasticIcons.AppSettingsAlt,
        )
    } else {
        RecoveryCard(
            message = rationale,
            actionLabel = stringResource(Res.string.grant_permission),
            onAction = onRequest,
            modifier = modifier,
        )
    }
}

/** Convenience overload that reads the status and actions directly from a [PermissionUiState]. */
@Composable
fun PermissionRecoveryCard(state: PermissionUiState, rationale: String, modifier: Modifier = Modifier) {
    PermissionRecoveryCard(
        status = state.status,
        rationale = rationale,
        onRequest = state.request,
        onOpenSettings = state.openAppSettings,
        modifier = modifier,
    )
}
