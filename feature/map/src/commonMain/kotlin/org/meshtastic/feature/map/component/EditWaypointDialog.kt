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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.systemTimeZone
import org.meshtastic.core.model.geofence.GeofenceRadiusPresets
import org.meshtastic.core.model.isLocked
import org.meshtastic.core.model.util.firstCodePointOrNull
import org.meshtastic.core.model.util.toCodePointString
import org.meshtastic.core.model.util.toDistanceString
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.date
import org.meshtastic.core.resources.delete
import org.meshtastic.core.resources.description
import org.meshtastic.core.resources.expires
import org.meshtastic.core.resources.geofence
import org.meshtastic.core.resources.geofence_edit_area
import org.meshtastic.core.resources.geofence_favorites_only
import org.meshtastic.core.resources.geofence_notify_on_enter
import org.meshtastic.core.resources.geofence_notify_on_exit
import org.meshtastic.core.resources.geofence_off
import org.meshtastic.core.resources.geofence_radius
import org.meshtastic.core.resources.geofence_remove_area
import org.meshtastic.core.resources.geofence_set_area
import org.meshtastic.core.resources.locked
import org.meshtastic.core.resources.name
import org.meshtastic.core.resources.save
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.time
import org.meshtastic.core.resources.waypoint_edit
import org.meshtastic.core.resources.waypoint_new
import org.meshtastic.core.ui.emoji.EmojiPickerDialog
import org.meshtastic.core.ui.icon.CalendarMonth
import org.meshtastic.core.ui.icon.Lock
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.proto.Waypoint
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Shared waypoint editor used by both the google and fdroid map flavors (DRY — replaces the two drifted per-flavor
 * copies). Map-engine-specific concerns stay outside: drawing the box overlay and the drag-to-define gesture are
 * triggered via [onBeginBoxAuthoring], which hands the current draft back to the flavor's map so the user can define
 * the bounding box there; the flavor re-opens this dialog with the drawn box applied.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
