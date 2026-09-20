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
import org.meshtastic.core.resources.schema_ambientlighting_blue
import org.meshtastic.core.resources.schema_ambientlighting_current
import org.meshtastic.core.resources.schema_ambientlighting_green
import org.meshtastic.core.resources.schema_ambientlighting_led_state
import org.meshtastic.core.resources.schema_ambientlighting_red
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.blue
import org.meshtastic.proto.current
import org.meshtastic.proto.green
import org.meshtastic.proto.red

@Composable
fun AmbientLightingConfigScreen(viewModel: RadioConfigViewModel, onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val ambientLightingConfig =
        state.moduleConfig.ambient_lighting ?: ModuleConfig.AmbientLightingConfig.Builder().build()
    val formState = rememberConfigState(initialValue = ambientLightingConfig)
    val focusManager = LocalFocusManager.current

    RadioConfigScreenList(
        rebootBehavior = RebootBehavior.ALWAYS,
        title = stringResource(Res.string.ambient_lighting),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = ModuleConfig.Builder().also { wb -> wb.ambient_lighting = it }.build()
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.ambient_lighting_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.schema_ambientlighting_led_state),
                    checked = formState.value.led_state,
                    enabled = state.connected,
                    onCheckedChange = {
                        formState.value = formState.value.newBuilder().also { wb -> wb.led_state = it }.build()
                    },
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
        BoundedIntEditTextPreference(
            title = stringResource(Res.string.schema_ambientlighting_current),
            value = config.current,
            metadata = ModuleConfig.AmbientLightingConfig.current,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChange = { onConfigChange(config.newBuilder().also { wb -> wb.current = it }.build()) },
        )
        BoundedIntEditTextPreference(
            title = stringResource(Res.string.schema_ambientlighting_red),
            value = config.red,
            metadata = ModuleConfig.AmbientLightingConfig.red,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChange = { onConfigChange(config.newBuilder().also { wb -> wb.red = it }.build()) },
        )
        BoundedIntEditTextPreference(
            title = stringResource(Res.string.schema_ambientlighting_green),
            value = config.green,
            metadata = ModuleConfig.AmbientLightingConfig.green,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChange = { onConfigChange(config.newBuilder().also { wb -> wb.green = it }.build()) },
        )
        BoundedIntEditTextPreference(
            title = stringResource(Res.string.schema_ambientlighting_blue),
            value = config.blue,
            metadata = ModuleConfig.AmbientLightingConfig.blue,
            enabled = enabled,
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onValueChange = { onConfigChange(config.newBuilder().also { wb -> wb.blue = it }.build()) },
        )
    }
}
