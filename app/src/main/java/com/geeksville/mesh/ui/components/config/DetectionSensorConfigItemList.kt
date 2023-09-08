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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.ModuleConfigProtos
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun DetectionSensorConfigItemList(
    detectionSensorConfig: ModuleConfigProtos.ModuleConfig.DetectionSensorConfig,
    enabled: Boolean,
    onSaveClicked: (ModuleConfigProtos.ModuleConfig.DetectionSensorConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var detectionSensorInput by remember(detectionSensorConfig) {
        mutableStateOf(detectionSensorConfig)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Detection Sensor Config") }

        item {
            SwitchPreference(title = "Detection Sensor enabled",
                checked = detectionSensorInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    detectionSensorInput = detectionSensorInput.copy { this.enabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Minimum broadcast (seconds)",
                value = detectionSensorInput.minimumBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    detectionSensorInput = detectionSensorInput.copy { minimumBroadcastSecs = it }
                })
        }

        item {
            EditTextPreference(title = "State broadcast (seconds)",
                value = detectionSensorInput.stateBroadcastSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    detectionSensorInput = detectionSensorInput.copy { stateBroadcastSecs = it }
                })
        }

        item {
            SwitchPreference(title = "Send bell with alert message",
                checked = detectionSensorInput.sendBell,
                enabled = enabled,
                onCheckedChange = {
                    detectionSensorInput = detectionSensorInput.copy { sendBell = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Friendly name",
                value = detectionSensorInput.name,
                maxSize = 19, // name max_size:20
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    detectionSensorInput = detectionSensorInput.copy { name = it }
                })
        }

        item {
            EditTextPreference(title = "GPIO pin to monitor",
                value = detectionSensorInput.monitorPin,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    detectionSensorInput = detectionSensorInput.copy { monitorPin = it }
                })
        }

        item {
            SwitchPreference(title = "Detection is triggered on HIGH (1)",
                checked = detectionSensorInput.detectionTriggeredHigh,
                enabled = enabled,
                onCheckedChange = {
                    detectionSensorInput = detectionSensorInput.copy { detectionTriggeredHigh = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Use INPUT_PULLUP mode",
                checked = detectionSensorInput.usePullup,
                enabled = enabled,
                onCheckedChange = {
                    detectionSensorInput = detectionSensorInput.copy { usePullup = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = detectionSensorInput != detectionSensorConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    detectionSensorInput = detectionSensorConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(detectionSensorInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DetectionSensorConfigPreview() {
    DetectionSensorConfigItemList(
        detectionSensorConfig = ModuleConfigProtos.ModuleConfig.DetectionSensorConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
