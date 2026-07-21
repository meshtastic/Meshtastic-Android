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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.packet_authenticity_balanced
import org.meshtastic.core.resources.packet_authenticity_balanced_summary
import org.meshtastic.core.resources.packet_authenticity_compatible
import org.meshtastic.core.resources.packet_authenticity_strict
import org.meshtastic.core.resources.packet_authenticity_strict_confirm
import org.meshtastic.core.resources.packet_authenticity_strict_summary
import org.meshtastic.core.resources.packet_authenticity_unsupported
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class PacketAuthenticitySettingTest {
    @Test
    fun `protobuf default is compatible`() {
        assertEquals(
            PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_COMPATIBLE,
            Config.SecurityConfig().packet_signature_policy,
        )
    }

    @Test
    fun `balanced selection updates the device config field`() = runComposeUiTest {
        var updatedConfig = Config.SecurityConfig()
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = updatedConfig.packet_signature_policy,
                    connected = true,
                    supported = true,
                    onPolicyChange = { policy -> updatedConfig = updatedConfig.copy(packet_signature_policy = policy) },
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_compatible)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()

        assertEquals(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED, updatedConfig.packet_signature_policy)
    }

    @Test
    fun `strict selection requires confirmation`() = runComposeUiTest {
        var selectedPolicy: PacketSignaturePolicy? = null
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = true,
                    supported = true,
                    onPolicyChange = { selectedPolicy = it },
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict)).performClick()

        assertNull(selectedPolicy)
        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.cancel)).performClick()
        assertNull(selectedPolicy)
        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertDoesNotExist()
    }

    @Test
    fun `confirmed strict selection updates policy`() = runComposeUiTest {
        var selectedPolicy: PacketSignaturePolicy? = null
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = true,
                    supported = true,
                    onPolicyChange = { selectedPolicy = it },
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).performClick()

        assertEquals(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT, selectedPolicy)
    }

    @Test
    fun `support loss dismisses strict confirmation without changing policy`() = runComposeUiTest {
        var supported by mutableStateOf(true)
        var selectedPolicy: PacketSignaturePolicy? = null
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = true,
                    supported = supported,
                    onPolicyChange = { selectedPolicy = it },
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertIsDisplayed()

        supported = false
        waitForIdle()

        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertDoesNotExist()
        assertNull(selectedPolicy)
    }

    @Test
    fun `connection loss dismisses strict confirmation without changing policy`() = runComposeUiTest {
        var connected by mutableStateOf(true)
        var selectedPolicy: PacketSignaturePolicy? = null
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = connected,
                    supported = true,
                    onPolicyChange = { selectedPolicy = it },
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertIsDisplayed()

        connected = false
        waitForIdle()

        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertDoesNotExist()
        assertNull(selectedPolicy)
    }

    @Test
    fun `selecting current strict policy does not prompt again`() = runComposeUiTest {
        var selectedPolicy: PacketSignaturePolicy? = null
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT,
                    connected = true,
                    supported = true,
                    onPolicyChange = { selectedPolicy = it },
                )
            }
        }

        val strictLabel = getString(Res.string.packet_authenticity_strict)
        onNodeWithText(strictLabel).performClick()
        onNodeWithTag(PACKET_AUTHENTICITY_STRICT_POLICY_TEST_TAG).performClick()

        onNodeWithText(getString(Res.string.packet_authenticity_strict_confirm)).assertDoesNotExist()
        assertEquals(PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT, selectedPolicy)
    }

    @Test
    fun `unsupported firmware cannot open selector`() = runComposeUiTest {
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = true,
                    supported = false,
                    onPolicyChange = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_unsupported)).assertIsDisplayed()
        onNodeWithTag(PACKET_AUTHENTICITY_SELECTOR_TEST_TAG).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict)).assertDoesNotExist()
    }

    @Test
    fun `unknown capability keeps current policy summary while disabled`() = runComposeUiTest {
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = true,
                    supported = null,
                    onPolicyChange = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_balanced_summary)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.packet_authenticity_unsupported)).assertDoesNotExist()
        onNodeWithTag(PACKET_AUTHENTICITY_SELECTOR_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `disconnected supported device cannot open selector`() = runComposeUiTest {
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_BALANCED,
                    connected = false,
                    supported = true,
                    onPolicyChange = {},
                )
            }
        }

        onNodeWithTag(PACKET_AUTHENTICITY_SELECTOR_TEST_TAG).assertIsNotEnabled()
        onNodeWithText(getString(Res.string.packet_authenticity_balanced)).performClick()
        onNodeWithText(getString(Res.string.packet_authenticity_strict)).assertDoesNotExist()
    }

    @Test
    fun `strict summary covers all authenticated mesh packets`() = runComposeUiTest {
        setContent {
            AppTheme {
                PacketAuthenticitySetting(
                    selectedPolicy = PacketSignaturePolicy.PACKET_SIGNATURE_POLICY_STRICT,
                    connected = true,
                    supported = true,
                    onPolicyChange = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.packet_authenticity_strict_summary)).assertIsDisplayed()
    }
}
