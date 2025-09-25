/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.settings.radio.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.geeksville.mesh.copy
import com.geeksville.mesh.moduleConfig
import com.geeksville.mesh.ui.settings.radio.RadioConfigViewModel
import org.meshtastic.core.strings.R
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.PreferenceCategory
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TextDividerPreference

@Composable
fun ExternalNotificationConfigScreen(navController: NavController, viewModel: RadioConfigViewModel = hiltViewModel()) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val extNotificationConfig = state.moduleConfig.externalNotification
    val ringtone = state.ringtone
    val formState = rememberConfigState(initialValue = extNotificationConfig)
    var ringtoneInput by rememberSaveable(ringtone) { mutableStateOf(ringtone) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(id = R.string.external_notification),
        onBack = { navController.popBackStack() },
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            if (ringtoneInput != ringtone) {
                viewModel.setRingtone(ringtoneInput)
            }
            if (formState.value != extNotificationConfig) {
                val config = moduleConfig { externalNotification = formState.value }
                viewModel.setModuleConfig(config)
            }
        },
    ) {
        item { PreferenceCategory(text = stringResource(R.string.external_notification_config)) }

        item {
            SwitchPreference(
                title = stringResource(R.string.external_notification_enabled),
                checked = formState.value.enabled,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
            )
        }

        item {
            TextDividerPreference(stringResource(R.string.notifications_on_message_receipt), enabled = state.connected)
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.alert_message_led),
                checked = formState.value.alertMessage,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { alertMessage = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.alert_message_buzzer),
                checked = formState.value.alertMessageBuzzer,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { alertMessageBuzzer = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.alert_message_vibra),
                checked = formState.value.alertMessageVibra,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { alertMessageVibra = it } },
            )
        }

        item {
            TextDividerPreference(
                stringResource(R.string.notifications_on_alert_bell_receipt),
                enabled = state.connected,
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.alert_bell_led),
                checked = formState.value.alertBell,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { alertBell = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.alert_bell_buzzer),
                checked = formState.value.alertBellBuzzer,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { alertBellBuzzer = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            SwitchPreference(
                title = stringResource(R.string.alert_bell_vibra),
                checked = formState.value.alertBellVibra,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { alertBellVibra = it } },
            )
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.output_led_gpio),
                value = formState.value.output,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { output = it } },
            )
        }

        if (formState.value.output != 0) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.output_led_active_high),
                    checked = formState.value.active,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { active = it } },
                )
            }
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.output_buzzer_gpio),
                value = formState.value.outputBuzzer,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { outputBuzzer = it } },
            )
        }

        if (formState.value.outputBuzzer != 0) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.use_pwm_buzzer),
                    checked = formState.value.usePwm,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { usePwm = it } },
                )
            }
        }
        item { HorizontalDivider() }

        item {
            EditTextPreference(
                title = stringResource(R.string.output_vibra_gpio),
                value = formState.value.outputVibra,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { outputVibra = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.output_duration_milliseconds),
                value = formState.value.outputMs,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { outputMs = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.nag_timeout_seconds),
                value = formState.value.nagTimeout,
                enabled = state.connected,
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { formState.value = formState.value.copy { nagTimeout = it } },
            )
        }

        item {
            EditTextPreference(
                title = stringResource(R.string.ringtone),
                value = ringtoneInput,
                maxSize = 230, // ringtone max_size:231
                enabled = state.connected,
                isError = false,
                keyboardOptions =
                KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                onValueChanged = { ringtoneInput = it },
            )
        }

        item {
            SwitchPreference(
                title = stringResource(R.string.use_i2s_as_buzzer),
                checked = formState.value.useI2SAsBuzzer,
                enabled = state.connected,
                onCheckedChange = { formState.value = formState.value.copy { useI2SAsBuzzer = it } },
            )
        }
        item { HorizontalDivider() }
    }
}
