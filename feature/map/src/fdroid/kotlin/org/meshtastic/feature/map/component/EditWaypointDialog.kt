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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.nowInstant
import org.meshtastic.core.model.util.nowSeconds
import org.meshtastic.core.model.util.systemTimeZone
import org.meshtastic.core.model.util.toDate
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.date
import org.meshtastic.core.strings.delete
import org.meshtastic.core.strings.description
import org.meshtastic.core.strings.expires
import org.meshtastic.core.strings.locked
import org.meshtastic.core.strings.name
import org.meshtastic.core.strings.send
import org.meshtastic.core.strings.time
import org.meshtastic.core.strings.waypoint_edit
import org.meshtastic.core.strings.waypoint_new
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Waypoint
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Suppress("LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditWaypointDialog(
    waypoint: Waypoint,
    onSendClicked: (Waypoint) -> Unit,
    onDeleteClicked: (Waypoint) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var waypointInput by remember { mutableStateOf(waypoint) }
    val title = if (waypoint.id == 0) Res.string.waypoint_new else Res.string.waypoint_edit

    @Suppress("MagicNumber")
    val emoji = if ((waypointInput.icon ?: 0) == 0) 128205 else waypointInput.icon!!
    var showEmojiPickerView by remember { mutableStateOf(false) }

    // Get current context for dialogs
    val context = LocalContext.current
    val tz = systemTimeZone

    // Determine locale-specific date format
    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }
    // Check if 24-hour format is preferred
    val is24Hour = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }

    val currentInstant =
        remember(waypointInput.expire) {
            val expire = waypointInput.expire ?: 0
            if (expire != 0 && expire != Int.MAX_VALUE) {
                Instant.fromEpochSeconds(expire.toLong())
            } else {
                nowInstant + 8.hours
            }
        }

    // State to hold selected date and time
    var selectedDate by
        remember(currentInstant) {
            mutableStateOf(
                if ((waypointInput.expire ?: 0) != 0 && waypointInput.expire != Int.MAX_VALUE) {
                    dateFormat.format(currentInstant.toDate())
                } else {
                    ""
                },
            )
        }
    var selectedTime by
        remember(currentInstant) {
            mutableStateOf(
                if ((waypointInput.expire ?: 0) != 0 && waypointInput.expire != Int.MAX_VALUE) {
                    timeFormat.format(currentInstant.toDate())
                } else {
                    ""
                },
            )
        }

    if (!showEmojiPickerView) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(16.dp),
            text = {
                Column(modifier = modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(title),
                        style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                    EditTextPreference(
                        title = stringResource(Res.string.name),
                        value = waypointInput.name ?: "",
                        maxSize = 29,
                        enabled = true,
                        isError = false,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {}),
                        onValueChanged = { waypointInput = waypointInput.copy(name = it) },
                        trailingIcon = {
                            IconButton(onClick = { showEmojiPickerView = true }) {
                                Text(
                                    text = String(Character.toChars(emoji)),
                                    modifier =
                                    Modifier.background(MaterialTheme.colorScheme.background, CircleShape)
                                        .padding(4.dp),
                                    fontSize = 24.sp,
                                    color = Color.Unspecified.copy(alpha = 1f),
                                )
                            }
                        },
                    )
                    EditTextPreference(
                        title = stringResource(Res.string.description),
                        value = waypointInput.description ?: "",
                        maxSize = 99,
                        enabled = true,
                        isError = false,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {}),
                        onValueChanged = { waypointInput = waypointInput.copy(description = it) },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().size(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(imageVector = Icons.Rounded.Lock, contentDescription = stringResource(Res.string.locked))
                        Text(stringResource(Res.string.locked))
                        Switch(
                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End),
                            checked = (waypointInput.locked_to ?: 0) != 0,
                            onCheckedChange = { waypointInput = waypointInput.copy(locked_to = if (it) 1 else 0) },
                        )
                    }

                    val ldt = currentInstant.toLocalDateTime(tz)
                    val datePickerDialog =
                        DatePickerDialog(
                            context,
                            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                                val newLdt =
                                    LocalDateTime(
                                        year = selectedYear,
                                        month = Month(selectedMonth + 1),
                                        day = selectedDay,
                                        hour = ldt.hour,
                                        minute = ldt.minute,
                                        second = ldt.second,
                                        nanosecond = ldt.nanosecond,
                                    )
                                waypointInput = waypointInput.copy(expire = newLdt.toInstant(tz).epochSeconds.toInt())
                            },
                            ldt.year,
                            ldt.monthNumber - 1,
                            ldt.dayOfMonth,
                        )

                    val timePickerDialog =
                        android.app.TimePickerDialog(
                            context,
                            { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                                val newLdt =
                                    LocalDateTime(
                                        year = ldt.year,
                                        month = ldt.month,
                                        day = ldt.dayOfMonth,
                                        hour = selectedHour,
                                        minute = selectedMinute,
                                        second = ldt.second,
                                        nanosecond = ldt.nanosecond,
                                    )
                                waypointInput = waypointInput.copy(expire = newLdt.toInstant(tz).epochSeconds.toInt())
                            },
                            ldt.hour,
                            ldt.minute,
                            is24Hour,
                        )

                    Row(
                        modifier = Modifier.fillMaxWidth().size(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = stringResource(Res.string.expires),
                        )
                        Text(stringResource(Res.string.expires))
                        Switch(
                            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End),
                            checked = waypointInput.expire != Int.MAX_VALUE && (waypointInput.expire ?: 0) != 0,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    waypointInput = waypointInput.copy(expire = currentInstant.epochSeconds.toInt())
                                } else {
                                    waypointInput = waypointInput.copy(expire = Int.MAX_VALUE)
                                }
                            },
                        )
                    }

                    if (waypointInput.expire != Int.MAX_VALUE && (waypointInput.expire ?: 0) != 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { datePickerDialog.show() }) { Text(stringResource(Res.string.date)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedDate,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { timePickerDialog.show() }) { Text(stringResource(Res.string.time)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedTime,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
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
                    TextButton(modifier = modifier.weight(1f), onClick = onDismissRequest) {
                        Text(stringResource(Res.string.cancel))
                    }
                    if (waypoint.id != 0) {
                        Button(
                            modifier = modifier.weight(1f),
                            onClick = { onDeleteClicked(waypointInput) },
                            enabled = !(waypointInput.name.isNullOrEmpty()),
                        ) {
                            Text(stringResource(Res.string.delete))
                        }
                    }
                    Button(modifier = modifier.weight(1f), onClick = { onSendClicked(waypointInput) }, enabled = true) {
                        Text(stringResource(Res.string.send))
                    }
                }
            },
        )
    } else {
        EmojiPickerDialog(onDismiss = { showEmojiPickerView = false }) {
            showEmojiPickerView = false
            waypointInput = waypointInput.copy(icon = it.codePointAt(0))
        }
    }
}

@Preview(showBackground = true)
@Composable
@Suppress("MagicNumber")
private fun EditWaypointFormPreview() {
    AppTheme {
        EditWaypointDialog(
            waypoint =
            Waypoint(
                id = 123,
                name = "Test 123",
                description = "This is only a test",
                icon = 128169,
                expire = (nowSeconds.toInt() + 8 * 3600),
            ),
            onSendClicked = {},
            onDeleteClicked = {},
            onDismissRequest = {},
        )
    }
}
