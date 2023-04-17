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
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.ExternalNotificationConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference
import com.geeksville.mesh.ui.components.TextDividerPreference

@Composable
fun ExternalNotificationConfigItemList(
    externalNotificationConfig: ExternalNotificationConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (ExternalNotificationConfig) -> Unit,
) {
    var externalNotificationInput by remember(externalNotificationConfig) {
        mutableStateOf(externalNotificationConfig)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item { PreferenceCategory(text = "External Notification Config") }

        item {
            SwitchPreference(title = "External notification enabled",
                checked = externalNotificationInput.enabled,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { this.enabled = it }
                })
        }

        item { TextDividerPreference("Notifications on message receipt", enabled = enabled) }

        item {
            SwitchPreference(title = "Alert message LED",
                checked = externalNotificationInput.alertMessage,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { alertMessage = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Alert message buzzer",
                checked = externalNotificationInput.alertMessageBuzzer,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput =
                        externalNotificationInput.copy { alertMessageBuzzer = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Alert message vibra",
                checked = externalNotificationInput.alertMessageVibra,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput =
                        externalNotificationInput.copy { alertMessageVibra = it }
                })
        }

        item { TextDividerPreference("Notifications on alert/bell receipt", enabled = enabled) }

        item {
            SwitchPreference(title = "Alert bell LED",
                checked = externalNotificationInput.alertBell,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { alertBell = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Alert bell buzzer",
                checked = externalNotificationInput.alertBellBuzzer,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput =
                        externalNotificationInput.copy { alertBellBuzzer = it }
                })
        }
        item { Divider() }

        item {
            SwitchPreference(title = "Alert bell vibra",
                checked = externalNotificationInput.alertBellVibra,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput =
                        externalNotificationInput.copy { alertBellVibra = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Output LED (GPIO)",
                value = externalNotificationInput.output,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { output = it }
                })
        }

        if (externalNotificationInput.output != 0) item {
            SwitchPreference(title = "Output LED active high",
                checked = externalNotificationInput.active,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { active = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Output buzzer (GPIO)",
                value = externalNotificationInput.outputBuzzer,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { outputBuzzer = it }
                })
        }

        if (externalNotificationInput.outputBuzzer != 0) item {
            SwitchPreference(title = "Use PWM buzzer",
                checked = externalNotificationInput.usePwm,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { usePwm = it }
                })
        }
        item { Divider() }

        item {
            EditTextPreference(title = "Output vibra (GPIO)",
                value = externalNotificationInput.outputVibra,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { outputVibra = it }
                })
        }

        item {
            EditTextPreference(title = "Output duration (milliseconds)",
                value = externalNotificationInput.outputMs,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { outputMs = it }
                })
        }

        item {
            EditTextPreference(title = "Nag timeout (seconds)",
                value = externalNotificationInput.nagTimeout,
                enabled = enabled,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = {
                    externalNotificationInput = externalNotificationInput.copy { nagTimeout = it }
                })
        }

        item {
            PreferenceFooter(
                enabled = externalNotificationInput != externalNotificationConfig,
                onCancelClicked = {
                    focusManager.clearFocus()
                    externalNotificationInput = externalNotificationConfig
                },
                onSaveClicked = { onSaveClicked(externalNotificationInput) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExternalNotificationConfigPreview(){
    ExternalNotificationConfigItemList(
        externalNotificationConfig = ExternalNotificationConfig.getDefaultInstance(),
        enabled = true,
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
