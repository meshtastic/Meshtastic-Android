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

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.schema_mqtt_downlink_targeted_only
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MqttDownlinkTargetedOnlyPreferenceTest {

    private var config = ModuleConfig.MQTTConfig.Builder().build()

    @Test
    fun shownAndTogglesOnFirmwareThatSupportsIt() = runComposeUiTest {
        setContent {
            Column {
                MqttDownlinkTargetedOnlyPreference(
                    capabilities = Capabilities("2.8.3", forceEnableAll = false),
                    enabled = true,
                    config = config,
                    onConfigChange = { config = it },
                )
            }
        }
        onNodeWithText(getString(Res.string.schema_mqtt_downlink_targeted_only)).performClick()
        assertTrue(config.downlink_targeted_only)
    }

    @Test
    fun hiddenOnOlderFirmware() = runComposeUiTest {
        setContent {
            Column {
                MqttDownlinkTargetedOnlyPreference(
                    capabilities = Capabilities("2.8.2", forceEnableAll = false),
                    enabled = true,
                    config = ModuleConfig.MQTTConfig.Builder().build(),
                    onConfigChange = {},
                )
            }
        }
        onNodeWithText(getString(Res.string.schema_mqtt_downlink_targeted_only)).assertDoesNotExist()
    }
}
