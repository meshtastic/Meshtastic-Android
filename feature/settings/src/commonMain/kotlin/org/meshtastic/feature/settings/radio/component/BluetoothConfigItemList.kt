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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.bluetooth_config
import org.meshtastic.core.resources.bluetooth_fixed_pin_range
import org.meshtastic.core.resources.schema_bluetooth_enabled
import org.meshtastic.core.resources.schema_bluetooth_enabled_description
import org.meshtastic.core.resources.schema_bluetooth_fixed_pin
import org.meshtastic.core.resources.schema_bluetooth_mode
import org.meshtastic.core.resources.schema_bluetooth_mode_description
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.navigation.ConfigRoute
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.search.SettingsSearchCatalog
import org.meshtastic.proto.Config

private const val PIN_LENGTH = 6
private const val MIN_PIN = 100000
private const val MAX_PIN = 999999

private fun ConfigState<Config.BluetoothConfig>.restoreFixedPin(pin: Int) {
    value = value.newBuilder().also { it.fixed_pin = pin }.build()
}

/**
 * Whether the PIN field blocks Save: an invalid PIN blocks when it was edited, or when fixed-PIN mode needs a usable
 * one. An unedited invalid stored PIN (such as 0) is left alone in the other modes, where it is unused.
 */
internal fun isFixedPinBlocked(text: String, savedText: String, fixedPinMode: Boolean): Boolean =
    !isValidFixedPin(text) && (text != savedText || fixedPinMode)

/** The stored PIN as the field shows it, padded to six digits. */
internal fun fixedPinText(pin: Int): String = pin.toString().padStart(PIN_LENGTH, '0')

/** Firmware draws PINs from 100000 to 999999, so a leading zero can never match its numeric PIN. */
internal fun isValidFixedPin(text: String): Boolean =
    text.length == PIN_LENGTH && text.all { it in '0'..'9' } && text.toInt() in MIN_PIN..MAX_PIN

@Composable
fun BluetoothConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    BluetoothConfigContent(
        bluetoothConfig = state.radioConfig.bluetooth ?: Config.BluetoothConfig.Builder().build(),
        connected = state.connected,
        responseState = state.responseState,
        connectPromptVisible = state.connectPromptVisible,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onBack = onBack,
        onSave = { viewModel.setConfig(Config.Builder().also { wb -> wb.bluetooth = it }.build()) },
    )
}

@Composable
internal fun BluetoothConfigContent(
    bluetoothConfig: Config.BluetoothConfig,
    connected: Boolean,
    responseState: ResponseState<Any>,
    connectPromptVisible: Boolean,
    onDismissPacketResponse: () -> Unit,
    onBack: () -> Unit,
    onSave: (Config.BluetoothConfig) -> Unit,
) {
    val formState = rememberConfigState(initialValue = bluetoothConfig)
    val focusManager = LocalFocusManager.current
    val savedPinText = fixedPinText(bluetoothConfig.fixed_pin)
    var pinText by rememberSaveable(bluetoothConfig) { mutableStateOf(savedPinText) }
    val pinEdited = pinText != savedPinText
    val pinBlocked =
        isFixedPinBlocked(pinText, savedPinText, formState.value.mode == Config.BluetoothConfig.PairingMode.FIXED_PIN)

    RadioConfigScreenList(
        rebootBehavior = RebootBehavior.ALWAYS,
        title = stringResource(Res.string.bluetooth),
        onBack = onBack,
        configState = formState,
        enabled = connected,
        saveEnabled = connected && !pinBlocked,
        additionalDirtyCheck = { pinEdited },
        onDiscard = { pinText = savedPinText },
        responseState = responseState,
        showConnectPrompt = connectPromptVisible,
        onDismissPacketResponse = onDismissPacketResponse,
        onSave = onSave,
    ) {
        fieldItem(SettingsSearchCatalog.fieldKeys(ConfigRoute.BLUETOOTH.route)) {
            TitledCard(title = stringResource(Res.string.bluetooth_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.schema_bluetooth_enabled),
                    modifier = Modifier.settingsField(Res.string.schema_bluetooth_enabled),
                    summary = stringResource(Res.string.schema_bluetooth_enabled_description),
                    checked = formState.value.enabled,
                    enabled = connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.enabled = it }.build()
                    },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                BluetoothPairingModePreference(
                    enabled = connected,
                    mode = formState.value.mode,
                    onModeChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.mode = it }.build()
                    },
                )
                HorizontalDivider()
                FixedPinPreference(
                    pinText = pinText,
                    showError = pinBlocked,
                    enabled = connected,
                    onPinTextChange = { text ->
                        pinText = text
                        if (text == savedPinText) formState.restoreFixedPin(bluetoothConfig.fixed_pin)
                    },
                    focusManager = focusManager,
                    onPinChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.fixed_pin = it }.build()
                    },
                )
            }
        }
    }
}

@Composable
internal fun BluetoothPairingModePreference(
    enabled: Boolean,
    mode: Config.BluetoothConfig.PairingMode,
    onModeChange: (Config.BluetoothConfig.PairingMode) -> Unit,
) {
    DropDownPreference(
        title = stringResource(Res.string.schema_bluetooth_mode),
        modifier = Modifier.settingsField(Res.string.schema_bluetooth_mode),
        summary = stringResource(Res.string.schema_bluetooth_mode_description),
        enabled = enabled,
        items = schemaLabelledItems(Config.BluetoothConfig.PairingMode.entries.filter { it.name != "UNRECOGNIZED" }),
        selectedItem = mode.takeUnless { it.name == "UNRECOGNIZED" } ?: Config.BluetoothConfig.PairingMode.RANDOM_PIN,
        onItemSelected = onModeChange,
    )
}

@Composable
private fun FixedPinPreference(
    pinText: String,
    showError: Boolean,
    enabled: Boolean,
    onPinTextChange: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onPinChange: (Int) -> Unit,
) {
    EditTextPreference(
        title = stringResource(Res.string.schema_bluetooth_fixed_pin),
        modifier = Modifier.settingsField(Res.string.schema_bluetooth_fixed_pin),
        value = pinText,
        summary = stringResource(Res.string.bluetooth_fixed_pin_range),
        enabled = enabled,
        isError = showError,
        keyboardOptions =
        KeyboardOptions.Default.copy(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        onValueChanged = { input ->
            if (input.length <= PIN_LENGTH && input.all { it in '0'..'9' }) {
                onPinTextChange(input)
                if (isValidFixedPin(input)) onPinChange(input.toInt())
            }
        },
    )
}
