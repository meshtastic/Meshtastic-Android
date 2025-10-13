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

package org.meshtastic.feature.settings.radio.component

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.core.location.LocationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.meshtastic.core.model.Position
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.BitwisePreference
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.FixedUpdateIntervals
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.gpioPins
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.ConfigProtos.Config.PositionConfig
import org.meshtastic.proto.config
import org.meshtastic.proto.copy

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PositionConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var phoneLocation: Location? by remember { mutableStateOf(null) }
    val node by viewModel.destNode.collectAsStateWithLifecycle()
    val currentPosition =
        Position(
            latitude = node?.latitude ?: 0.0,
            longitude = node?.longitude ?: 0.0,
            altitude = node?.position?.altitude ?: 0,
            time = 1, // ignore time for fixed_position
        )
    val positionConfig = state.radioConfig.position
    val sanitizedPositionConfig =
        remember(positionConfig) {
            val positionItems = IntervalConfiguration.POSITION.allowedIntervals
            val smartBroadcastItems = IntervalConfiguration.SMART_BROADCAST_MINIMUM.allowedIntervals
            positionConfig.copy {
                if (FixedUpdateIntervals.fromValue(positionBroadcastSecs.toLong()) == null) {
                    positionBroadcastSecs = positionItems.first().value.toInt()
                }
                if (FixedUpdateIntervals.fromValue(broadcastSmartMinimumIntervalSecs.toLong()) == null) {
                    broadcastSmartMinimumIntervalSecs = smartBroadcastItems.first().value.toInt()
                }
                if (FixedUpdateIntervals.fromValue(gpsUpdateInterval.toLong()) == null) {
                    gpsUpdateInterval = positionItems.first().value.toInt()
                }
            }
        }
    val formState = rememberConfigState(initialValue = sanitizedPositionConfig)
    var locationInput by rememberSaveable { mutableStateOf(currentPosition) }

    val locationPermissionState =
        rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION) { granted ->
            if (granted) {
                @SuppressLint("MissingPermission")
                coroutineScope.launch { phoneLocation = viewModel.getCurrentLocation() }
            }
        }

    LaunchedEffect(phoneLocation) {
        phoneLocation?.let { phoneLoc ->
            locationInput =
                Position(
                    latitude = phoneLoc.latitude,
                    longitude = phoneLoc.longitude,
                    altitude =
                    LocationCompat.hasMslAltitude(phoneLoc).let {
                        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            phoneLoc.mslAltitudeMeters.toInt()
                        } else {
                            phoneLoc.altitude.toInt()
                        }
                    },
                )
        }
    }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.position),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            if (formState.value.fixedPosition) {
                if (locationInput != currentPosition) {
                    viewModel.setFixedPosition(locationInput)
                }
            } else {
                if (positionConfig.fixedPosition) {
                    // fixed position changed from enabled to disabled
                    viewModel.removeFixedPosition()
                }
            }
            val config = config { position = it }
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(R.string.position_packet)) {
                val items = remember { IntervalConfiguration.BROADCAST_MEDIUM.allowedIntervals }
                DropDownPreference(
                    title = stringResource(R.string.broadcast_interval),
                    summary = stringResource(id = R.string.config_position_broadcast_secs_summary),
                    enabled = state.connected,
                    items = items.map { it to it.toDisplayString() },
                    selectedItem =
                    FixedUpdateIntervals.fromValue(formState.value.positionBroadcastSecs.toLong()) ?: items.first(),
                    onItemSelected = {
                        formState.value = formState.value.copy { positionBroadcastSecs = it.value.toInt() }
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(R.string.smart_position),
                    checked = formState.value.positionBroadcastSmartEnabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { positionBroadcastSmartEnabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (formState.value.positionBroadcastSmartEnabled) {
                    HorizontalDivider()
                    val smartItems = remember { IntervalConfiguration.SMART_BROADCAST_MINIMUM.allowedIntervals }
                    DropDownPreference(
                        title = stringResource(R.string.minimum_interval),
                        summary =
                        stringResource(id = R.string.config_position_broadcast_smart_minimum_interval_secs_summary),
                        enabled = state.connected,
                        items = smartItems.map { it to it.toDisplayString() },
                        selectedItem =
                        FixedUpdateIntervals.fromValue(formState.value.broadcastSmartMinimumIntervalSecs.toLong())
                            ?: smartItems.first(),
                        onItemSelected = {
                            formState.value =
                                formState.value.copy { broadcastSmartMinimumIntervalSecs = it.value.toInt() }
                        },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(R.string.minimum_distance),
                        summary =
                        stringResource(id = R.string.config_position_broadcast_smart_minimum_distance_summary),
                        value = formState.value.broadcastSmartMinimumDistance,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = {
                            formState.value = formState.value.copy { broadcastSmartMinimumDistance = it }
                        },
                    )
                }
            }
        }
        item {
            TitledCard(title = stringResource(R.string.device_gps)) {
                SwitchPreference(
                    title = stringResource(R.string.fixed_position),
                    checked = formState.value.fixedPosition,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { fixedPosition = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (formState.value.fixedPosition) {
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(R.string.latitude),
                        value = locationInput.latitude,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { value ->
                            if (value >= -90 && value <= 90.0) {
                                locationInput = locationInput.copy(latitude = value)
                            }
                        },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(R.string.longitude),
                        value = locationInput.longitude,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { value ->
                            if (value >= -180 && value <= 180.0) {
                                locationInput = locationInput.copy(longitude = value)
                            }
                        },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(R.string.altitude),
                        value = locationInput.altitude,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { value -> locationInput = locationInput.copy(altitude = value) },
                    )
                    HorizontalDivider()
                    TextButton(
                        enabled = state.connected,
                        onClick = { coroutineScope.launch { locationPermissionState.launchPermissionRequest() } },
                    ) {
                        Text(text = stringResource(R.string.position_config_set_fixed_from_phone))
                    }
                } else {
                    HorizontalDivider()
                    DropDownPreference(
                        title = stringResource(R.string.gps_mode),
                        enabled = state.connected,
                        items =
                        PositionConfig.GpsMode.entries
                            .filter { it != PositionConfig.GpsMode.UNRECOGNIZED }
                            .map { it to it.name },
                        selectedItem = formState.value.gpsMode,
                        onItemSelected = { formState.value = formState.value.copy { gpsMode = it } },
                    )
                    HorizontalDivider()
                    val items = remember { IntervalConfiguration.GPS_UPDATE.allowedIntervals }
                    DropDownPreference(
                        title = stringResource(R.string.update_interval),
                        summary = stringResource(id = R.string.config_position_gps_update_interval_summary),
                        enabled = state.connected,
                        items = items.map { it to it.toDisplayString() },
                        selectedItem =
                        FixedUpdateIntervals.fromValue(formState.value.gpsUpdateInterval.toLong()) ?: items.first(),
                        onItemSelected = {
                            formState.value = formState.value.copy { gpsUpdateInterval = it.value.toInt() }
                        },
                    )
                }
            }
        }
        item {
            TitledCard(title = stringResource(R.string.position_flags)) {
                BitwisePreference(
                    title = stringResource(R.string.position_flags),
                    summary = stringResource(id = R.string.config_position_flags_summary),
                    value = formState.value.positionFlags,
                    enabled = state.connected,
                    items =
                    PositionConfig.PositionFlags.entries
                        .filter {
                            it != PositionConfig.PositionFlags.UNSET &&
                                it != PositionConfig.PositionFlags.UNRECOGNIZED
                        }
                        .map { it.number to it.name },
                    onItemSelected = { formState.value = formState.value.copy { positionFlags = it } },
                )
            }
        }
        item {
            TitledCard(title = stringResource(R.string.advanced_device_gps)) {
                val pins = remember { gpioPins }
                DropDownPreference(
                    title = stringResource(R.string.gps_receive_gpio),
                    enabled = state.connected,
                    items = pins,
                    selectedItem = formState.value.rxGpio,
                    onItemSelected = { formState.value = formState.value.copy { rxGpio = it } },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(R.string.gps_transmit_gpio),
                    enabled = state.connected,
                    items = pins,
                    selectedItem = formState.value.txGpio,
                    onItemSelected = { formState.value = formState.value.copy { txGpio = it } },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(R.string.gps_en_gpio),
                    enabled = state.connected,
                    items = pins,
                    selectedItem = formState.value.gpsEnGpio,
                    onItemSelected = { formState.value = formState.value.copy { gpsEnGpio = it } },
                )
            }
        }
    }
}
