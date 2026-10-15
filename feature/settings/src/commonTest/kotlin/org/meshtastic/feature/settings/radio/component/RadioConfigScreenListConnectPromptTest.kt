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

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.connect_radio_to_change_settings
import org.meshtastic.core.resources.discard_changes
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.search.LocalSettingsFieldFocus
import org.meshtastic.feature.settings.search.SettingsFieldFocus
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RadioConfigScreenListConnectPromptTest {

    @Test
    fun `prompt shows for the local radio only while disconnected`() {
        assertTrue(RadioConfigState(isLocal = true, connected = false).connectPromptVisible)
        assertFalse(RadioConfigState(isLocal = true, connected = true).connectPromptVisible)
        assertFalse(RadioConfigState(isLocal = false, connected = false).connectPromptVisible)
        assertFalse(RadioConfigState(isLocal = false, connected = true).connectPromptVisible)
    }

    @Test
    fun `disconnected screen shows the prompt and disables Save and Discard`() = runComposeUiTest {
        lateinit var configState: ConfigState<Config.LoRaConfig>

        setContent {
            AppTheme {
                configState = rememberConfigState(Config.LoRaConfig.Builder().build())
                RadioConfigScreenList(
                    title = "LoRa",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = false,
                    showConnectPrompt = true,
                    onSave = {},
                ) {}
            }
        }
        runOnIdle { configState.value = configState.value.newBuilder().also { wb -> wb.hop_limit = 2 }.build() }

        onNodeWithText(getString(Res.string.connect_radio_to_change_settings)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.save_changes)).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.discard_changes)).assertIsNotEnabled()
    }

    @Test
    fun `connected screen hides the prompt`() = runComposeUiTest {
        setContent {
            AppTheme {
                RadioConfigScreenList(
                    title = "LoRa",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = rememberConfigState(Config.LoRaConfig.Builder().build()),
                    enabled = true,
                    onSave = {},
                ) {}
            }
        }

        onNodeWithText(getString(Res.string.connect_radio_to_change_settings)).assertDoesNotExist()
    }

    @Test
    fun `search focus lands on its item below the prompt row`() = runComposeUiTest {
        val focus = SettingsFieldFocus()
        focus.request("field_30")

        setContent {
            AppTheme {
                CompositionLocalProvider(LocalSettingsFieldFocus provides focus) {
                    RadioConfigScreenList(
                        title = "LoRa",
                        onBack = {},
                        responseState = ResponseState.Empty,
                        onDismissPacketResponse = {},
                        configState = rememberConfigState(Config.LoRaConfig.Builder().build()),
                        enabled = false,
                        showConnectPrompt = true,
                        onSave = {},
                    ) {
                        items(count = 40, key = { "field_$it" }) { Text("Field $it", Modifier.height(48.dp)) }
                    }
                }
            }
        }
        waitForIdle()

        onNodeWithText("Field 30").assertIsDisplayed()
        runOnIdle { assertEquals(null, focus.pending.value) }
    }
}
