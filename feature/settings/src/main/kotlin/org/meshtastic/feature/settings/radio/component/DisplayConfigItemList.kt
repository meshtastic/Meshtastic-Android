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

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.advanced
import org.meshtastic.core.strings.always_point_north
import org.meshtastic.core.strings.bold_heading
import org.meshtastic.core.strings.carousel_interval
import org.meshtastic.core.strings.compass_orientation
import org.meshtastic.core.strings.config_display_auto_screen_carousel_secs_summary
import org.meshtastic.core.strings.config_display_compass_north_top_summary
import org.meshtastic.core.strings.config_display_displaymode_summary
import org.meshtastic.core.strings.config_display_flip_screen_summary
import org.meshtastic.core.strings.config_display_heading_bold_summary
import org.meshtastic.core.strings.config_display_oled_summary
import org.meshtastic.core.strings.config_display_screen_on_secs_summary
import org.meshtastic.core.strings.config_display_units_summary
import org.meshtastic.core.strings.config_display_wake_on_tap_or_motion_summary
import org.meshtastic.core.strings.display
import org.meshtastic.core.strings.display_config
import org.meshtastic.core.strings.display_mode
import org.meshtastic.core.strings.display_time_in_12h_format
import org.meshtastic.core.strings.display_units
import org.meshtastic.core.strings.flip_screen
import org.meshtastic.core.strings.oled_type
import org.meshtastic.core.strings.screen_on_for
import org.meshtastic.core.strings.use_12h_format
import org.meshtastic.core.strings.wake_on_tap_or_motion
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.Config
import org.meshtastic.proto.config

@Composable
fun DisplayConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val displayConfig = state.radioConfig.display
    val formState = rememberConfigState(initialValue = displayConfig, adapter = Config.DisplayConfig.ADAPTER)

    RadioConfigScreenList(
        title = stringResource(Res.string.display),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = config { display = it }
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.display_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.always_point_north),
                    summary = stringResource(Res.string.config_display_compass_north_top_summary),
                    checked = formState.value.compass_north_top,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(compass_north_top = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.use_12h_format),
                    summary = stringResource(Res.string.display_time_in_12h_format),
                    enabled = state.connected,
                    checked = formState.value.use_12h_clock,
                    onCheckedChange = { formState.value = formState.value.copy(use_12h_clock = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.bold_heading),
                    summary = stringResource(Res.string.config_display_heading_bold_summary),
                    checked = formState.value.heading_bold,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(heading_bold = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.display_units),
                    summary = stringResource(Res.string.config_display_units_summary),
                    enabled = state.connected,
                    items =
                    Config.DisplayConfig.DisplayUnits.entries
                        .filter { it != Config.DisplayConfig.DisplayUnits.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.units,
                    onItemSelected = { formState.value = formState.value.copy(units = it) },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.advanced)) {
                val screenOnIntervals = remember { IntervalConfiguration.DISPLAY_SCREEN_ON.allowedIntervals }
                val carouselIntervals = remember { IntervalConfiguration.DISPLAY_CAROUSEL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.screen_on_for),
                    summary = stringResource(Res.string.config_display_screen_on_secs_summary),
                    enabled = state.connected,
                    items = screenOnIntervals.map { it to it.toDisplayString() },
                    selectedItem =
                    screenOnIntervals.find { it.value == formState.value.screen_on_secs.toLong() }
                        ?: screenOnIntervals.first(),
                    onItemSelected = { formState.value = formState.value.copy(screen_on_secs = it.value.toInt()) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.carousel_interval),
                    summary = stringResource(Res.string.config_display_auto_screen_carousel_secs_summary),
                    enabled = state.connected,
                    items = carouselIntervals.map { it to it.toDisplayString() },
                    selectedItem =
                    carouselIntervals.find { it.value == formState.value.auto_screen_carousel_secs.toLong() }
                        ?: carouselIntervals.first(),
                    onItemSelected = {
                        formState.value = formState.value.copy(auto_screen_carousel_secs = it.value.toInt())
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.wake_on_tap_or_motion),
                    summary = stringResource(Res.string.config_display_wake_on_tap_or_motion_summary),
                    checked = formState.value.wake_on_tap_or_motion,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(wake_on_tap_or_motion = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.flip_screen),
                    summary = stringResource(Res.string.config_display_flip_screen_summary),
                    checked = formState.value.flip_screen,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(flip_screen = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.display_mode),
                    summary = stringResource(Res.string.config_display_displaymode_summary),
                    enabled = state.connected,
                    items =
                    Config.DisplayConfig.DisplayMode.entries
                        .filter { it != Config.DisplayConfig.DisplayMode.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.displaymode,
                    onItemSelected = { formState.value = formState.value.copy(displaymode = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.oled_type),
                    summary = stringResource(Res.string.config_display_oled_summary),
                    enabled = state.connected,
                    items =
                    Config.DisplayConfig.OledType.entries
                        .filter { it != Config.DisplayConfig.OledType.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.oled,
                    onItemSelected = { formState.value = formState.value.copy(oled = it) },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.compass_orientation),
                    enabled = state.connected,
                    items =
                    Config.DisplayConfig.CompassOrientation.entries
                        .filter { it != Config.DisplayConfig.CompassOrientation.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.compass_orientation,
                    onItemSelected = { formState.value = formState.value.copy(compass_orientation = it) },
                )
            }
        }
    }
}
