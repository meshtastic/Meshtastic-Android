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
package org.meshtastic.core.ui.qr

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ScannedQrCodeDialogUiTest {
    private val radioButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun channel(name: String, key: Byte) = ChannelSettings.Builder()
        .also { wb ->
            wb.name = name
            wb.psk = byteArrayOf(key).toByteString()
        }
        .build()

    private fun lora(block: LoRaConfig.Builder.() -> Unit) = Channel.default.loraConfig.newBuilder().also(block).build()

    // The radio's own transmit power and MQTT consent differ from the link's, so a Replace that forgot to keep them
    // would write a different config than the one the dialog describes.
    private val onRadio =
        ChannelSet.Builder()
            .also { wb ->
                wb.settings = listOf(Channel.default.settings)
                wb.lora_config = lora {
                    tx_power = 17
                    config_ok_to_mqtt = true
                }
            }
            .build()

    private val link =
        ChannelSet.Builder()
            .also { wb ->
                wb.settings = listOf(channel("Field", 5))
                wb.lora_config = lora {
                    hop_limit = 5
                    tx_power = 30
                    config_ok_to_mqtt = false
                }
            }
            .build()

    private class Calls {
        val confirmed = mutableListOf<ChannelSet>()
        var dismissed = 0
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        incoming: ChannelSet = link,
        channels: ChannelSet = onRadio,
        calls: Calls,
    ) = setContent {
        AppTheme {
            ScannedQrCodeDialog(
                channels = channels,
                incoming = incoming,
                onDismiss = { calls.dismissed++ },
                onConfirm = { calls.confirmed += it },
            )
        }
    }

    @Test
    fun the_confirm_button_names_the_selected_mode() = runComposeUiTest {
        show(calls = Calls())

        onNodeWithText("Replace channels").assertIsDisplayed()

        onNode(radioButton and hasText("Add")).performClick()

        onNodeWithText("Add channels").assertIsDisplayed()
        onNodeWithText("Replace channels").assertDoesNotExist()
    }

    @Test
    fun choosing_a_mode_imports_nothing_and_leaves_the_dialog_open() = runComposeUiTest {
        val calls = Calls()
        show(calls = calls)

        onNode(radioButton and hasText("Add")).performClick()
        onNode(radioButton and hasText("Replace")).performClick()

        assertTrue(calls.confirmed.isEmpty())
        assertEquals(0, calls.dismissed)
    }

    @Test
    fun confirming_replace_imports_once_with_the_replacement_set() = runComposeUiTest {
        val calls = Calls()
        show(calls = calls)

        onNodeWithText("Replace channels").performClick()

        val imported = calls.confirmed.single()
        assertEquals(link.settings, imported.settings)
        assertEquals(loraConfigForReplace(link.lora_config!!, onRadio.lora_config), imported.lora_config)
        assertEquals(5, imported.lora_config?.hop_limit)
        assertEquals(17, imported.lora_config?.tx_power)
        assertTrue(imported.lora_config?.config_ok_to_mqtt == true)
        assertEquals(1, calls.dismissed)
    }

    @Test
    fun confirming_add_imports_once_with_the_radio_channels_plus_the_new_one() = runComposeUiTest {
        val calls = Calls()
        show(calls = calls)

        onNode(radioButton and hasText("Add")).performClick()
        onNodeWithText("Add channels").performClick()

        val imported = calls.confirmed.single()
        assertEquals(onRadio.settings + link.settings, imported.settings)
        assertEquals(onRadio.lora_config, imported.lora_config)
        assertEquals(1, calls.dismissed)
    }

    @Test
    fun the_mode_control_is_a_radio_group_that_follows_the_selection() = runComposeUiTest {
        show(calls = Calls())

        onAllNodes(radioButton).assertCountEquals(2)
        onNode(radioButton and hasText("Replace")).assertIsSelected()
        onNode(radioButton and hasText("Add")).assertIsNotSelected()

        onNode(radioButton and hasText("Add")).performClick()

        onNode(radioButton and hasText("Add")).assertIsSelected()
        onNode(radioButton and hasText("Replace")).assertIsNotSelected()
    }

    @Test
    fun the_mode_control_meets_the_minimum_touch_target() = runComposeUiTest {
        show(calls = Calls())

        onNode(radioButton and hasText("Replace")).assertHeightIsAtLeast(44.dp)
        onNode(radioButton and hasText("Add")).assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun a_link_without_lora_settings_offers_add_only() = runComposeUiTest {
        val calls = Calls()
        show(incoming = ChannelSet.Builder().also { it.settings = listOf(channel("Field", 5)) }.build(), calls = calls)

        onNodeWithText("Add channels").assertIsDisplayed()
        onNode(radioButton and hasText("Replace")).performClick()

        onNodeWithText("Add channels").assertIsDisplayed()
        onNodeWithText("Replace channels").assertDoesNotExist()
    }

    private val longLink =
        ChannelSet.Builder()
            .also { wb ->
                wb.settings = (0..7).map { channel("Channel $it", (it + 2).toByte()) }
                wb.lora_config = lora {
                    region = LoRaConfig.RegionCode.EU_868
                    modem_preset = LoRaConfig.ModemPreset.SHORT_FAST
                    hop_limit = 5
                    channel_num = 20
                    tx_enabled = false
                    override_duty_cycle = true
                    sx126x_rx_boosted_gain = true
                    override_frequency = 915.5f
                    ignore_incoming = listOf(1, 2, 3, 4, 5, 6)
                    pa_fan_disabled = true
                    ignore_mqtt = true
                    serial_hal_only = true
                    transparent_mode = true
                    frequency_offset = 0.25f
                }
            }
            .build()

    @Test
    fun the_title_and_actions_stay_on_screen_while_a_long_list_scrolls() = runComposeUiTest {
        show(incoming = longLink, calls = Calls())

        onNodeWithText("Import channels").assertIsDisplayed()
        onNodeWithText("Cancel").assertIsDisplayed()
        onNodeWithText("Replace channels").assertIsDisplayed()
        onNode(hasScrollAction()).assertIsDisplayed()
    }

    @Test
    fun a_change_reads_as_one_statement_under_a_heading() = runComposeUiTest {
        show(calls = Calls())

        onNode(hasText("LoRa settings that change") and isHeading()).assertIsDisplayed()
        onNode(hasContentDescription("Hop Limit, from 3 to 5")).assertIsDisplayed()
    }

    @Test
    fun changes_to_sending_and_receiving_are_named_in_text() = runComposeUiTest {
        show(
            incoming =
            ChannelSet.Builder()
                .also { wb ->
                    wb.settings = listOf(channel("Field", 5))
                    wb.lora_config = lora { tx_enabled = false }
                }
                .build(),
            calls = Calls(),
        )

        onNode(hasText("Changes to sending and receiving") and isHeading()).assertIsDisplayed()
        onNode(hasContentDescription("Transmit Enabled, from Enabled to Disabled")).assertIsDisplayed()
    }

    @Test
    fun deselecting_the_primary_updates_the_location_sharing_of_a_channel_that_inherits_its_key() = runComposeUiTest {
        // "Guest" has no key of its own: it takes the first written channel's key, and precision is capped only on a
        // publicly decryptable one.
        val team = channel("Team", 0).newBuilder().also { it.psk = ByteArray(32) { 3 }.toByteString() }.build()
        val guest =
            channel("Guest", 0)
                .newBuilder()
                .also {
                    it.psk = ByteArray(0).toByteString()
                    it.module_settings = ModuleSettings.Builder().also { m -> m.position_precision = 32 }.build()
                }
                .build()
        val incoming = link.newBuilder().also { it.settings = listOf(channel("Plaza", 1), team, guest) }.build()
        show(incoming = incoming, calls = Calls())

        onNode(hasText("Shares your approximate location")).assertExists()
        onNode(hasText("Shares your precise location")).assertDoesNotExist()

        onNode(hasText("Plaza")).performClick()

        onNode(hasText("Shares your precise location")).assertExists()
        onNode(hasText("Shares your approximate location")).assertDoesNotExist()
    }
}
