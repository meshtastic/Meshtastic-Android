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

package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ConfigProtos.Config.PositionConfig
import com.geeksville.mesh.Position
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.BitwisePreference
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun PositionConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    val node by viewModel.destNode.collectAsStateWithLifecycle()
    val currentPosition = Position(
        latitude = node?.latitude ?: 0.0,
        longitude = node?.longitude ?: 0.0,
        altitude = node?.position?.altitude ?: 0,
        time = 1, // ignore time for fixed_position
    )

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    PositionConfigItemList(
        location = currentPosition,
        positionConfig = state.radioConfig.position,
        enabled = state.connected,
        onSaveClicked = { locationInput, positionInput ->
            if (positionInput.fixedPosition) {
                if (locationInput != currentPosition) {
                    viewModel.setFixedPosition(locationInput)
                }
            } else {
                if (state.radioConfig.position.fixedPosition) {
                    // fixed position changed from enabled to disabled
                    viewModel.removeFixedPosition()
                }
            }
            val config = config { position = positionInput }
            viewModel.setConfig(config)
        }
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun PositionConfigItemList(
    location: Position,
    positionConfig: PositionConfig,
    enabled: Boolean,
    onSaveClicked: (position: Position, config: PositionConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var locationInput by rememberSaveable { mutableStateOf(location) }
    var positionInput by rememberSaveable { mutableStateOf(positionConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Position Config") }

        item {
            EditTextPreference(title = "Position broadcast interval (seconds)",
                value = positionInput.positionBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    positionInput = positionInput.copy { positionBroadcastSecs = it }
                })
        }

        item {
            SwitchPreference(title = "Smart position enabled",
                checked = positionInput.positionBroadcastSmartEnabled,
                enabled = enabled,
                onCheckedChange = {
                    positionInput = positionInput.copy { positionBroadcastSmartEnabled = it }
                })
        }
        item { Divider() }

        if (positionInput.positionBroadcastSmartEnabled) {
            item {
                EditTextPreference(title = "Smart broadcast minimum distance (meters)",
                    value = positionInput.broadcastSmartMinimumDistance,
                    enabled = enabled,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        positionInput = positionInput.copy { broadcastSmartMinimumDistance = it }
                    })
            }

            item {
                EditTextPreference(title = "Smart broadcast minimum interval (seconds)",
                    value = positionInput.broadcastSmartMinimumIntervalSecs,
                    enabled = enabled,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = {
                        positionInput = positionInput.copy { broadcastSmartMinimumIntervalSecs = it }
                    })
            }
        }

        item {
            SwitchPreference(title = "Use fixed position",
                checked = positionInput.fixedPosition,
                enabled = enabled,
                onCheckedChange = { positionInput = positionInput.copy { fixedPosition = it } })
        }
        item { Divider() }

        if (positionInput.fixedPosition) {
            item {
                EditTextPreference(title = "Latitude",
                    value = locationInput.latitude,
                    enabled = enabled,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        if (value >= -90 && value <= 90.0) {
                            locationInput = locationInput.copy(latitude = value)
                        }
                    })
            }
            item {
                EditTextPreference(title = "Longitude",
                    value = locationInput.longitude,
                    enabled = enabled,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        if (value >= -180 && value <= 180.0) {
                            locationInput = locationInput.copy(longitude = value)
                        }
                    })
            }
            item {
                EditTextPreference(title = "Altitude (meters)",
                    value = locationInput.altitude,
                    enabled = enabled,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        locationInput = locationInput.copy(altitude = value)
                    })
            }
        }

        item {
            DropDownPreference(title = "GPS mode",
                enabled = enabled,
                items = ConfigProtos.Config.PositionConfig.GpsMode.entries
                    .filter { it != ConfigProtos.Config.PositionConfig.GpsMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = positionInput.gpsMode,
                onItemSelected = { positionInput = positionInput.copy { gpsMode = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "GPS update interval (seconds)",
                value = positionInput.gpsUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { gpsUpdateInterval = it } })
        }

        item {
            BitwisePreference(title = "Position flags",
                value = positionInput.positionFlags,
                enabled = enabled,
                items = ConfigProtos.Config.PositionConfig.PositionFlags.entries
                    .filter { it != PositionConfig.PositionFlags.UNSET && it != PositionConfig.PositionFlags.UNRECOGNIZED }
                    .map { it.number to it.name },
                onItemSelected = { positionInput = positionInput.copy { positionFlags = it } }
            )
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Redefine GPS_RX_PIN",
                value = positionInput.rxGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { rxGpio = it } })
        }

        item {
            EditTextPreference(title = "Redefine GPS_TX_PIN",
                value = positionInput.txGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { txGpio = it } })
        }

        item {
            EditTextPreference(title = "Redefine PIN_GPS_EN",
                value = positionInput.gpsEnGpio,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { positionInput = positionInput.copy { gpsEnGpio = it } })
        }

        item {
            PreferenceFooter(
                enabled = enabled && positionInput != positionConfig || locationInput != location,
                onCancelClicked = {
                    focusManager.clearFocus()
                    locationInput = location
                    positionInput = positionConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(locationInput, positionInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PositionConfigPreview() {
    PositionConfigItemList(
        location = Position(0.0, 0.0, 0),
        positionConfig = PositionConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { _, _ -> },
    )
}
