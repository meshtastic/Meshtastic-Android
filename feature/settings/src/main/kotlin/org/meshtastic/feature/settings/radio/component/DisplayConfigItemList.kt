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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig
import com.geeksville.mesh.config
import com.geeksville.mesh.copy
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
fun DisplayConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val displayConfig = state.radioConfig.display
    val formState = rememberConfigState(initialValue = displayConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.display),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { display = it }
            viewModel.setConfig(config)
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.display_config)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.always_point_north),
                summary = stringResource(id = R.string.config_display_compass_north_top_summary),
                checked = formState.value.compassNorthTop,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { compassNorthTop = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.use_12h_format),
                summary = stringResource(R.string.display_time_in_12h_format),
                enabled = state.connected,
                checked = formState.value.use12HClock,
                onCheckedChange = { formState.value = formState.value.copy { use12HClock = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.bold_heading),
                summary = stringResource(id = R.string.config_display_heading_bold_summary),
                checked = formState.value.headingBold,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { headingBold = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            DropDownPreference(
                title = stringResource(R.string.display_units),
                summary = stringResource(id = R.string.config_display_units_summary),
                enabled = state.connected,
                items =
                DisplayConfig.DisplayUnits.entries
                    .filter { it != DisplayConfig.DisplayUnits.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.units,
                onItemSelected = { formState.value = formState.value.copy { units = it } },
            )
        }
        item { HorizontalDivider() }

        item { PreferenceCategory(text = stringResource(R.string.advanced)) }
        item {
            EditTextPreference(
                title = stringResource(R.string.screen_on_for),
                summary = stringResource(id = R.string.config_display_screen_on_secs_summary),
                value = formState.value.screenOnSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { screenOnSecs = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.carousel_interval),
                summary = stringResource(id = R.string.config_display_auto_screen_carousel_secs_summary),
                value = formState.value.autoScreenCarouselSecs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { autoScreenCarouselSecs = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.wake_on_tap_or_motion),
                summary = stringResource(id = R.string.config_display_wake_on_tap_or_motion_summary),
                checked = formState.value.wakeOnTapOrMotion,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { wakeOnTapOrMotion = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            SwitchPreference(
                title = stringResource(R.string.flip_screen),
                summary = stringResource(id = R.string.config_display_flip_screen_summary),
                checked = formState.value.flipScreen,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { flipScreen = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            DropDownPreference(
                title = stringResource(R.string.display_mode),
                summary = stringResource(id = R.string.config_display_displaymode_summary),
                enabled = state.connected,
                items =
                DisplayConfig.DisplayMode.entries
                    .filter { it != DisplayConfig.DisplayMode.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.displaymode,
                onItemSelected = { formState.value = formState.value.copy { displaymode = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            DropDownPreference(
                title = stringResource(R.string.oled_type),
                summary = stringResource(id = R.string.config_display_oled_summary),
                enabled = state.connected,
                items =
                DisplayConfig.OledType.entries
                    .filter { it != DisplayConfig.OledType.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.oled,
                onItemSelected = { formState.value = formState.value.copy { oled = it } },
            )
        }
        item { HorizontalDivider() }
        item {
            DropDownPreference(
                title = stringResource(R.string.compass_orientation),
                enabled = state.connected,
                items =
                DisplayConfig.CompassOrientation.entries
                    .filter { it != DisplayConfig.CompassOrientation.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = formState.value.compassOrientation,
                onItemSelected = { formState.value = formState.value.copy { compassOrientation = it } },
            )
        }
        item { HorizontalDivider() }
    }
}
