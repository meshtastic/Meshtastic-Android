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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.advanced
import org.meshtastic.core.resources.display
import org.meshtastic.core.resources.display_config
import org.meshtastic.core.resources.schema_display_auto_screen_carousel_secs
import org.meshtastic.core.resources.schema_display_auto_screen_carousel_secs_description
import org.meshtastic.core.resources.schema_display_compass_north_top
import org.meshtastic.core.resources.schema_display_compass_north_top_description
import org.meshtastic.core.resources.schema_display_compass_orientation
import org.meshtastic.core.resources.schema_display_displaymode
import org.meshtastic.core.resources.schema_display_displaymode_description
import org.meshtastic.core.resources.schema_display_flip_screen
import org.meshtastic.core.resources.schema_display_flip_screen_description
import org.meshtastic.core.resources.schema_display_heading_bold
import org.meshtastic.core.resources.schema_display_heading_bold_description
import org.meshtastic.core.resources.schema_display_oled
import org.meshtastic.core.resources.schema_display_oled_description
import org.meshtastic.core.resources.schema_display_screen_on_secs
import org.meshtastic.core.resources.schema_display_screen_on_secs_description
import org.meshtastic.core.resources.schema_display_units
import org.meshtastic.core.resources.schema_display_units_description
import org.meshtastic.core.resources.schema_display_use_12h_clock
import org.meshtastic.core.resources.schema_display_use_12h_clock_description
import org.meshtastic.core.resources.schema_display_wake_on_tap_or_motion
import org.meshtastic.core.resources.schema_display_wake_on_tap_or_motion_description
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.Config
import org.meshtastic.proto.compass_north_top
import org.meshtastic.proto.use_12h_clock

@Suppress("DEPRECATION", "LongMethod")
@Composable
fun DisplayConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val firmwareVersion = state.metadata?.firmware_version
    val capabilities = remember(firmwareVersion) { Capabilities(firmwareVersion) }
    val displayConfig = state.radioConfig.display ?: Config.DisplayConfig.Builder().build()
    val formState = rememberConfigState(initialValue = displayConfig)

    RadioConfigScreenList(
        title = stringResource(Res.string.display),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = Config.Builder().also { wb -> wb.display = it }.build()
            viewModel.setConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.display_config)) {
                if (
                    capabilities.offers(
                        Config.DisplayConfig.compass_north_top,
                        isSet = formState.value.compass_north_top,
                    )
                ) {
                    SwitchPreference(
                        title = stringResource(Res.string.schema_display_compass_north_top),
                        summary = stringResource(Res.string.schema_display_compass_north_top_description),
                        checked = formState.value.compass_north_top,
                        enabled = state.connected,
                        onCheckedChange = {
                            formState.value =
                                formState.value.newBuilder().also { wb -> wb.compass_north_top = it }.build()
                        },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                    HorizontalDivider()
                }
                if (capabilities.offers(Config.DisplayConfig.use_12h_clock)) {
                    SwitchPreference(
                        title = stringResource(Res.string.schema_display_use_12h_clock),
                        summary = stringResource(Res.string.schema_display_use_12h_clock_description),
                        enabled = state.connected,
                        checked = formState.value.use_12h_clock,
                        onCheckedChange = {
                            formState.value = formState.value.newBuilder().also { wb -> wb.use_12h_clock = it }.build()
                        },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                    HorizontalDivider()
                }
                SwitchPreference(
                    title = stringResource(Res.string.schema_display_heading_bold),
                    summary = stringResource(Res.string.schema_display_heading_bold_description),
                    checked = formState.value.heading_bold,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.heading_bold = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_display_units),
                    summary = stringResource(Res.string.schema_display_units_description),
                    enabled = state.connected,
                    selectedItem = formState.value.units,
                    onItemSelected = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.units = it }.build()
                    },
                )
            }
        }
        item {
            TitledCard(title = stringResource(Res.string.advanced)) {
                val screenOnIntervals = remember { IntervalConfiguration.DISPLAY_SCREEN_ON.allowedIntervals }
                val carouselIntervals = remember { IntervalConfiguration.DISPLAY_CAROUSEL.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.schema_display_screen_on_secs),
                    summary = stringResource(Res.string.schema_display_screen_on_secs_description),
                    enabled = state.connected,
                    items = screenOnIntervals.map { it to it.toDisplayString() },
                    selectedItem =
                    screenOnIntervals.find { it.value == formState.value.screen_on_secs.toLong() }
                        ?: screenOnIntervals.first(),
                    onItemSelected = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.screen_on_secs = it.value.toInt() }.build()
                    },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_display_auto_screen_carousel_secs),
                    summary = stringResource(Res.string.schema_display_auto_screen_carousel_secs_description),
                    enabled = state.connected,
                    items = carouselIntervals.map { it to it.toDisplayString() },
                    selectedItem =
                    carouselIntervals.find { it.value == formState.value.auto_screen_carousel_secs.toLong() }
                        ?: carouselIntervals.first(),
                    onItemSelected = {
                        formState.value =
                            formState.value
                                .newBuilder()
                                .also { wb -> wb.auto_screen_carousel_secs = it.value.toInt() }
                                .build()
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.schema_display_wake_on_tap_or_motion),
                    summary = stringResource(Res.string.schema_display_wake_on_tap_or_motion_description),
                    checked = formState.value.wake_on_tap_or_motion,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.wake_on_tap_or_motion = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.schema_display_flip_screen),
                    summary = stringResource(Res.string.schema_display_flip_screen_description),
                    checked = formState.value.flip_screen,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.flip_screen = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_display_displaymode),
                    summary = stringResource(Res.string.schema_display_displaymode_description),
                    enabled = state.connected,
                    selectedItem = formState.value.displaymode,
                    onItemSelected = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.displaymode = it }.build()
                    },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_display_oled),
                    summary = stringResource(Res.string.schema_display_oled_description),
                    enabled = state.connected,
                    selectedItem = formState.value.oled,
                    onItemSelected = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.oled = it }.build()
                    },
                )
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.schema_display_compass_orientation),
                    enabled = state.connected,
                    selectedItem = formState.value.compass_orientation,
                    onItemSelected = {
                        formState.value =
                            formState.value.newBuilder().also { wb -> wb.compass_orientation = it }.build()
                    },
                )
            }
        }
    }
}
