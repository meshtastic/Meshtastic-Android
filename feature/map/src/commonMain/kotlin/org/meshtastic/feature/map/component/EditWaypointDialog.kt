/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.description
import org.meshtastic.core.resources.name
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.waypoint_edit
import org.meshtastic.core.resources.waypoint_lock_to_my_node
import org.meshtastic.core.resources.waypoint_new
import org.meshtastic.feature.map.util.convertIntToEmoji
import org.maplibre.spatialk.geojson.Position as GeoPosition

private const val MAX_NAME_LENGTH = 29
private const val MAX_DESCRIPTION_LENGTH = 99
private const val DEFAULT_EMOJI = 0x1F4CD // Round Pushpin
private const val COORDINATE_PRECISION = 1_000_000L

/**
 * Dialog for creating or editing a waypoint on the map.
 *
 * Replaces the old Android-specific `EditWaypointDialog` with a fully cross-platform Compose Multiplatform version.
 * Date/time picker for expiry is deferred (requires platform-specific pickers or CMP M3 DatePicker availability).
 */
@Suppress("LongParameterList", "LongMethod")
@Composable
fun EditWaypointDialog(
    onDismiss: () -> Unit,
    onSend: (name: String, description: String, icon: Int, locked: Boolean, expire: Int) -> Unit,
    onDelete: (() -> Unit)? = null,
    initialName: String = "",
    initialDescription: String = "",
    initialIcon: Int = DEFAULT_EMOJI,
    initialLocked: Boolean = false,
    isEditing: Boolean = false,
    position: GeoPosition? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var emojiCodepoint by remember { mutableIntStateOf(if (initialIcon != 0) initialIcon else DEFAULT_EMOJI) }
    var locked by remember { mutableStateOf(initialLocked) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(if (isEditing) Res.string.waypoint_edit else Res.string.waypoint_new),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Emoji + Name row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = convertIntToEmoji(emojiCodepoint),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= MAX_NAME_LENGTH) name = it },
                        label = { Text(stringResource(Res.string.name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= MAX_DESCRIPTION_LENGTH) description = it },
                    label = { Text(stringResource(Res.string.description)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Lock toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(Res.string.waypoint_lock_to_my_node),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = locked, onCheckedChange = { locked = it })
                }

                // Position info
                if (position != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${position.latitude.formatCoord()}, ${position.longitude.formatCoord()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                    ) {
                        Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSend(name, description, emojiCodepoint, locked, 0)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(Res.string.send))
            }
        },
    )
}

/** Format a coordinate to 6 decimal places without using JVM-only String.format(). */
private fun Double.formatCoord(): String {
    val rounded = (this * COORDINATE_PRECISION).toLong() / COORDINATE_PRECISION.toDouble()
    return rounded.toString()
}
