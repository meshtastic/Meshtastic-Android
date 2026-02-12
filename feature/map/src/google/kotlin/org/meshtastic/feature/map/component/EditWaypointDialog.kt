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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.atTime
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.util.nowInstant
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
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import org.meshtastic.proto.Waypoint
import kotlin.time.Duration.Companion.hours

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
    val title = if (waypoint.id == 0) Res.string.waypoint_new else Res.string.waypoint_edit
    val defaultEmoji = 0x1F4CD // 📍 Round Pushpin
    val currentEmojiCodepoint = if ((waypointInput.icon ?: 0) == 0) defaultEmoji else waypointInput.icon!!
    var showEmojiPickerView by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val tz = systemTimeZone

    // Initialize date and time states from waypointInput.expire
    var selectedDateString by remember { mutableStateOf("") }
    var selectedTimeString by remember { mutableStateOf("") }
    var isExpiryEnabled by remember {
        mutableStateOf((waypointInput.expire ?: 0) != 0 && waypointInput.expire != Int.MAX_VALUE)
    }

    val dateFormat = remember { android.text.format.DateFormat.getDateFormat(context) }
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    dateFormat.timeZone = java.util.TimeZone.getDefault()
    timeFormat.timeZone = java.util.TimeZone.getDefault()

    LaunchedEffect(waypointInput.expire, isExpiryEnabled) {
        val expireValue = waypointInput.expire ?: 0
        if (isExpiryEnabled) {
            if (expireValue != 0 && expireValue != Int.MAX_VALUE) {
                val instant = Instant.fromEpochSeconds(expireValue.toLong())
                val date = instant.toDate()
                selectedDateString = dateFormat.format(date)
                selectedTimeString = timeFormat.format(date)
            } else { // If enabled but not set, default to 8 hours from now
                val futureInstant = nowInstant + 8.hours
                val date = futureInstant.toDate()
                selectedDateString = dateFormat.format(date)
                selectedTimeString = timeFormat.format(date)
                waypointInput = waypointInput.copy(expire = futureInstant.epochSeconds.toInt())
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
                        value = waypointInput.name ?: "",
                        onValueChange = { waypointInput = waypointInput.copy(name = it.take(29)) },
                        label = { Text(stringResource(Res.string.name)) },
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
                        value = waypointInput.description ?: "",
                        onValueChange = { waypointInput = waypointInput.copy(description = it.take(99)) },
                        label = { Text(stringResource(Res.string.description)) },
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
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(Res.string.locked),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.locked))
                        }
                        Switch(
                            checked = (waypointInput.locked_to ?: 0) != 0,
                            onCheckedChange = { waypointInput = waypointInput.copy(locked_to = if (it) 1 else 0) },
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
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = stringResource(Res.string.expires),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.expires))
                        }
                        Switch(
                            checked = isExpiryEnabled,
                            onCheckedChange = { checked ->
                                isExpiryEnabled = checked
                                if (checked) {
                                    val expireValue = waypointInput.expire ?: 0
                                    // Default to 8 hours from now if not already set
                                    if (expireValue == 0 || expireValue == Int.MAX_VALUE) {
                                        val futureInstant = nowInstant + 8.hours
                                        waypointInput = waypointInput.copy(expire = futureInstant.epochSeconds.toInt())
                                    }
                                } else {
                                    waypointInput = waypointInput.copy(expire = Int.MAX_VALUE)
                                }
                            },
                        )
                    }

                    if (isExpiryEnabled) {
                        val currentInstant =
                            (waypointInput.expire ?: 0).let {
                                if (it != 0 && it != Int.MAX_VALUE) {
                                    Instant.fromEpochSeconds(it.toLong())
                                } else {
                                    nowInstant + 8.hours
                                }
                            }
                        val ldt = currentInstant.toLocalDateTime(tz)

                        val datePickerDialog =
                            DatePickerDialog(
                                context,
                                { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
                                    val currentLdt =
                                        (waypointInput.expire ?: 0)
                                            .let {
                                                if (it != 0 && it != Int.MAX_VALUE) {
                                                    Instant.fromEpochSeconds(it.toLong())
                                                } else {
                                                    nowInstant + 8.hours
                                                }
                                            }
                                            .toLocalDateTime(tz)

                                    val newLdt =
                                        LocalDate(
                                            year = selectedYear,
                                            month = Month(selectedMonth + 1),
                                            day = selectedDay,
                                        )
                                            .atTime(
                                                hour = currentLdt.hour,
                                                minute = currentLdt.minute,
                                                second = currentLdt.second,
                                                nanosecond = currentLdt.nanosecond,
                                            )
                                    waypointInput =
                                        waypointInput.copy(expire = newLdt.toInstant(tz).epochSeconds.toInt())
                                },
                                ldt.year,
                                ldt.month.number - 1,
                                ldt.day,
                            )

                        val timePickerDialog =
                            TimePickerDialog(
                                context,
                                { _: TimePicker, selectedHour: Int, selectedMinute: Int ->
                                    val currentLdt =
                                        (waypointInput.expire ?: 0)
                                            .let {
                                                if (it != 0 && it != Int.MAX_VALUE) {
                                                    Instant.fromEpochSeconds(it.toLong())
                                                } else {
                                                    nowInstant + 8.hours
                                                }
                                            }
                                            .toLocalDateTime(tz)

                                    val newLdt =
                                        LocalDate(
                                            year = currentLdt.year,
                                            month = currentLdt.month,
                                            day = currentLdt.day,
                                        )
                                            .atTime(
                                                hour = selectedHour,
                                                minute = selectedMinute,
                                                second = currentLdt.second,
                                                nanosecond = currentLdt.nanosecond,
                                            )
                                    waypointInput =
                                        waypointInput.copy(expire = newLdt.toInstant(tz).epochSeconds.toInt())
                                },
                                ldt.hour,
                                ldt.minute,
                                android.text.format.DateFormat.is24HourFormat(context),
                            )
                        Spacer(modifier = Modifier.size(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { datePickerDialog.show() }) { Text(stringResource(Res.string.date)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedDateString,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { timePickerDialog.show() }) { Text(stringResource(Res.string.time)) }
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
                            Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f)) // Pushes delete to left and cancel/send to right
                    TextButton(onClick = onDismissRequest, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(
                        onClick = { onSendClicked(waypointInput) },
                        enabled = (waypointInput.name ?: "").isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.send))
                    }
                }
            },
            dismissButton = null, // Using custom buttons in confirmButton Row
            modifier = modifier,
        )
    } else {
        EmojiPickerDialog(onDismiss = { showEmojiPickerView = false }) { selectedEmoji ->
            showEmojiPickerView = false
            waypointInput = waypointInput.copy(icon = selectedEmoji.codePointAt(0))
        }
    }
}
