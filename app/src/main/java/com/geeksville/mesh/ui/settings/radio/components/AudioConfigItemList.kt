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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.AudioConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.common.components.DropDownPreference
import com.geeksville.mesh.ui.common.components.EditTextPreference
import com.geeksville.mesh.ui.common.components.PreferenceCategory
import com.geeksville.mesh.ui.common.components.PreferenceFooter
import com.geeksville.mesh.ui.common.components.SwitchPreference
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R

@Composable
fun AudioConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(state = state.responseState, onDismiss = viewModel::clearPacketResponse)
    }

    AudioConfigItemList(
        audioConfig = state.moduleConfig.audio,
        enabled = state.connected,
        onSaveClicked = { audioInput ->
            val config = moduleConfig { audio = audioInput }
            viewModel.setModuleConfig(config)
        },
    )
}

@Suppress("LongMethod")
@Composable
fun AudioConfigItemList(audioConfig: AudioConfig, enabled: Boolean, onSaveClicked: (AudioConfig) -> Unit) {
    val focusManager = LocalFocusManager.current
    var audioInput by rememberSaveable { mutableStateOf(audioConfig) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { PreferenceCategory(text = stringResource(R.string.audio_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.codec_2_enabled),
                checked = audioInput.codec2Enabled,
                enabled = enabled,
                onCheckedChange = { audioInput = audioInput.copy { codec2Enabled = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.ptt_pin),
                value = audioInput.pttPin,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { pttPin = it } },
            )
        }

        item {
            DropDownPreference(
                title = stringResource(R.string.codec2_sample_rate),
                enabled = enabled,
                items =
                AudioConfig.Audio_Baud.entries
                    .filter { it != AudioConfig.Audio_Baud.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = audioInput.bitrate,
                onItemSelected = { audioInput = audioInput.copy { bitrate = it } },
            )
        }
        item { Divider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.i2s_word_select),
                value = audioInput.i2SWs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SWs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.i2s_data_in),
                value = audioInput.i2SSd,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SSd = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.i2s_data_out),
                value = audioInput.i2SDin,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SDin = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.i2s_clock),
                value = audioInput.i2SSck,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SSck = it } },
            )
        }

        item {
            PreferenceFooter(
                enabled = enabled && audioInput != audioConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    audioInput = audioConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(audioInput)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioConfigPreview() {
    AudioConfigItemList(audioConfig = AudioConfig.getDefaultInstance(), enabled = true, onSaveClicked = {})
}
