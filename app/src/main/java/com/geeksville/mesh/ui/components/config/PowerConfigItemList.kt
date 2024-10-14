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
import com.geeksville.mesh.ConfigProtos.Config.PowerConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference

@Composable
fun PowerConfigItemList(
    powerConfig: PowerConfig,
    enabled: Boolean,
    onSaveClicked: (PowerConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var powerInput by rememberSaveable { mutableStateOf(powerConfig) }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "Power Config") }

        item {
            SwitchPreference(title = "Enable power saving mode",
                checked = powerInput.isPowerSaving,
                enabled = enabled,
                onCheckedChange = { powerInput = powerInput.copy { isPowerSaving = it } })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Shutdown on battery delay (seconds)",
                value = powerInput.onBatteryShutdownAfterSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    powerInput = powerInput.copy { onBatteryShutdownAfterSecs = it }
                })
        }

        item {
            EditTextPreference(title = "ADC multiplier override ratio",
                value = powerInput.adcMultiplierOverride,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { adcMultiplierOverride = it } })
        }

        item {
            EditTextPreference(title = "Wait for Bluetooth duration (seconds)",
                value = powerInput.waitBluetoothSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { waitBluetoothSecs = it } })
        }

        item {
            EditTextPreference(title = "Super deep sleep duration (seconds)",
                value = powerInput.sdsSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { sdsSecs = it } })
        }

        item {
            EditTextPreference(title = "Light sleep duration (seconds)",
                value = powerInput.lsSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { lsSecs = it } })
        }

        item {
            EditTextPreference(title = "Minimum wake time (seconds)",
                value = powerInput.minWakeSecs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { minWakeSecs = it } })
        }

        item {
            EditTextPreference(title = "Battery INA_2XX I2C address",
                value = powerInput.deviceBatteryInaAddress,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { powerInput = powerInput.copy { deviceBatteryInaAddress = it } })
        }

        item {
            PreferenceFooter(
                enabled = powerInput != powerConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    powerInput = powerConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(powerInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PowerConfigPreview() {
    PowerConfigItemList(
        powerConfig = PowerConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { },
    )
}
