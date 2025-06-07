/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.map.components

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.EmojiPickerDialog
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.waypoint
import java.util.Calendar
import java.util.Locale

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

    // State to hold selected date and time
    var selectedDate by remember { mutableStateOf("") }
    var selectedTime by remember { mutableStateOf("") }
    var epochTime by remember { mutableStateOf<Long?>(null) }

    // Get current context for dialogs
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val currentTime = System.currentTimeMillis()
    calendar.timeInMillis = currentTime
    @Suppress("MagicNumber")
    calendar.add(Calendar.HOUR_OF_DAY, 8)

    // Current time for initializing pickers
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)

    if (!showEmojiPickerView) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(16.dp),
            text = {
                Column(modifier = modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleLarge.copy(
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
                        keyboardActions = KeyboardActions(onDone = { }),
                        onValueChanged = { waypointInput = waypointInput.copy { name = it } },
                        trailingIcon = {
                            IconButton(onClick = { showEmojiPickerView = true }) {
                                Text(
                                    text = String(Character.toChars(emoji)),
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.background, CircleShape)
                                        .padding(4.dp),
                                    fontSize = 24.sp,
                                    color = Color.Unspecified.copy(alpha = 1f),
                                )
                            }
                        },
                    )
                    EditTextPreference(
                        title = stringResource(R.string.description),
                        value = waypointInput.description,
                        maxSize = 99, // description max_size:100
                        enabled = true,
                        isError = false,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { }),
                        onValueChanged = { waypointInput = waypointInput.copy { description = it } }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            imageVector = Icons.Default.Lock,
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
                    val datePickerDialog = DatePickerDialog(
                        context,
                        { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                            selectedDate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                            calendar.set(selectedYear, selectedMonth, selectedDay)
                        }, year, month, day
                    )

                    val timePickerDialog = android.app.TimePickerDialog(
                        context,
                        { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                            selectedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute)
                            calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                            calendar.set(Calendar.MINUTE, selectedMinute)
                            epochTime = calendar.timeInMillis
                        }, hour, minute, true
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .size(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Image(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = stringResource(R.string.locked),
                        )
                        Text(stringResource(R.string.expires))
                        @Suppress("MagicNumber")
                        Switch(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End),
                            checked = waypointInput.expire != Int.MAX_VALUE && waypointInput.expire != 0,
                            onCheckedChange = { isChecked ->
                                waypointInput = waypointInput.copy {
                                    expire = if (isChecked) {
                                        val expiresTime = calendar.timeInMillis / 1000
                                        selectedDate =
                                            "${calendar.get(Calendar.DAY_OF_MONTH)}/" +
                                                "${calendar.get(Calendar.MONTH) + 1}/" +
                                                calendar.get(Calendar.YEAR)
                                        selectedTime = String.format(
                                            Locale.getDefault(),
                                            "%02d:%02d",
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE)
                                        )
                                        expiresTime.toInt()
                                    } else {
                                        Int.MAX_VALUE
                                    }
                                }
                            }
                        )
                    }

                    if (waypointInput.expire != Int.MAX_VALUE && waypointInput.expire != 0) {
                        Row(
                            modifier = modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            ) {
                        Button(onClick = { datePickerDialog.show() }) {
                            Text(stringResource(R.string.date))
                        }

                        Button(onClick = { timePickerDialog.show() }) {
                            Text(stringResource(R.string.time))
                        }
                    }
                }
                }
            },
            confirmButton = {
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
                        enabled = true,
                    ) { Text(stringResource(R.string.send)) }
                }
            },
        )
    } else {
        EmojiPickerDialog(onDismiss = { showEmojiPickerView = false }) {
            showEmojiPickerView = false
            waypointInput = waypointInput.copy { icon = it.codePointAt(0) }
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
                expire = 2
            },
            onSendClicked = { },
            onDeleteClicked = { },
            onDismissRequest = { },
        )
    }
}
