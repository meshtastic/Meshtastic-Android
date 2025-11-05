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

package org.meshtastic.feature.settings.radio.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.database.model.isUnmessageableRole
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.RegularPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.copy
import org.meshtastic.core.strings.R as Res

@Composable
fun UserConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val userConfig = state.userConfig
    val formState = rememberConfigState(initialValue = userConfig)
    val firmwareVersion = DeviceVersion(state.metadata?.firmwareVersion ?: "")

    val validLongName = formState.value.longName.isNotBlank()
    val validShortName = formState.value.shortName.isNotBlank()
    val validNames = validLongName && validShortName
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.user),
        onBack = onBack,
        configState = formState,
        enabled = state.connected && validNames,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = viewModel::setOwner,
    ) {
        item {
            TitledCard(title = stringResource(Res.string.user_config)) {
                RegularPreference(
                    title = stringResource(Res.string.node_id),
                    subtitle = formState.value.id,
                    onClick = {},
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.long_name),
                    value = formState.value.longName,
                    maxSize = 39, // long_name max_size:40
                    enabled = state.connected,
                    isError = !validLongName,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { longName = it } },
                )
                HorizontalDivider()
                EditTextPreference(
                    title = stringResource(Res.string.short_name),
                    value = formState.value.shortName,
                    maxSize = 4, // short_name max_size:5
                    enabled = state.connected,
                    isError = !validShortName,
                    keyboardOptions =
                    KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    onValueChanged = { formState.value = formState.value.copy { shortName = it } },
                )
                HorizontalDivider()
                RegularPreference(
                    title = stringResource(Res.string.hardware_model),
                    subtitle = formState.value.hwModel.name,
                    onClick = {},
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.unmessageable),
                    summary = stringResource(Res.string.unmonitored_or_infrastructure),
                    checked =
                    formState.value.isUnmessagable ||
                        (firmwareVersion < DeviceVersion("2.6.9") && formState.value.role.isUnmessageableRole()),
                    enabled = formState.value.hasIsUnmessagable() || firmwareVersion >= DeviceVersion("2.6.9"),
                    onCheckedChange = { formState.value = formState.value.copy { isUnmessagable = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.licensed_amateur_radio),
                    summary = stringResource(Res.string.licensed_amateur_radio_text),
                    checked = formState.value.isLicensed,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { isLicensed = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
