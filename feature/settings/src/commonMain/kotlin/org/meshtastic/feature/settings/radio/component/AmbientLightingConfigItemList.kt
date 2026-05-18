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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ambient_lighting
import org.meshtastic.core.resources.ambient_lighting_config
import org.meshtastic.core.resources.blue
import org.meshtastic.core.resources.current
import org.meshtastic.core.resources.green
import org.meshtastic.core.resources.led_state
import org.meshtastic.core.resources.red
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.ModuleConfig

private const val MAX_LED_CURRENT = 31
private const val MAX_RGB_VALUE = 255

@Composable
fun AmbientLightingConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val ambientLightingConfig = state.moduleConfig.ambient_lighting ?: ModuleConfig.AmbientLightingConfig()
    val formState = rememberConfigState(initialValue = ambientLightingConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        title = stringResource(Res.string.ambient_lighting),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig(ambient_lighting = it)
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.ambient_lighting_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.led_state),
                    checked = formState.value.led_state,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy(led_state = it) },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                LedColorFields(
                    config = formState.value,
                    enabled = state.connected,
                    focusManager = focusManager,
                    onConfigChange = { formState.value = it },
                )
            }
        }
    }
}

@Composable
private fun LedColorFields(
    config: ModuleConfig.AmbientLightingConfig,
    enabled: Boolean,
    focusManager: FocusManager,
    onConfigChange: (ModuleConfig.AmbientLightingConfig) -> Unit,
) {
    androidx.compose.foundation.layout.Column {
        EditTextPreference(
            title = stringResource(Res.string.current),
            value = config.current,
            enabled = enabled,
            isError = config.current !in 0..MAX_LED_CURRENT,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = {
                if (it in 0..MAX_LED_CURRENT) {
                    onConfigChange(config.copy(current = it))
                }
            },
        )
        EditTextPreference(
            title = stringResource(Res.string.red),
            value = config.red,
            enabled = enabled,
            isError = config.red !in 0..MAX_RGB_VALUE,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = {
                if (it in 0..MAX_RGB_VALUE) {
                    onConfigChange(config.copy(red = it))
                }
            },
        )
        EditTextPreference(
            title = stringResource(Res.string.green),
            value = config.green,
            enabled = enabled,
            isError = config.green !in 0..MAX_RGB_VALUE,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = {
                if (it in 0..MAX_RGB_VALUE) {
                    onConfigChange(config.copy(green = it))
                }
            },
        )
        EditTextPreference(
            title = stringResource(Res.string.blue),
            value = config.blue,
            enabled = enabled,
            isError = config.blue !in 0..MAX_RGB_VALUE,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChanged = {
                if (it in 0..MAX_RGB_VALUE) {
                    onConfigChange(config.copy(blue = it))
                }
            },
        )
    }
}
