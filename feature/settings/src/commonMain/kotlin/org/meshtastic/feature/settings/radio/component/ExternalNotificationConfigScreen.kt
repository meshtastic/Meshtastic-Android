/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.advanced
import org.meshtastic.core.resources.alert_bell_buzzer
import org.meshtastic.core.resources.alert_bell_led
import org.meshtastic.core.resources.alert_bell_vibra
import org.meshtastic.core.resources.alert_message_buzzer
import org.meshtastic.core.resources.alert_message_led
import org.meshtastic.core.resources.alert_message_vibra
import org.meshtastic.core.resources.external_notification
import org.meshtastic.core.resources.external_notification_config
import org.meshtastic.core.resources.external_notification_enabled
import org.meshtastic.core.resources.nag_timeout_seconds
import org.meshtastic.core.resources.notifications_on_alert_bell_receipt
import org.meshtastic.core.resources.notifications_on_message_receipt
import org.meshtastic.core.resources.output_buzzer_gpio
import org.meshtastic.core.resources.output_duration_milliseconds
import org.meshtastic.core.resources.output_led_active_high
import org.meshtastic.core.resources.output_led_gpio
import org.meshtastic.core.resources.output_vibra_gpio
import org.meshtastic.core.resources.ringtone
import org.meshtastic.core.resources.use_i2s_as_buzzer
import org.meshtastic.core.resources.use_pwm_buzzer
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.ModuleConfig

private const val MAX_RINGTONE_SIZE = 230

@Composable
expect fun RingtoneTrailingIcon(ringtoneInput: String, onRingtoneImported: (String) -> Unit, enabled: Boolean)

@Suppress("LongMethod", "TooGenericExceptionCaught")
@Composable
fun ExternalNotificationConfigScreenCommon(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RadioConfigViewModel,
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val extNotificationConfig = state.moduleConfig.external_notification ?: ModuleConfig.ExternalNotificationConfig()
    val ringtone = state.ringtone
    val formState = rememberConfigState(initialValue = extNotificationConfig)
    var ringtoneInput by rememberSaveable(ringtone) { mutableStateOf(ringtone) }
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        modifier = modifier,
        title = stringResource(Res.string.external_notification),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        additionalDirtyCheck = { ringtoneInput != ringtone },
        onDiscard = { ringtoneInput = ringtone },
        onSave = {
            if (ringtoneInput != ringtone) {
                viewModel.setRingtone(ringtoneInput)
            }
            if (formState.value != extNotificationConfig) {
                val config = ModuleConfig(external_notification = formState.value)
                viewModel.setModuleConfig(config)
            }
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.external_notification_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.external_notification_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(enabled = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.notifications_on_message_receipt)) {
                SwitchPreference(
                    title = stringResource(Res.string.alert_message_led),
                    checked = formState.value.alert_message,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(alert_message = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.alert_message_buzzer),
                    checked = formState.value.alert_message_buzzer,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(alert_message_buzzer = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.alert_message_vibra),
                    checked = formState.value.alert_message_vibra,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(alert_message_vibra = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.notifications_on_alert_bell_receipt)) {
                SwitchPreference(
                    title = stringResource(Res.string.alert_bell_led),
                    checked = formState.value.alert_bell,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(alert_bell = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.alert_bell_buzzer),
                    checked = formState.value.alert_bell_buzzer,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(alert_bell_buzzer = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.alert_bell_vibra),
                    checked = formState.value.alert_bell_vibra,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(alert_bell_vibra = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }

        item {
            TitledCard(title = stringResource(Res.string.advanced)) {
                val gpio = remember { org.meshtastic.feature.settings.util.gpioPins }
                DropDownPreference(
                    title = stringResource(Res.string.output_led_gpio),
                    items = gpio,
                    selectedItem = formState.value.output.toLong(),
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(output = it.toInt()) },
                )
                if (formState.value.output != 0) {
                    HorizontalDivider()
                    SwitchPreference(
                        title = stringResource(Res.string.output_led_active_high),
                        checked = formState.value.active,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy(active = it) },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                }
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.output_buzzer_gpio),
                    items = gpio,
                    selectedItem = formState.value.output_buzzer.toLong(),
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(output_buzzer = it.toInt()) },
                )
                if (formState.value.output_buzzer != 0) {
                    HorizontalDivider()
                    SwitchPreference(
                        title = stringResource(Res.string.use_pwm_buzzer),
                        checked = formState.value.use_pwm,
                        enabled = state.connected,
                        onCheckedChange = { formState.value = formState.value.copy(use_pwm = it) },
                        containerColor = CardDefaults.cardColors().containerColor,
                    )
                }
                HorizontalDivider()
                DropDownPreference(
                    title = stringResource(Res.string.output_vibra_gpio),
                    items = gpio,
                    selectedItem = formState.value.output_vibra.toLong(),
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(output_vibra = it.toInt()) },
                )
                HorizontalDivider()
                val outputItems = remember { IntervalConfiguration.OUTPUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.output_duration_milliseconds),
                    items = outputItems.map { it.value to it.toDisplayString() },
                    selectedItem = formState.value.output_ms.toLong(),
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(output_ms = it.toInt()) },
                )
                HorizontalDivider()
                val nagItems = remember { IntervalConfiguration.NAG_TIMEOUT.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.nag_timeout_seconds),
                    items = nagItems.map { it.value to it.toDisplayString() },
                    selectedItem = formState.value.nag_timeout.toLong(),
                    enabled = state.connected,
                    onItemSelected = { formState.value = formState.value.copy(nag_timeout = it.toInt()) },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.ringtone),
                    value = ringtoneInput,
                    maxSize = MAX_RINGTONE_SIZE,
                    enabled = state.connected,
                    isError = false,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { ringtoneInput = it },
                    trailingIcon = {
                        RingtoneTrailingIcon(
                            ringtoneInput = ringtoneInput,
                            onRingtoneImported = { ringtoneInput = it },
                            enabled = state.connected,
                        )
                    },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.use_i2s_as_buzzer),
                    checked = formState.value.use_i2s_as_buzzer,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(use_i2s_as_buzzer = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
