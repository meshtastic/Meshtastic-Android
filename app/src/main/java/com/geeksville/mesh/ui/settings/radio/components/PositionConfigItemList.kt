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

package com.geeksville.mesh.ui.settings.radio.components

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.os.Build
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.navigation.NavController
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ConfigProtos.Config.PositionConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import org.meshtastic.core.ui.component.BitwisePreference
import com.geeksville.mesh.ui.common.components.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.meshtastic.core.model.Position
import org.meshtastic.core.strings.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PositionConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
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
    val formState = rememberConfigState(initialValue = positionConfig)
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
        onBack = { navController.popBackStack() },
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
        item { PreferenceCategory(text = stringResource(R.string.position_packet)) }

        item {
            EditTextPreference(
                title = stringResource(R.string.broadcast_interval),
                summary = stringResource(id = R.string.config_position_broadcast_secs_summary),
                value = formState.value.positionBroadcastSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { positionBroadcastSecs = it } },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.smart_position),
                checked = formState.value.positionBroadcastSmartEnabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { positionBroadcastSmartEnabled = it } },
            )
        }
        item { HorizontalDivider() }

        if (formState.value.positionBroadcastSmartEnabled) {
            item {
                EditTextPreference(
                    title = stringResource(R.string.minimum_interval),
                    summary =
                    stringResource(id = R.string.config_position_broadcast_smart_minimum_interval_secs_summary),
                    value = formState.value.broadcastSmartMinimumIntervalSecs,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        formState.value = formState.value.copy { broadcastSmartMinimumIntervalSecs = it }
                    },
                )
            }
            item {
                EditTextPreference(
                    title = stringResource(R.string.minimum_distance),
                    summary = stringResource(id = R.string.config_position_broadcast_smart_minimum_distance_summary),
                    value = formState.value.broadcastSmartMinimumDistance,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { broadcastSmartMinimumDistance = it } },
                )
            }
        }
        item { PreferenceCategory(text = stringResource(R.string.device_gps)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.fixed_position),
                checked = formState.value.fixedPosition,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { fixedPosition = it } },
            )
        }
        item { HorizontalDivider() }

        if (formState.value.fixedPosition) {
            item {
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
            }
            item {
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
            }
            item {
                EditTextPreference(
                    title = stringResource(R.string.altitude),
                    value = locationInput.altitude,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value -> locationInput = locationInput.copy(altitude = value) },
                )
            }
            item {
                TextButton(
                    enabled = state.connected,
                    onClick = { coroutineScope.launch { locationPermissionState.launchPermissionRequest() } },
                ) {
                    Text(text = stringResource(R.string.position_config_set_fixed_from_phone))
                }
            }
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.gps_mode),
                enabled = state.connected,
                items =
                ConfigProtos.Config.PositionConfig.GpsMode.entries
                    .filter { it != ConfigProtos.Config.PositionConfig.GpsMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.gpsMode,
                onItemSelected = { formState.value = formState.value.copy { gpsMode = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.update_interval),
                summary = stringResource(id = R.string.config_position_gps_update_interval_summary),
                value = formState.value.gpsUpdateInterval,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { gpsUpdateInterval = it } },
            )
        }
        item { PreferenceCategory(text = stringResource(R.string.position_flags)) }
        item {
            BitwisePreference(
                title = stringResource(R.string.position_flags),
                summary = stringResource(id = R.string.config_position_flags_summary),
                value = formState.value.positionFlags,
                enabled = state.connected,
                items =
                ConfigProtos.Config.PositionConfig.PositionFlags.entries
                    .filter {
                        it != PositionConfig.PositionFlags.UNSET && it != PositionConfig.PositionFlags.UNRECOGNIZED
                    }
                    .map { it.number to it.name },
                onItemSelected = { formState.value = formState.value.copy { positionFlags = it } },
            )
        }
        item { HorizontalDivider() }
        item { PreferenceCategory(text = stringResource(R.string.advanced_device_gps)) }

        item {
            EditTextPreference(
                title = stringResource(R.string.gps_receive_gpio),
                value = formState.value.rxGpio,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { rxGpio = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.gps_transmit_gpio),
                value = formState.value.txGpio,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { txGpio = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.gps_en_gpio),
                value = formState.value.gpsEnGpio,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { gpsEnGpio = it } },
            )
        }
    }
}
