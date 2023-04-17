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
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.TelemetryConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun TelemetryConfigItemList(
    telemetryConfig: TelemetryConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (TelemetryConfig) -> Unit,
) {
    var telemetryInput by remember(telemetryConfig) { mutableStateOf(telemetryConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Telemetry Config") }

        item {
            EditTextPreference(title = "Device metrics update interval (seconds)",
                value = telemetryInput.deviceUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { deviceUpdateInterval = it }
                })
        }

        item {
            EditTextPreference(title = "Environment metrics update interval (seconds)",
                value = telemetryInput.environmentUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { environmentUpdateInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Environment metrics module enabled",
                checked = telemetryInput.environmentMeasurementEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentMeasurementEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Environment metrics on-screen enabled",
                checked = telemetryInput.environmentScreenEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentScreenEnabled = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Environment metrics use Fahrenheit",
                checked = telemetryInput.environmentDisplayFahrenheit,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { environmentDisplayFahrenheit = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = telemetryInput != telemetryConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    telemetryInput = telemetryConfig
                },
                onSaveClicked = { onSaveClicked(telemetryInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TelemetryConfigPreview(){
    TelemetryConfigItemList(
        telemetryConfig = TelemetryConfig.getDefaultInstance(),
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
