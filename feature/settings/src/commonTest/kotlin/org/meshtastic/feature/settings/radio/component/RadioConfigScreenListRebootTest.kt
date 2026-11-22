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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.save_and_restart
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.RebootBehavior
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.proto.Config
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RadioConfigScreenListRebootTest {

    private fun assertSaveLabel(pioEnv: String?, restartCopy: Boolean) = runComposeUiTest {
        lateinit var configState: ConfigState<Config.NetworkConfig>
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalConfigTargetPioEnv provides pioEnv) {
                    configState = rememberConfigState(Config.NetworkConfig.Builder().build())
                    RadioConfigScreenList(
                        title = "Network",
                        onBack = {},
                        responseState = ResponseState.Empty,
                        onDismissPacketResponse = {},
                        configState = configState,
                        enabled = true,
                        rebootBehavior = RebootBehavior.ALWAYS,
                        onSave = {},
                    ) {}
                }
            }
        }
        runOnIdle { configState.value = configState.value.newBuilder().also { wb -> wb.wifi_enabled = true }.build() }

        val shown = if (restartCopy) Res.string.save_and_restart else Res.string.save_changes
        val absent = if (restartCopy) Res.string.save_changes else Res.string.save_and_restart
        onNodeWithText(getString(shown)).assertIsDisplayed()
        onNodeWithText(getString(absent)).assertDoesNotExist()
    }

    @Test fun `a node-kmp target saves without the restart copy`() = assertSaveLabel("node-kmp", restartCopy = false)

    @Test fun `a firmware target keeps the restart copy`() = assertSaveLabel("heltec-v3", restartCopy = true)
}
