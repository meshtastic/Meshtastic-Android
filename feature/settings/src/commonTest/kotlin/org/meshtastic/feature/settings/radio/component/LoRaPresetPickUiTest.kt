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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.resources.schema_lora_modempreset_long_fast
import org.meshtastic.core.resources.schema_lora_modempreset_long_turbo
import org.meshtastic.core.ui.component.DropDownPreference
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LoRaPresetPickUiTest {

    @Test
    fun pickingLongFastOverAnotherStoredPreset_offersSaveAndSavesLongFast() = runComposeUiTest {
        // LONG_FAST is enum 0, so this pins that picking it is a change, not an unset field.
        val stored =
            Config.LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = true
                    wb.region = RegionCode.UNSET
                    wb.modem_preset = ModemPreset.LONG_TURBO
                    wb.hop_limit = 3
                    wb.tx_enabled = true
                    wb.tx_power = 10
                }
                .build()
        lateinit var configState: ConfigState<Config.LoRaConfig>
        var savedConfig: Config.LoRaConfig? = null
        val capabilities = Capabilities(firmwareVersion = null)

        setContent {
            AppTheme {
                configState = rememberConfigState(stored)
                RadioConfigScreenList(
                    title = "LoRa",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = true,
                    onSave = { savedConfig = it },
                ) {
                    item {
                        val selected = configState.value.modem_preset
                        DropDownPreference(
                            title = "Preset",
                            enabled = true,
                            items = buildPresetItems(null, false, selected, capabilities, presetLabels()),
                            selectedItem = selected,
                            onItemSelected = {
                                configState.value =
                                    configState.value.newBuilder().also { wb -> wb.modem_preset = it }.build()
                            },
                        )
                    }
                }
            }
        }

        onNodeWithText(getString(Res.string.schema_lora_modempreset_long_turbo)).performClick()
        onNodeWithText(getString(Res.string.schema_lora_modempreset_long_fast)).performClick()

        runOnIdle { assertEquals(true, configState.isDirty) }
        onNodeWithText(getString(Res.string.save_changes)).assertIsEnabled().performClick()

        runOnIdle {
            assertEquals(ModemPreset.LONG_FAST, savedConfig?.modem_preset)
            assertEquals(RegionCode.UNSET, savedConfig?.region)
        }
    }
}
