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
    onSaveClicked: (TelemetryConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var telemetryInput by rememberSaveable { mutableStateOf(telemetryConfig) }

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
            SwitchPreference(title = "Air quality metrics module enabled",
                checked = telemetryInput.airQualityEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { airQualityEnabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Air quality metrics update interval (seconds)",
                value = telemetryInput.airQualityInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { airQualityInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Power metrics module enabled",
                checked = telemetryInput.powerMeasurementEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { powerMeasurementEnabled = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Power metrics update interval (seconds)",
                value = telemetryInput.powerUpdateInterval,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    telemetryInput = telemetryInput.copy { powerUpdateInterval = it }
                })
        }

        item {
            SwitchPreference(title = "Power metrics on-screen enabled",
                checked = telemetryInput.powerScreenEnabled,
                enabled = enabled,
                onCheckedChange = {
                    telemetryInput = telemetryInput.copy { powerScreenEnabled = it }
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
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(telemetryInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TelemetryConfigPreview() {
    TelemetryConfigItemList(
        telemetryConfig = TelemetryConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
