/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProviderAdapter
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.CustomRecentEmojiProvider
import com.geeksville.mesh.waypoint

@Suppress("LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditWaypointDialog(
    waypoint: Waypoint,
    onSendClicked: (Waypoint) -> Unit,
    onDeleteClicked: (Waypoint) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var waypointInput by remember { mutableStateOf(waypoint) }
    val title = if (waypoint.id == 0) R.string.waypoint_new else R.string.waypoint_edit
    val emoji = if (waypointInput.icon == 0) 128205 else waypointInput.icon
    var showEmojiPickerView by remember { mutableStateOf(false) }

    if (!showEmojiPickerView) AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        text = {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.h6.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
                EditTextPreference(
                    title = stringResource(R.string.name),
                    value = waypointInput.name,
                    maxSize = 29, // name max_size:30
                    enabled = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { /*TODO*/ }),
                    onValueChanged = { waypointInput = waypointInput.copy { name = it } },
                    trailingIcon = {
                        IconButton(onClick = { showEmojiPickerView = true }) {
                            Text(
                                text = String(Character.toChars(emoji)),
                                modifier = Modifier
                                    .background(MaterialTheme.colors.background, CircleShape)
                                    .padding(4.dp),
                                fontSize = 24.sp,
                                color = Color.Unspecified.copy(alpha = 1f),
                            )
                        }
                    },
                )
                EditTextPreference(title = stringResource(R.string.description),
                    value = waypointInput.description,
                    maxSize = 99, // description max_size:100
                    enabled = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { /*TODO*/ }),
                    onValueChanged = { waypointInput = waypointInput.copy { description = it } }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_twotone_lock_24),
                        contentDescription = stringResource(R.string.locked),
                    )
                    Text(stringResource(R.string.locked))
                    Switch(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.End),
                        checked = waypointInput.lockedTo != 0,
                        onCheckedChange = {
                            waypointInput =
                                waypointInput.copy { lockedTo = if (it) 1 else 0 }
                        }
                    )
                }
            }
        },
        buttons = {
            FlowRow(
                modifier = modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    modifier = modifier.weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                if (waypoint.id != 0) {
                    Button(
                        modifier = modifier.weight(1f),
                        onClick = { onDeleteClicked(waypointInput) },
                        enabled = waypointInput.name.isNotEmpty(),
                    ) { Text(stringResource(R.string.delete)) }
                }
                Button(
                    modifier = modifier.weight(1f),
                    onClick = { onSendClicked(waypointInput) },
                    enabled = waypointInput.name.isNotEmpty(),
                ) { Text(stringResource(R.string.send)) }
            }
        },
    ) else {
        Column(
            verticalArrangement = Arrangement.Bottom
        ) {
            BackHandler {
                showEmojiPickerView = false
            }

            AndroidView(
                factory = { context ->
                    EmojiPickerView(context).apply {
                        clipToOutline = true
                        setRecentEmojiProvider(
                            RecentEmojiProviderAdapter(CustomRecentEmojiProvider(context))
                        )
                        setOnEmojiPickedListener { emoji ->
                            showEmojiPickerView = false
                            waypointInput = waypointInput.copy { icon = emoji.emoji.codePointAt(0) }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(MaterialTheme.colors.background)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditWaypointFormPreview() {
    AppTheme {
        EditWaypointDialog(
            waypoint = waypoint {
                id = 123
                name = "Test 123"
                description = "This is only a test"
                icon = 128169
            },
            onSendClicked = { },
            onDeleteClicked = { },
            onDismissRequest = { },
        )
    }
}
