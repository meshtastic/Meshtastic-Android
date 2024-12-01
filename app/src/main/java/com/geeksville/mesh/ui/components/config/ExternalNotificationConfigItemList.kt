/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.ui.components.config

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ModuleConfigProtos.ModuleConfig.ExternalNotificationConfig
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.RadioConfigViewModel
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.SwitchPreference
import com.geeksville.mesh.ui.components.TextDividerPreference

@Composable
fun ExternalNotificationConfigScreen(
    viewModel: RadioConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()

    if (state.responseState.isWaiting()) {
        PacketResponseStateDialog(
            state = state.responseState,
            onDismiss = viewModel::clearPacketResponse,
        )
    }

    ExternalNotificationConfigItemList(
        ringtone = state.ringtone,
        extNotificationConfig = state.moduleConfig.externalNotification,
        enabled = state.connected,
        onSaveClicked = { ringtoneInput, extNotificationInput ->
            if (ringtoneInput != state.ringtone) {
                viewModel.setRingtone(ringtoneInput)
            }
            if (extNotificationInput != state.moduleConfig.externalNotification) {
                val config = moduleConfig { externalNotification = extNotificationInput }
                viewModel.setModuleConfig(config)
            }
        }
    )
}

@Composable
fun ExternalNotificationConfigItemList(
    ringtone: String,
    extNotificationConfig: ExternalNotificationConfig,
    enabled: Boolean,
    onSaveClicked: (ringtone: String, config: ExternalNotificationConfig) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var ringtoneInput by rememberSaveable { mutableStateOf(ringtone) }
    var externalNotificationInput by rememberSaveable { mutableStateOf(extNotificationConfig) }

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
            EditTextPreference(title = "Ringtone",
                value = ringtoneInput,
                maxSize = 230, // ringtone max_size:231
                enabled = enabled,
                isError = false,
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { ringtoneInput = it }
            )
        }

        item {
            SwitchPreference(title = "Use I2S as buzzer",
                checked = externalNotificationInput.useI2SAsBuzzer,
                enabled = enabled,
                onCheckedChange = {
                    externalNotificationInput = externalNotificationInput.copy { useI2SAsBuzzer = it }
                })
        }
        item { Divider() }

        item {
            PreferenceFooter(
                enabled = enabled && externalNotificationInput != extNotificationConfig || ringtoneInput != ringtone,
                onCancelClicked = {
                    focusManager.clearFocus()
                    ringtoneInput = ringtone
                    externalNotificationInput = extNotificationConfig
                },
                onSaveClicked = {
                    focusManager.clearFocus()
                    onSaveClicked(ringtoneInput, externalNotificationInput)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExternalNotificationConfigPreview() {
    ExternalNotificationConfigItemList(
        ringtone = "",
        extNotificationConfig = ExternalNotificationConfig.getDefaultInstance(),
        enabled = true,
        onSaveClicked = { _, _ -> },
    )
}
