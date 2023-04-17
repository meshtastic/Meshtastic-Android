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
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.MQTTConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun MQTTConfigItemList(
    mqttConfig: MQTTConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (MQTTConfig) -> Unit,
) {
    var mqttInput by remember(mqttConfig) { mutableStateOf(mqttConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "MQTT Config") }

        item {
            SwitchPreference(title = "MQTT enabled",
                checked = mqttInput.enabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { this.enabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Address",
                value = mqttInput.address,
                maxSize = 63, // address max_size:64
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { address = it } })
        }

        item {
            EditTextPreference(title = "Username",
                value = mqttInput.username,
                maxSize = 63, // username max_size:64
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { username = it } })
        }

        item {
            EditTextPreference(title = "Password",
                value = mqttInput.password,
                maxSize = 63, // password max_size:64
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { password = it } })
        }

        item {
            SwitchPreference(title = "Encryption enabled",
                checked = mqttInput.encryptionEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { encryptionEnabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "JSON output enabled",
                checked = mqttInput.jsonEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { jsonEnabled = it } })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "TLS enabled",
                checked = mqttInput.tlsEnabled,
                enabled = enabled,
                onCheckedChange = { mqttInput = mqttInput.copy { tlsEnabled = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Root topic",
                value = mqttInput.root,
                maxSize = 15, // root max_size:16
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { mqttInput = mqttInput.copy { root = it } })
        }

        item {
            PreferenceFooter(
                enabled = mqttInput != mqttConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    mqttInput = mqttConfig
                },
                onSaveClicked = { onSaveClicked(mqttInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MQTTConfigPreview(){
    MQTTConfigItemList(
        mqttConfig = MQTTConfig.getDefaultInstance(),
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
