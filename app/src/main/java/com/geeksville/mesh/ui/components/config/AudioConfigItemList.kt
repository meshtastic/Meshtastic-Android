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
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.AudioConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun AudioConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    AudioConfigItemList(
        audioConfig = state.moduleConfig.audio,
        enabled = state.connected,
        onSaveClicked = { audioInput ->
            val config = moduleConfig { audio = audioInput }
            viewModel.setModuleConfig(config)
        }
    )
}

@Composable
fun AudioConfigItemList(
    audioConfig: AudioConfig,
    enabled: Boolean,
    onSaveClicked: (AudioConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var audioInput by rememberSaveable { mutableStateOf(audioConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Audio Config") }

        item {
            SwitchPreference(title = "CODEC 2 enabled",
                checked = audioInput.codec2Enabled,
                enabled = enabled,
                onCheckedChange = { audioInput = audioInput.copy { codec2Enabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "PTT pin",
                value = audioInput.pttPin,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { pttPin = it } })
        }

        item {
            DropDownPreference(title = "CODEC2 sample rate",
                enabled = enabled,
                items = AudioConfig.Audio_Baud.entries
                    .filter { it != AudioConfig.Audio_Baud.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = audioInput.bitrate,
                onItemSelected = { audioInput = audioInput.copy { bitrate = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "I2S word select",
                value = audioInput.i2SWs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SWs = it } })
        }

        item {
            EditTextPreference(title = "I2S data in",
                value = audioInput.i2SSd,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SSd = it } })
        }

        item {
            EditTextPreference(title = "I2S data out",
                value = audioInput.i2SDin,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SDin = it } })
        }

        item {
            EditTextPreference(title = "I2S clock",
                value = audioInput.i2SSck,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { audioInput = audioInput.copy { i2SSck = it } })
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
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AudioConfigPreview() {
    AudioConfigItemList(
        audioConfig = AudioConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
