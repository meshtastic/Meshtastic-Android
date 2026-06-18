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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.grant_permission
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.ErrorOutline
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.PermissionUiState

/**
 * A reusable error-state card for a missing runtime permission. Generalizes the compass warning/recovery pattern so
 * every feature presents context plus a single, context-correct recovery action:
 * - [PermissionStatus.NOT_REQUESTED] / [PermissionStatus.DENIED_CAN_RETRY] — shows a "Grant permission" button that
 *   re-launches the in-context request.
 * - [PermissionStatus.PERMANENTLY_DENIED] — shows an "Open settings" button (user-initiated recovery) because the
 *   system will no longer show the dialog.
 * - [PermissionStatus.GRANTED] — renders nothing.
 *
 * @param rationale a feature-specific explanation of why the permission is needed.
 */
@Composable
fun PermissionRecoveryCard(
    status: PermissionStatus,
    rationale: String,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status == PermissionStatus.GRANTED) return

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
                    text = rationale,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (status == PermissionStatus.PERMANENTLY_DENIED) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = MeshtasticIcons.AppSettingsAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(Res.string.open_settings))
            }
        } else {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.grant_permission))
            }
        }
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
