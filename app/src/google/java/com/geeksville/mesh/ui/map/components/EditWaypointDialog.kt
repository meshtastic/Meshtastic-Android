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
import android.app.TimePickerDialog
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.copy
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
@Composable
fun EditWaypointDialog(
    waypoint: Waypoint,
    onSendClicked: (Waypoint) -> Unit,
    onDeleteClicked: (Waypoint) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var waypointInput by remember { mutableStateOf(waypoint) }
    val title = if (waypoint.id == 0) R.string.waypoint_new else R.string.waypoint_edit
    val defaultEmoji = 0x1F4CD // 📍 Round Pushpin
    val currentEmojiCodepoint = if (waypointInput.icon == 0) defaultEmoji else waypointInput.icon
    var showEmojiPickerView by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    // Initialize date and time states from waypointInput.expire
    var selectedDateString by remember { mutableStateOf("") }
    var selectedTimeString by remember { mutableStateOf("") }
    var isExpiryEnabled by remember {
        mutableStateOf(waypointInput.expire != 0 && waypointInput.expire != Int.MAX_VALUE)
    }

    val locale = Locale.getDefault()
    val dateFormat = remember {
        if (locale.country.equals("US", ignoreCase = true)) {
            SimpleDateFormat("MM/dd/yyyy", locale)
        } else {
            SimpleDateFormat("dd/MM/yyyy", locale)
        }
    }
    val timeFormat = remember {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        if (is24Hour) {
            SimpleDateFormat("HH:mm", locale)
        } else {
            SimpleDateFormat("hh:mm a", locale)
        }
    }
    dateFormat.timeZone = TimeZone.getDefault()
    timeFormat.timeZone = TimeZone.getDefault()

    LaunchedEffect(waypointInput.expire, isExpiryEnabled) {
        if (isExpiryEnabled) {
            if (waypointInput.expire != 0 && waypointInput.expire != Int.MAX_VALUE) {
                calendar.timeInMillis = waypointInput.expire * 1000L
                selectedDateString = dateFormat.format(calendar.time)
                selectedTimeString = timeFormat.format(calendar.time)
            } else { // If enabled but not set, default to 8 hours from now
                calendar.timeInMillis = System.currentTimeMillis()
                calendar.add(Calendar.HOUR_OF_DAY, 8)
                waypointInput = waypointInput.copy { expire = (calendar.timeInMillis / 1000).toInt() }
            }
        } else {
            selectedDateString = ""
            selectedTimeString = ""
        }
    }

    if (!showEmojiPickerView) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(modifier = modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = waypointInput.name,
                        onValueChange = { waypointInput = waypointInput.copy { name = it.take(29) } },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showEmojiPickerView = true }) {
                                Text(
                                    text = String(Character.toChars(currentEmojiCodepoint)),
                                    modifier =
                                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                        .padding(6.dp),
                                    fontSize = 20.sp,
                                )
                            }
                        },
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    OutlinedTextField(
                        value = waypointInput.description,
                        onValueChange = { waypointInput = waypointInput.copy { description = it.take(99) } },
                        label = { Text(stringResource(R.string.description)) },
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { /* Handle next/done focus */ }),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.locked),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.locked))
                        }
                        Switch(
                            checked = waypointInput.lockedTo != 0,
                            onCheckedChange = { waypointInput = waypointInput.copy { lockedTo = if (it) 1 else 0 } },
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = stringResource(R.string.expires),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.expires))
                        }
                        Switch(
                            checked = isExpiryEnabled,
                            onCheckedChange = { checked ->
                                isExpiryEnabled = checked
                                if (checked) {
                                    // Default to 8 hours from now if not already set
                                    if (waypointInput.expire == 0 || waypointInput.expire == Int.MAX_VALUE) {
                                        val cal = Calendar.getInstance()
                                        cal.timeInMillis = System.currentTimeMillis()
                                        cal.add(Calendar.HOUR_OF_DAY, 8)
                                        waypointInput =
                                            waypointInput.copy { expire = (cal.timeInMillis / 1000).toInt() }
                                    }
                                    // LaunchedEffect will update date/time strings
                                } else {
                                    waypointInput = waypointInput.copy { expire = Int.MAX_VALUE }
                                }
                            },
                        )
                    }

                    if (isExpiryEnabled) {
                        val currentCalendar =
                            Calendar.getInstance().apply {
                                if (waypointInput.expire != 0 && waypointInput.expire != Int.MAX_VALUE) {
                                    timeInMillis = waypointInput.expire * 1000L
                                } else {
                                    timeInMillis = System.currentTimeMillis()
                                    add(Calendar.HOUR_OF_DAY, 8) // Default if re-enabling
                                }
                            }
                        val year = currentCalendar.get(Calendar.YEAR)
                        val month = currentCalendar.get(Calendar.MONTH)
                        val day = currentCalendar.get(Calendar.DAY_OF_MONTH)
                        val hour = currentCalendar.get(Calendar.HOUR_OF_DAY)
                        val minute = currentCalendar.get(Calendar.MINUTE)

                        val datePickerDialog =
                            DatePickerDialog(
                                context,
                                { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                                    calendar.clear()
                                    calendar.set(selectedYear, selectedMonth, selectedDay, hour, minute)
                                    waypointInput =
                                        waypointInput.copy { expire = (calendar.timeInMillis / 1000).toInt() }
                                },
                                year,
                                month,
                                day,
                            )

                        val timePickerDialog =
                            TimePickerDialog(
                                context,
                                { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                                    // Keep the existing date part
                                    val tempCal = Calendar.getInstance()
                                    tempCal.timeInMillis = waypointInput.expire * 1000L
                                    tempCal.set(Calendar.HOUR_OF_DAY, selectedHour)
                                    tempCal.set(Calendar.MINUTE, selectedMinute)
                                    waypointInput =
                                        waypointInput.copy { expire = (tempCal.timeInMillis / 1000).toInt() }
                                },
                                hour,
                                minute,
                                android.text.format.DateFormat.is24HourFormat(context),
                            )
                        Spacer(modifier = Modifier.size(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { datePickerDialog.show() }) { Text(stringResource(R.string.date)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedDateString,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { timePickerDialog.show() }) { Text(stringResource(R.string.time)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedTimeString,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (waypoint.id != 0) {
                        TextButton(
                            onClick = { onDeleteClicked(waypointInput) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f)) // Pushes delete to left and cancel/send to right
                    TextButton(onClick = onDismissRequest, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = { onSendClicked(waypointInput) }, enabled = waypointInput.name.isNotBlank()) {
                        Text(stringResource(R.string.send))
                    }
                }
            },
            dismissButton = null, // Using custom buttons in confirmButton Row
            modifier = modifier,
        )
    } else {
        EmojiPickerDialog(onDismiss = { showEmojiPickerView = false }) { selectedEmoji ->
            showEmojiPickerView = false
            waypointInput = waypointInput.copy { icon = selectedEmoji.codePointAt(0) }
        }
    }
}