@Composable
fun EditWaypointDialog(
    waypoint: Waypoint,
    displayUnits: MeasurementSystem,
    myNodeNum: Int?,
    onSend: (Waypoint) -> Unit,
    onDelete: (Waypoint) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    onBeginBoxAuthoring: (Waypoint) -> Unit = {},
) {
    var waypointInput by remember { mutableStateOf(waypoint) }
    val title = if (waypoint.id == 0) Res.string.waypoint_new else Res.string.waypoint_edit
    val defaultEmoji = 0x1F4CD // 📍 Round Pushpin
    val currentEmojiCodepoint = if (waypointInput.icon == 0) defaultEmoji else waypointInput.icon
    var showEmojiPickerView by remember { mutableStateOf(false) }

    val tz = systemTimeZone

    var selectedDateString by remember { mutableStateOf("") }
    var selectedTimeString by remember { mutableStateOf("") }
    var isExpiryEnabled by remember {
        mutableStateOf(waypointInput.expire != 0 && waypointInput.expire != Int.MAX_VALUE)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(waypointInput.expire, isExpiryEnabled) {
        val expireValue = waypointInput.expire
        if (isExpiryEnabled) {
            if (expireValue != 0 && expireValue != Int.MAX_VALUE) {
                val millis = kotlin.time.Instant.fromEpochSeconds(expireValue.toLong()).toEpochMilliseconds()
                selectedDateString = DateFormatter.formatDate(millis)
                selectedTimeString = DateFormatter.formatTime(millis)
            } else {
                val futureInstant = kotlin.time.Clock.System.now() + 8.hours
                selectedDateString = DateFormatter.formatDate(futureInstant.toEpochMilliseconds())
                selectedTimeString = DateFormatter.formatTime(futureInstant.toEpochMilliseconds())
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
                Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = waypointInput.name,
                        onValueChange = { waypointInput = waypointInput.copy(name = it.take(29)) },
                        label = { Text(stringResource(Res.string.name)) },
                        singleLine = true,
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { showEmojiPickerView = true }) {
                                Text(
                                    text = currentEmojiCodepoint.toCodePointString(),
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
                        onValueChange = { waypointInput = waypointInput.copy(description = it.take(99)) },
                        label = { Text(stringResource(Res.string.description)) },
                        keyboardOptions =
                        KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {}),
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
                                imageVector = MeshtasticIcons.Lock,
                                contentDescription = stringResource(Res.string.locked),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.locked))
                        }
                        Switch(
                            checked = waypointInput.isLocked,
                            // Lock to our own node so we (and only we) can later edit or remove it; unlock clears the
                            // owner. locked_to must be a real node number — a placeholder locks the waypoint to a
                            // phantom node and blocks even the creator (see #6343). Disabled until our node number is
                            // known, since without it we can't lock a waypoint to ourselves (the toggle would silently
                            // stay unlocked).
                            enabled = myNodeNum != null,
                            onCheckedChange = { locked ->
                                waypointInput = waypointInput.copy(locked_to = if (locked) myNodeNum ?: 0 else 0)
                            },
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
                                imageVector = MeshtasticIcons.CalendarMonth,
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
                                    val expireValue = waypointInput.expire
                                    if (expireValue == 0 || expireValue == Int.MAX_VALUE) {
                                        val futureInstant = kotlin.time.Clock.System.now() + 8.hours
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
                            (waypointInput.expire).let {
                                if (it != 0 && it != Int.MAX_VALUE) {
                                    kotlin.time.Instant.fromEpochSeconds(it.toLong())
                                } else {
                                    kotlin.time.Clock.System.now() + 8.hours
                                }
                            }
                        val ldt = currentInstant.toLocalDateTime(tz)

                        // The pickers are Material 3's own, so this dialog builds for desktop as well as Android.
                        // They replace `android.app.DatePickerDialog`/`TimePickerDialog`, which are what kept this
                        // file in `androidMain` and left the desktop map unable to create a waypoint at all.
                        if (showDatePicker) {
                            ExpiryDatePickerDialog(
                                initial = ldt,
                                onDismiss = { showDatePicker = false },
                                onPick = { picked ->
                                    val newLdt =
                                        picked.atTime(
                                            hour = ldt.hour,
                                            minute = ldt.minute,
                                            second = ldt.second,
                                            nanosecond = ldt.nanosecond,
                                        )
                                    waypointInput =
                                        waypointInput.copy(expire = newLdt.toInstant(tz).epochSeconds.toInt())
                                    showDatePicker = false
                                },
                            )
                        }

                        if (showTimePicker) {
                            ExpiryTimePickerDialog(
                                initial = ldt,
                                onDismiss = { showTimePicker = false },
                                onPick = { hour, minute ->
                                    val newLdt =
                                        LocalDate(year = ldt.year, month = ldt.month, day = ldt.day)
                                            .atTime(
                                                hour = hour,
                                                minute = minute,
                                                second = ldt.second,
                                                nanosecond = ldt.nanosecond,
                                            )
                                    waypointInput =
                                        waypointInput.copy(expire = newLdt.toInstant(tz).epochSeconds.toInt())
                                    showTimePicker = false
                                },
                            )
                        }

                        Spacer(modifier = Modifier.size(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { showDatePicker = true }) { Text(stringResource(Res.string.date)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedDateString,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(onClick = { showTimePicker = true }) { Text(stringResource(Res.string.time)) }
                                Text(
                                    modifier = Modifier.padding(top = 4.dp),
                                    text = selectedTimeString,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.size(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.size(8.dp))
                    GeofenceSection(
                        waypoint = waypointInput,
                        displayUnits = displayUnits,
                        onWaypointChange = { waypointInput = it },
                        onBeginBoxAuthoring = { onBeginBoxAuthoring(waypointInput) },
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (waypoint.id != 0) {
                        TextButton(onClick = { onDelete(waypointInput) }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissRequest, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(onClick = { onSend(waypointInput) }, enabled = (waypointInput.name).isNotBlank()) {
                        Text(stringResource(Res.string.send))
                    }
                }
            },
            dismissButton = null,
            modifier = modifier,
        )
    } else {
        EmojiPickerDialog(onDismiss = { showEmojiPickerView = false }) { selectedEmoji ->
            showEmojiPickerView = false
            selectedEmoji.firstCodePointOrNull()?.let { waypointInput = waypointInput.copy(icon = it) }
        }
    }
}

/**
 * Geofence authoring controls. Radius presets are locale-aware (wire value is always metres); the enter/exit toggles
 * appear only once a circle or box exists, and the favorites-only toggle only once enter or exit is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeofenceSection(
    waypoint: Waypoint,
    displayUnits: MeasurementSystem,
    onWaypointChange: (Waypoint) -> Unit,
    onBeginBoxAuthoring: () -> Unit,
) {
    val presets = remember(displayUnits) { GeofenceRadiusPresets.forUnits(displayUnits) }
    val hasBox = waypoint.bounding_box != null
    val hasRegion = waypoint.geofence_radius > 0 || hasBox

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.geofence),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(stringResource(Res.string.geofence_radius), style = MaterialTheme.typography.bodyMedium)
        // Highlight the closest preset so a radius authored on another client (e.g. an imperial/non-preset value)
        // is still shown as selected rather than appearing as "no selection" and being silently clobbered on edit.
        val selectedRadius = GeofenceRadiusPresets.nearest(waypoint.geofence_radius, displayUnits)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { meters ->
                val label =
                    if (meters == 0) {
                        stringResource(Res.string.geofence_off)
                    } else {
                        meters.toDistanceString(displayUnits)
                    }
                FilterChip(
                    selected = meters == selectedRadius,
                    onClick = {
                        onWaypointChange(waypoint.copy(geofence_radius = meters).normalizeGeofenceNotifications())
                    },
                    label = { Text(label) },
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBeginBoxAuthoring) {
                Text(stringResource(if (hasBox) Res.string.geofence_edit_area else Res.string.geofence_set_area))
            }
            if (hasBox) {
                TextButton(
                    onClick = { onWaypointChange(waypoint.copy(bounding_box = null).normalizeGeofenceNotifications()) },
                ) {
                    Text(stringResource(Res.string.geofence_remove_area))
                }
            }
        }

        if (hasRegion) {
            Spacer(modifier = Modifier.size(8.dp))
            GeofenceNotificationControls(waypoint = waypoint, onWaypointChange = onWaypointChange)
        }
    }
}

/** Enter/exit toggles plus the favorites-only toggle (visible only once enter or exit is on). */
@Composable
private fun GeofenceNotificationControls(waypoint: Waypoint, onWaypointChange: (Waypoint) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GeofenceToggleRow(
            label = stringResource(Res.string.geofence_notify_on_enter),
            checked = waypoint.notify_on_enter,
            onCheckedChange = {
                onWaypointChange(waypoint.copy(notify_on_enter = it).normalizeGeofenceNotifications())
            },
        )
        GeofenceToggleRow(
            label = stringResource(Res.string.geofence_notify_on_exit),
            checked = waypoint.notify_on_exit,
            onCheckedChange = { onWaypointChange(waypoint.copy(notify_on_exit = it).normalizeGeofenceNotifications()) },
        )
        if (waypoint.notify_on_enter || waypoint.notify_on_exit) {
            GeofenceToggleRow(
                label = stringResource(Res.string.geofence_favorites_only),
                checked = waypoint.notify_favorites_only,
                onCheckedChange = { onWaypointChange(waypoint.copy(notify_favorites_only = it)) },
            )
        }
    }
}

@Composable
private fun GeofenceToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        // The whole row is one toggleable target so the label names the switch to a screen reader and the
        // tap target spans the row, not just the thumb.
        modifier =
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * Clears geofence notification flags the UI can no longer expose, so stale values don't silently reappear when a region
 * is added again: drop all three when no region remains, and drop favorites-only when neither enter nor exit is on.
 */
private fun Waypoint.normalizeGeofenceNotifications(): Waypoint = when {
    geofence_radius <= 0 && bounding_box == null ->
        copy(notify_on_enter = false, notify_on_exit = false, notify_favorites_only = false)

    !notify_on_enter && !notify_on_exit -> copy(notify_favorites_only = false)

    else -> this
}

/**
 * The expiry date, picked with Material 3's own dialog.
 *
 * The picker works in UTC-midnight milliseconds, so the value is converted through [TimeZone.UTC] on the way in and
 * out. Reading it back in the local zone would land on the previous day for anyone west of Greenwich.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePickerDialog(initial: LocalDateTime, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
                    } ?: onDismiss()
                },
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/**
 * The expiry time of day.
 *
 * `is24Hour` is left to [rememberTimePickerState], whose default resolves the platform's own clock preference — which
 * is what `android.text.format.DateFormat.is24HourFormat` used to supply here, and what made this Android-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryTimePickerDialog(initial: LocalDateTime, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute) }) { Text(stringResource(Res.string.save)) }
        },
        title = { Text(stringResource(Res.string.time)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) {
        TimePicker(state = state)
    }
}
