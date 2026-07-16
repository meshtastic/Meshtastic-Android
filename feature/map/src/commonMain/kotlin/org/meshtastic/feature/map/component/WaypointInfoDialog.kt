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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.close
import org.meshtastic.core.resources.edit
import org.meshtastic.core.resources.geofence
import org.meshtastic.core.resources.geofence_alerts_opt_in
import org.meshtastic.core.resources.geofence_alerts_opt_in_desc
import org.meshtastic.core.resources.geofence_radius
import org.meshtastic.core.resources.geofence_set_area
import org.meshtastic.core.ui.component.BasicListItem
import org.meshtastic.proto.Config.DisplayConfig.DisplayUnits
import org.meshtastic.proto.Waypoint

/**
 * Read-only view of a geofence waypoint the local device did NOT create. Editing/re-broadcasting someone else's
 * geofence is not offered here; the one interactive control is the receiver-local opt-in that decides whether THIS
 * device raises crossing notifications for it (foreign geofences are silent by default — see
 * [org.meshtastic.core.data.manager .GeofenceMonitor]). Reached for both locked and unlocked foreign geofences, so the
 * locked case gets a view at all. Unlocked foreign geofences are still editable — [onEdit], when non-null, opens the
 * full editor.
 */
@Composable
fun WaypointInfoDialog(
    waypoint: Waypoint,
    displayUnits: DisplayUnits,
    alertsEnabled: Boolean,
    onToggleAlerts: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val emoji = if (waypoint.icon == 0) PUSHPIN else waypoint.icon
    val title = waypoint.name.takeIf { it.isNotBlank() } ?: stringResource(Res.string.geofence)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "${emojiCodePointToString(emoji)}  $title", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (waypoint.description.isNotBlank()) {
                    Text(waypoint.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                if (waypoint.geofence_radius > 0) {
                    Text(
                        "${stringResource(Res.string.geofence_radius)}: " +
                            waypoint.geofence_radius.toDistanceString(displayUnits),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (waypoint.bounding_box != null) {
                    Text(stringResource(Res.string.geofence_set_area), style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.size(8.dp))
                HorizontalDivider()

                // The one interactive control: whether this device alerts on crossings of a geofence it didn't create.
                BasicListItem(
                    text = stringResource(Res.string.geofence_alerts_opt_in),
                    supportingText = stringResource(Res.string.geofence_alerts_opt_in_desc),
                    trailingContent = { Switch(checked = alertsEnabled, onCheckedChange = null) },
                    onClick = { onToggleAlerts(!alertsEnabled) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.close)) } },
        // Unlocked foreign geofences stay editable; the button is absent for locked ones.
        dismissButton =
        if (onEdit != null) {
            { TextButton(onClick = onEdit) { Text(stringResource(Res.string.edit)) } }
        } else {
            null
        },
        modifier = modifier,
    )
}

private const val PUSHPIN = 0x1F4CD // 📍 Round Pushpin
