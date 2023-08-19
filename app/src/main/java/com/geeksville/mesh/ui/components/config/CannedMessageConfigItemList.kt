package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.CannedMessageConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.DropDownPreference
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun CannedMessageConfigItemList(
    messages: String,
    cannedMessageConfig: CannedMessageConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (messages: String, config: CannedMessageConfig) -> Unit,
) {
    var messagesInput by remember { mutableStateOf(messages) }
    var cannedMessageInput by remember { mutableStateOf(cannedMessageConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Canned Message Config") }

        item {
            SwitchPreference(title = "Canned message enabled",
                checked = cannedMessageInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { this.enabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Rotary encoder #1 enabled",
                checked = cannedMessageInput.rotary1Enabled,
                enabled = enabled,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { rotary1Enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "GPIO pin for rotary encoder A port",
                value = cannedMessageInput.inputbrokerPinA,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerPinA = it }
                })
        }

        item {
            EditTextPreference(title = "GPIO pin for rotary encoder B port",
                value = cannedMessageInput.inputbrokerPinB,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerPinB = it }
                })
        }

        item {
            EditTextPreference(title = "GPIO pin for rotary encoder Press port",
                value = cannedMessageInput.inputbrokerPinPress,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerPinPress = it }
                })
        }

        item {
            DropDownPreference(title = "Generate input event on Press",
                enabled = enabled,
                items = CannedMessageConfig.InputEventChar.values()
                    .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventPress,
                onItemSelected = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerEventPress = it }
                })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Generate input event on CW",
                enabled = enabled,
                items = CannedMessageConfig.InputEventChar.values()
                    .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventCw,
                onItemSelected = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerEventCw = it }
                })
        }
        item { Divider() }

        item {
            DropDownPreference(title = "Generate input event on CCW",
                enabled = enabled,
                items = CannedMessageConfig.InputEventChar.values()
                    .filter { it != CannedMessageConfig.InputEventChar.UNRECOGNIZED }
                    .map { it to it.name },
                selectedItem = cannedMessageInput.inputbrokerEventCcw,
                onItemSelected = {
                    cannedMessageInput = cannedMessageInput.copy { inputbrokerEventCcw = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Up/Down/Select input enabled",
                checked = cannedMessageInput.updown1Enabled,
                enabled = enabled,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { updown1Enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Allow input source",
                value = cannedMessageInput.allowInputSource,
                maxSize = 63, // allow_input_source max_size:16
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    cannedMessageInput = cannedMessageInput.copy { allowInputSource = it }
                })
        }

        item {
            SwitchPreference(title = "Send bell",
                checked = cannedMessageInput.sendBell,
                enabled = enabled,
                onCheckedChange = {
                    cannedMessageInput = cannedMessageInput.copy { sendBell = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Messages",
                value = messagesInput,
                maxSize = 200, // messages max_size:201
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { messagesInput = it }
            )
        }

        item {
            PreferenceFooter(
                enabled = cannedMessageInput != cannedMessageConfig || messagesInput != messages,
                onCancelClicked = {
                    focusManager.clearFocus()
                    messagesInput = messages
                    cannedMessageInput = cannedMessageConfig
                },
                onSaveClicked = { onSaveClicked(messagesInput,cannedMessageInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CannedMessageConfigPreview() {
    CannedMessageConfigItemList(
        messages = "",
        cannedMessageConfig = CannedMessageConfig.getDefaultInstance(),
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { _, _ -> },
    )
}
