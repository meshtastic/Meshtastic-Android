package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.ConfigProtos.Config.PositionConfig
import com.geeksville.mesh.Position
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.BitwisePreference
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun PositionConfigItemList(
    isLocal: Boolean = false,
    location: Position?,
    positionConfig: PositionConfig,
    enabled: Boolean,
    onSaveClicked: (position: Position?, config: PositionConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var locationInput by remember(location) { mutableStateOf(location) }
    var positionInput by remember(positionConfig) { mutableStateOf(positionConfig) }

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
                    value = locationInput?.latitude ?: 0.0,
                    enabled = enabled && isLocal,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        if (value >= -90 && value <= 90.0)
                            locationInput?.let { locationInput = it.copy(latitude = value) }
                    })
            }
            item {
                EditTextPreference(title = "Longitude",
                    value = locationInput?.longitude ?: 0.0,
                    enabled = enabled && isLocal,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        if (value >= -180 && value <= 180.0)
                            locationInput?.let { locationInput = it.copy(longitude = value) }
                    })
            }
            item {
                EditTextPreference(title = "Altitude (meters)",
                    value = locationInput?.altitude ?: 0,
                    enabled = enabled && isLocal,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { value ->
                        locationInput?.let { locationInput = it.copy(altitude = value) }
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
                enabled = positionInput != positionConfig || locationInput != location,
                onCancelClicked = {
                    focusManager.clearFocus()
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
        location = null,
        positionConfig = PositionConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { _, _ -> },
    )
}
