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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.tls_enabled_public_broker_summary
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the switch shows must be what gets stored: the radio uses the stored `tls_enabled` verbatim when it reaches the
 * broker over its own network, so a display that disagrees with the stored flag is a security defect, not cosmetics.
 */
@OptIn(ExperimentalTestApi::class)
class MqttTlsPreferenceTest {

    /** Renders the preference over a real MQTTConfig so the assertions are about the config that would be sent. */
    private fun storedConfig(address: String, tlsEnabled: Boolean) = ModuleConfig.MQTTConfig.Builder()
        .also { wb ->
            wb.address = address
            wb.tls_enabled = tlsEnabled
        }
        .build()

    @Test
    fun `public broker with TLS off shows the switch off and settable - stored config is the truth`() =
        runComposeUiTest {
            // The empty address is the public broker, and the shipping default.
            var config by mutableStateOf(storedConfig(address = "", tlsEnabled = false))
            setContent {
                AppTheme {
                    MqttTlsPreference(
                        enabled = true,
                        address = config.address,
                        tlsEnabled = config.tls_enabled,
                        onCheckedChange = { config = config.newBuilder().also { wb -> wb.tls_enabled = it }.build() },
                    )
                }
            }

            onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).assertIsOff().assertIsEnabled()
            assertFalse(config.tls_enabled)

            onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).performClick()

            // The user can now store TLS for a radio that connects to the public broker by itself.
            assertTrue(config.tls_enabled)
            onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).assertIsOn()
        }

    @Test
    fun `explicit public broker address with TLS off shows the switch off`() = runComposeUiTest {
        setContent {
            AppTheme {
                MqttTlsPreference(
                    enabled = true,
                    address = "mqtt.meshtastic.org",
                    tlsEnabled = false,
                    onCheckedChange = {},
                )
            }
        }

        onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).assertIsOff().assertIsEnabled()
    }

    @Test
    fun `stored TLS on shows the switch on`() = runComposeUiTest {
        val config = storedConfig(address = "mqtt.example.org", tlsEnabled = true)
        setContent {
            AppTheme {
                MqttTlsPreference(
                    enabled = true,
                    address = config.address,
                    tlsEnabled = config.tls_enabled,
                    onCheckedChange = {},
                )
            }
        }

        onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).assertIsOn()
    }

    @Test
    fun `custom broker turns TLS on and off without interference`() = runComposeUiTest {
        var config by mutableStateOf(storedConfig(address = "mqtt.example.org", tlsEnabled = true))
        setContent {
            AppTheme {
                MqttTlsPreference(
                    enabled = true,
                    address = config.address,
                    tlsEnabled = config.tls_enabled,
                    onCheckedChange = { config = config.newBuilder().also { wb -> wb.tls_enabled = it }.build() },
                )
            }
        }

        onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).performClick()

        assertFalse(config.tls_enabled)
        onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).assertIsOff()
    }

    @Test
    fun `public broker explains the phone-relay TLS upgrade`() = runComposeUiTest {
        setContent {
            AppTheme { MqttTlsPreference(enabled = true, address = "", tlsEnabled = false, onCheckedChange = {}) }
        }

        onNodeWithText(getString(Res.string.tls_enabled_public_broker_summary)).assertIsDisplayed()
    }

    @Test
    fun `custom broker shows no phone-relay note`() = runComposeUiTest {
        setContent {
            AppTheme {
                MqttTlsPreference(
                    enabled = true,
                    address = "mqtt.example.org",
                    tlsEnabled = false,
                    onCheckedChange = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.tls_enabled_public_broker_summary)).assertDoesNotExist()
    }

    @Test
    fun `a disconnected radio leaves the switch untouchable but still truthful`() = runComposeUiTest {
        var config by mutableStateOf(storedConfig(address = "", tlsEnabled = false))
        setContent {
            AppTheme {
                MqttTlsPreference(
                    enabled = false,
                    address = config.address,
                    tlsEnabled = config.tls_enabled,
                    onCheckedChange = { config = config.newBuilder().also { wb -> wb.tls_enabled = it }.build() },
                )
            }
        }

        onNodeWithTag(MQTT_TLS_SWITCH_TEST_TAG).assertIsOff()
        assertEquals(false, config.tls_enabled)
    }
}
