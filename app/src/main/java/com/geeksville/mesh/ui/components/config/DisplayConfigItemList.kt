/*
 * Copyright (c) 2024 Meshtastic LLC
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
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun DisplayConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    DisplayConfigItemList(
        displayConfig = state.radioConfig.display,
        enabled = state.connected,
        onSaveClicked = { displayInput ->
            val config = config { display = displayInput }
            viewModel.setConfig(config)
        }
    )
}

@Composable
fun DisplayConfigItemList(
    displayConfig: DisplayConfig,
    enabled: Boolean,
    onSaveClicked: (DisplayConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var displayInput by rememberSaveable { mutableStateOf(displayConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Display Config") }

        item {
            EditTextPreference(title = "Screen timeout (seconds)",
                value = displayInput.screenOnSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { displayInput = displayInput.copy { screenOnSecs = it } })
        }

        item {
            DropDownPreference(title = "GPS coordinates format",
                enabled = enabled,
                items = DisplayConfig.GpsCoordinateFormat.entries
                    .filter { it != DisplayConfig.GpsCoordinateFormat.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.gpsFormat,
                onItemSelected = { displayInput = displayInput.copy { gpsFormat = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Auto screen carousel (seconds)",
                value = displayInput.autoScreenCarouselSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    displayInput = displayInput.copy { autoScreenCarouselSecs = it }
                })
        }

        item {
            SwitchPreference(title = "Compass north top",
                checked = displayInput.compassNorthTop,
                enabled = enabled,
                onCheckedChange = { displayInput = displayInput.copy { compassNorthTop = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Flip screen",
                checked = displayInput.flipScreen,
                enabled = enabled,
                onCheckedChange = { displayInput = displayInput.copy { flipScreen = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Display units",
                enabled = enabled,
                items = DisplayConfig.DisplayUnits.entries
                    .filter { it != DisplayConfig.DisplayUnits.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.units,
                onItemSelected = { displayInput = displayInput.copy { units = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Override OLED auto-detect",
                enabled = enabled,
                items = DisplayConfig.OledType.entries
                    .filter { it != DisplayConfig.OledType.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.oled,
                onItemSelected = { displayInput = displayInput.copy { oled = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Display mode",
                enabled = enabled,
                items = DisplayConfig.DisplayMode.entries
                    .filter { it != DisplayConfig.DisplayMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.displaymode,
                onItemSelected = { displayInput = displayInput.copy { displaymode = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Heading bold",
                checked = displayInput.headingBold,
                enabled = enabled,
                onCheckedChange = { displayInput = displayInput.copy { headingBold = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Wake screen on tap or motion",
                checked = displayInput.wakeOnTapOrMotion,
                enabled = enabled,
                onCheckedChange = { displayInput = displayInput.copy { wakeOnTapOrMotion = it } })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Compass orientation",
                enabled = enabled,
                items = DisplayConfig.CompassOrientation.entries
                    .filter { it != DisplayConfig.CompassOrientation.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = displayInput.compassOrientation,
                onItemSelected = { displayInput = displayInput.copy { compassOrientation = it } })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = enabled && displayInput != displayConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    displayInput = displayConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(displayInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DisplayConfigPreview() {
    DisplayConfigItemList(
        displayConfig = DisplayConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
