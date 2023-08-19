package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun AmbientLightingConfigItemList(
    ambientLightingConfig: ModuleConfigProtos.ModuleConfig.AmbientLightingConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (ModuleConfigProtos.ModuleConfig.AmbientLightingConfig) -> Unit,
) {
    var ambientLightingInput by remember(ambientLightingConfig) {
        mutableStateOf(ambientLightingConfig)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Ambient Lighting Config") }

        item {
            SwitchPreference(title = "LED state",
                checked = ambientLightingInput.ledState,
                enabled = enabled,
                onCheckedChange = {
                    ambientLightingInput = ambientLightingInput.copy { ledState = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Current",
                value = ambientLightingInput.current,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    ambientLightingInput = ambientLightingInput.copy { current = it }
                })
        }

        item {
            EditTextPreference(title = "Red",
                value = ambientLightingInput.red,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { ambientLightingInput = ambientLightingInput.copy { red = it } })
        }

        item {
            EditTextPreference(title = "Green",
                value = ambientLightingInput.green,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { ambientLightingInput = ambientLightingInput.copy { green = it } })
        }

        item {
            EditTextPreference(title = "Blue",
                value = ambientLightingInput.blue,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { ambientLightingInput = ambientLightingInput.copy { blue = it } })
        }

        item {
            PreferenceFooter(
                enabled = ambientLightingInput != ambientLightingConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    ambientLightingInput = ambientLightingConfig
                },
                onSaveClicked = { onSaveClicked(ambientLightingInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AmbientLightingConfigPreview() {
    AmbientLightingConfigItemList(
        ambientLightingConfig = ModuleConfigProtos.ModuleConfig.AmbientLightingConfig.getDefaultInstance(),
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
