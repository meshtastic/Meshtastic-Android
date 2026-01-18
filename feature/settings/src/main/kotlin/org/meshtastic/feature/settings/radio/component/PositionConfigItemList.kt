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
import androidx.core.location.LocationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Position
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.advanced_device_gps
import org.meshtastic.core.strings.altitude
import org.meshtastic.core.strings.broadcast_interval
import org.meshtastic.core.strings.config_position_broadcast_secs_summary
import org.meshtastic.core.strings.config_position_broadcast_smart_minimum_distance_summary
import org.meshtastic.core.strings.config_position_broadcast_smart_minimum_interval_secs_summary
import org.meshtastic.core.strings.config_position_flags_summary
import org.meshtastic.core.strings.config_position_gps_update_interval_summary
import org.meshtastic.core.strings.device_gps
import org.meshtastic.core.strings.fixed_position
import org.meshtastic.core.strings.gps_en_gpio
import org.meshtastic.core.strings.gps_mode
import org.meshtastic.core.strings.gps_receive_gpio
import org.meshtastic.core.strings.gps_transmit_gpio
import org.meshtastic.core.strings.latitude
import org.meshtastic.core.strings.longitude
import org.meshtastic.core.strings.minimum_distance
import org.meshtastic.core.strings.minimum_interval
import org.meshtastic.core.strings.position
import org.meshtastic.core.strings.position_config_set_fixed_from_phone
import org.meshtastic.core.strings.position_flags
import org.meshtastic.core.strings.position_packet
import org.meshtastic.core.strings.smart_position
import org.meshtastic.core.strings.update_interval
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
import org.meshtastic.proto.Config

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
    val positionConfig = state.radioConfig.position ?: Config.PositionConfig()
    val sanitizedPositionConfig =
        remember(positionConfig) {
            val positionItems = IntervalConfiguration.POSITION.allowedIntervals
            val smartBroadcastItems = IntervalConfiguration.SMART_BROADCAST_MINIMUM.allowedIntervals
            var updated = positionConfig
            if (FixedUpdateIntervals.fromValue((updated.position_broadcast_secs ?: 0).toLong()) == null) {
                updated = updated.copy(position_broadcast_secs = positionItems.first().value.toInt())
            }
            if (FixedUpdateIntervals.fromValue((updated.broadcast_smart_minimum_interval_secs ?: 0).toLong()) == null) {
                updated =
                    updated.copy(broadcast_smart_minimum_interval_secs = smartBroadcastItems.first().value.toInt())
            }
            if (FixedUpdateIntervals.fromValue((updated.gps_update_interval ?: 0).toLong()) == null) {
                updated = updated.copy(gps_update_interval = positionItems.first().value.toInt())
            }
            updated
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
        title = stringResource(Res.string.position),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        additionalDirtyCheck = { locationInput != currentPosition },
        onDiscard = { locationInput = currentPosition },
        onSave = {
            if (formState.value.fixed_position) {
                if (locationInput != currentPosition) {
                    viewModel.setFixedPosition(locationInput)
                }
            } else {
                if (positionConfig.fixed_position) {
                    // fixed position changed from enabled to disabled
                    viewModel.removeFixedPosition()
                }
            }
            val config = Config(position = it)
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.position_packet)) {
                val items = remember { IntervalConfiguration.POSITION_BROADCAST.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.broadcast_interval),
                    summary = stringResource(Res.string.config_position_broadcast_secs_summary),
                    enabled = state.connected,
                    items = items.map { it to it.toDisplayString() },
                    selectedItem =
                    FixedUpdateIntervals.fromValue((formState.value.position_broadcast_secs ?: 0).toLong())
                        ?: items.first(),
                    onItemSelected = {
                        formState.value = formState.value.copy(position_broadcast_secs = it.value.toInt())
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.smart_position),
                    checked = formState.value.position_broadcast_smart_enabled ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(position_broadcast_smart_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (formState.value.position_broadcast_smart_enabled ?: false) {
                    HorizontalDivider()
                    val smartItems = remember { IntervalConfiguration.SMART_BROADCAST_MINIMUM.allowedIntervals }
                    DropDownPreference(
                        title = stringResource(Res.string.minimum_interval),
                        summary =
                        stringResource(Res.string.config_position_broadcast_smart_minimum_interval_secs_summary),
                        enabled = state.connected,
                        items = smartItems.map { it to it.toDisplayString() },
                        selectedItem =
                        FixedUpdateIntervals.fromValue(
                            (formState.value.broadcast_smart_minimum_interval_secs ?: 0).toLong(),
                        ) ?: smartItems.first(),
                        onItemSelected = {
                            formState.value =
                                formState.value.copy(broadcast_smart_minimum_interval_secs = it.value.toInt())
                        },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.minimum_distance),
                        summary = stringResource(Res.string.config_position_broadcast_smart_minimum_distance_summary),
                        value = formState.value.broadcast_smart_minimum_distance ?: 0,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = {
                            formState.value = formState.value.copy(broadcast_smart_minimum_distance = it)
                        },
                    )
                }
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.device_gps)) {
                SwitchPreference(
                    title = stringResource(Res.string.fixed_position),
                    checked = formState.value.fixed_position ?: false,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(fixed_position = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                if (formState.value.fixed_position ?: false) {
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.latitude),
                        value = locationInput.latitude,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { lat: Double ->
                            if (lat >= -90 && lat <= 90.0) {
                                locationInput = locationInput.copy(latitude = lat)
                            }
                        },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.longitude),
                        value = locationInput.longitude,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { lon: Double ->
                            if (lon >= -180 && lon <= 180.0) {
                                locationInput = locationInput.copy(longitude = lon)
                            }
                        },
                    )
                    HorizontalDivider()
                    EditTextPreference(
                        title = stringResource(Res.string.altitude),
                        value = locationInput.altitude,
                        enabled = state.connected,
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        onValueChanged = { alt: Int -> locationInput = locationInput.copy(altitude = alt) },
                    )
                    HorizontalDivider()
                    TextButton(
                        enabled = state.connected,
                        onClick = { coroutineScope.launch { locationPermissionState.launchPermissionRequest() } },
                    ) {
                        Text(text = stringResource(Res.string.position_config_set_fixed_from_phone))
                    }
                } else {
                    HorizontalDivider()
                    DropDownPreference(
                        title = stringResource(Res.string.gps_mode),
                        enabled = state.connected,
                        items = Config.PositionConfig.GpsMode.entries.map { it to it.name },
                        selectedItem = formState.value.gps_mode ?: Config.PositionConfig.GpsMode.DISABLED,
                        onItemSelected = { formState.value = formState.value.copy(gps_mode = it) },
                    )
                    HorizontalDivider()
                    val items = remember { IntervalConfiguration.GPS_UPDATE.allowedIntervals }
                    DropDownPreference(
                        title = stringResource(Res.string.update_interval),
                        summary = stringResource(Res.string.config_position_gps_update_interval_summary),
                        enabled = state.connected,
                        items = items.map { it to it.toDisplayString() },
                        selectedItem =
                        FixedUpdateIntervals.fromValue((formState.value.gps_update_interval ?: 0).toLong())
                            ?: items.first(),
                        onItemSelected = {
                            formState.value = formState.value.copy(gps_update_interval = it.value.toInt())
                        },
                    )
                }
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.position_flags)) {
                BitwisePreference(
                    title = stringResource(Res.string.position_flags),
                    summary = stringResource(Res.string.config_position_flags_summary),
                    value = formState.value.position_flags ?: 0,
                    enabled = state.connected,
                    items =
                    Config.PositionConfig.PositionFlags.entries
                        .filter { it != Config.PositionConfig.PositionFlags.UNSET }
                        .map { it.value to it.name },
                    onItemSelected = { formState.value = formState.value.copy(position_flags = it) },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.advanced_device_gps)) {
                val pins = remember { gpioPins }
                DropDownPreference(
                    title = stringResource(Res.string.gps_receive_gpio),
                    enabled = state.connected,
                    items = pins,
                    selectedItem = formState.value.rx_gpio ?: 0,
                    onItemSelected = { formState.value = formState.value.copy(rx_gpio = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.gps_transmit_gpio),
                    enabled = state.connected,
                    items = pins,
                    selectedItem = formState.value.tx_gpio ?: 0,
                    onItemSelected = { formState.value = formState.value.copy(tx_gpio = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.gps_en_gpio),
                    enabled = state.connected,
                    items = pins,
                    selectedItem = formState.value.gps_en_gpio ?: 0,
                    onItemSelected = { formState.value = formState.value.copy(gps_en_gpio = it) },
                )
            }
        }
    }
}
