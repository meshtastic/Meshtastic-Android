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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfigProtos.ModuleConfig.AudioConfig
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig

@Composable
fun AudioConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val audioConfig = state.moduleConfig.audio
    val formState = rememberConfigState(initialValue = audioConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.audio),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { audio = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(R.string.audio_config)) {
                SwitchPreference(
                    title = stringResource(R.string.codec_2_enabled),
                    checked = formState.value.codec2Enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { codec2Enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.ptt_pin),
                    value = formState.value.pttPin,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { pttPin = it } },
                )
                DropDownPreference(
                    title = stringResource(R.string.codec2_sample_rate),
                    enabled = state.connected,
                    items =
                    AudioConfig.Audio_Baud.entries
                        .filter { it != AudioConfig.Audio_Baud.UNRECOGNIZED }
                        .map { it to it.name },
                    selectedItem = formState.value.bitrate,
                    onItemSelected = { formState.value = formState.value.copy { bitrate = it } },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(R.string.i2s_word_select),
                    value = formState.value.i2SWs,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { i2SWs = it } },
                )
                EditTextPreference(
                    title = stringResource(R.string.i2s_data_in),
                    value = formState.value.i2SSd,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { i2SSd = it } },
                )
                EditTextPreference(
                    title = stringResource(R.string.i2s_data_out),
                    value = formState.value.i2SDin,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { i2SDin = it } },
                )
                EditTextPreference(
                    title = stringResource(R.string.i2s_clock),
                    value = formState.value.i2SSck,
                    enabled = state.connected,
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { i2SSck = it } },
                )
            }
        }
    }
}
