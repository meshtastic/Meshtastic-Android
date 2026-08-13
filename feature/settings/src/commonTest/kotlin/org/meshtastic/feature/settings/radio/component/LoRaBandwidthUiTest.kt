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

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bandwidth_unsupported_summary
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.HardwareModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class LoRaBandwidthUiTest {

    @Test
    fun invalidPersistedValues_explainWhyAndCannotSendUntilReplaced() = runComposeUiTest {
        val initialConfig =
            Config.LoRaConfig(use_preset = false, region = RegionCode.LORA_24, bandwidth = 125, hop_limit = 1)
        lateinit var configState: ConfigState<Config.LoRaConfig>
        var savedConfig: Config.LoRaConfig? = null

        setContent {
            AppTheme {
                configState = rememberConfigState(initialConfig)
                val selection =
                    loRaBandwidthSelection(
                        storedValue = configState.value.bandwidth,
                        region = configState.value.region,
                        hwModel = HardwareModel.MUZI_BASE,
                        pioEnv = "muzi-base",
                    )
                RadioConfigScreenList(
                    title = "LoRa",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = true,
                    saveEnabled = selection.allowsSave(configState.value.use_preset),
                    onSave = { savedConfig = it },
                ) {
                    item {
                        LoRaBandwidthPreference(
                            config = configState.value,
                            selection = selection,
                            enabled = true,
                            focusManager = LocalFocusManager.current,
                            onConfigChange = { configState.value = it },
                        )
                    }
                }
            }
        }

        runOnIdle { configState.value = configState.value.copy(hop_limit = 2) }

        onNodeWithText("Unsupported (125 kHz)").assertIsDisplayed()
        onNodeWithText(getString(Res.string.bandwidth_unsupported_summary)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.save_changes)).assertIsNotEnabled().performClick()
        runOnIdle { assertNull(savedConfig) }

        runOnIdle { configState.value = configState.value.copy(bandwidth = 0) }
        onNodeWithText("Default (812.5 kHz)").assertIsDisplayed()
        onNodeWithText(getString(Res.string.save_changes)).assertIsEnabled().performClick()

        runOnIdle { assertEquals(0, savedConfig?.bandwidth) }
    }
}
