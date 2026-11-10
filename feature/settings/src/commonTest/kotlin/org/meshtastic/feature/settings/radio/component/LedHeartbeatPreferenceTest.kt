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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The switch is titled "Disable LED Heartbeat", so on must mean `led_heartbeat_disabled` is true. */
@OptIn(ExperimentalTestApi::class)
class LedHeartbeatPreferenceTest {

    @Test
    fun `heartbeat running shows the disable switch off and turning it on disables the heartbeat`() = runComposeUiTest {
        var config by mutableStateOf(Config.DeviceConfig.Builder().build())
        setContent {
            AppTheme {
                LedHeartbeatPreference(
                    enabled = true,
                    heartbeatDisabled = config.led_heartbeat_disabled,
                    onCheckedChange = {
                        config = config.newBuilder().also { wb -> wb.led_heartbeat_disabled = it }.build()
                    },
                )
            }
        }

        onNodeWithTag(LED_HEARTBEAT_SWITCH_TEST_TAG).assertIsOff()

        onNodeWithTag(LED_HEARTBEAT_SWITCH_TEST_TAG).performClick()

        assertTrue(config.led_heartbeat_disabled)
        onNodeWithTag(LED_HEARTBEAT_SWITCH_TEST_TAG).assertIsOn()
    }

    @Test
    fun `heartbeat disabled shows the disable switch on and turning it off restores the heartbeat`() =
        runComposeUiTest {
            var config by
                mutableStateOf(Config.DeviceConfig.Builder().also { wb -> wb.led_heartbeat_disabled = true }.build())
            setContent {
                AppTheme {
                    LedHeartbeatPreference(
                        enabled = true,
                        heartbeatDisabled = config.led_heartbeat_disabled,
                        onCheckedChange = {
                            config = config.newBuilder().also { wb -> wb.led_heartbeat_disabled = it }.build()
                        },
                    )
                }
            }

            onNodeWithTag(LED_HEARTBEAT_SWITCH_TEST_TAG).assertIsOn()

            onNodeWithTag(LED_HEARTBEAT_SWITCH_TEST_TAG).performClick()

            assertFalse(config.led_heartbeat_disabled)
        }
}
