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

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.feature.settings.util.toDisplayString
import org.meshtastic.proto.copy
import org.meshtastic.proto.moduleConfig
import org.meshtastic.core.strings.R as Res

@Composable
fun RangeTestConfigScreen(viewModel: RadioConfigViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val rangeTestConfig = state.moduleConfig.rangeTest
    val formState = rememberConfigState(initialValue = rangeTestConfig)

    RadioConfigScreenList(
        title = stringResource(Res.string.range_test),
        onBack = onBack,
        configState = formState,
        enabled = state.connected,
        responseState = state.responseState,
        onDismissPacketResponse = viewModel::clearPacketResponse,
        onSave = {
            val config = moduleConfig { rangeTest = it }
            viewModel.setModuleConfig(config)
        },
    ) {
        item {
            TitledCard(title = stringResource(Res.string.range_test_config)) {
                SwitchPreference(
                    title = stringResource(Res.string.range_test_enabled),
                    checked = formState.value.enabled,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { this.enabled = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
                HorizontalDivider()
                val rangeItems = remember { IntervalConfiguration.RANGE_TEST_SENDER.allowedIntervals }
                DropDownPreference(
                    title = stringResource(Res.string.sender_message_interval_seconds),
                    selectedItem = formState.value.sender.toLong(),
                    enabled = state.connected,
                    items = rangeItems.map { it.value to it.toDisplayString() },
                    onItemSelected = { formState.value = formState.value.copy { sender = it.toInt() } },
                )
                HorizontalDivider()
                SwitchPreference(
                    title = stringResource(Res.string.save_csv_in_storage_esp32_only),
                    checked = formState.value.save,
                    enabled = state.connected,
                    onCheckedChange = { formState.value = formState.value.copy { save = it } },
                    containerColor = CardDefaults.cardColors().containerColor,
                )
            }
        }
    }
}
