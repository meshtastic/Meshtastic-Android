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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.audio
import org.meshtastic.core.strings.audio_config
import org.meshtastic.core.strings.codec2_sample_rate
import org.meshtastic.core.strings.codec_2_enabled
import org.meshtastic.core.strings.i2s_clock
import org.meshtastic.core.strings.i2s_data_in
import org.meshtastic.core.strings.i2s_data_out
import org.meshtastic.core.strings.i2s_word_select
import org.meshtastic.core.strings.ptt_pin
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

@Composable
fun AudioConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val audioConfig = state.moduleConfig.audio ?: ModuleConfig.AudioConfig()
    val formState = rememberConfigState(initialValue = audioConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.audio),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(audio = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.audio_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.codec_2_enabled),
                    checked = formState.value.codec2_enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(codec2_enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.ptt_pin),
                    value = formState.value.ptt_pin ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(ptt_pin = it) },
                )
                DropDownPreference(
                    title = stringResource(Res.string.codec2_sample_rate),
                    enabled = state.connected,
                    items = ModuleConfig.AudioConfig.Audio_Baud.entries.map { it to it.name },
                    selectedItem = formState.value.bitrate ?: ModuleConfig.AudioConfig.Audio_Baud.CODEC2_DEFAULT,
                    onItemSelected = { formState.value = formState.value.copy(bitrate = it) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.i2s_word_select),
                    value = formState.value.i2s_ws ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(i2s_ws = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.i2s_data_in),
                    value = formState.value.i2s_sd ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(i2s_sd = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.i2s_data_out),
                    value = formState.value.i2s_din ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(i2s_din = it) },
                )
                EditTextPreference(
                    title = stringResource(Res.string.i2s_clock),
                    value = formState.value.i2s_sck ?: 0,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy(i2s_sck = it) },
                )
            }
        }
    }
}
