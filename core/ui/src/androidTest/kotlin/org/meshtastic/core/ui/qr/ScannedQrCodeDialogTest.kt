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

package org.meshtastic.core.ui.qr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.Channel
import org.meshtastic.core.strings.R
import org.meshtastic.proto.AppOnlyProtos.ChannelSet
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.channelSet
import org.meshtastic.proto.channelSettings
import org.meshtastic.proto.copy

@RunWith(AndroidJUnit4::class)
class ScannedQrCodeDialogTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun getString(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun getRandomKey() = Channel.getRandomKey()

    private val channels = channelSet {
        settings.add(Channel.default.settings)
        loraConfig = Channel.default.loraConfig
    }

    private val incoming = channelSet {
        settings.addAll(
            listOf(
                Channel.default.settings,
                channelSettings {
                    name = "2"
                    psk = getRandomKey()
                },
                channelSettings {
                    name = "3"
                    psk = getRandomKey()
                },
                channelSettings {
                    name = "admin"
                    psk = getRandomKey()
                },
            ),
        )
        loraConfig =
            Channel.default.loraConfig.copy { modemPreset = ConfigProtos.Config.LoRaConfig.ModemPreset.SHORT_FAST }
    }

    private fun testScannedQrCodeDialog(onDismiss: () -> Unit = {}, onConfirm: (ChannelSet) -> Unit = {}) =
        composeTestRule.setContent {
            ScannedQrCodeDialog(channels = channels, incoming = incoming, onDismiss = onDismiss, onConfirm = onConfirm)
        }

    @Test
    fun testScannedQrCodeDialog_showsDialogTitle() {
        composeTestRule.apply {
            testScannedQrCodeDialog()

            // Verify that the dialog title is displayed
            onNodeWithText(getString(R.string.new_channel_rcvd)).assertIsDisplayed()
        }
    }

    @Test
    fun testScannedQrCodeDialog_showsAddAndReplaceButtons() {
        composeTestRule.apply {
            testScannedQrCodeDialog()

            // Verify that the "Add" and "Replace" buttons are displayed
            onNodeWithText(getString(R.string.add)).assertIsDisplayed()
            onNodeWithText(getString(R.string.replace)).assertIsDisplayed()
        }
    }

    @Test
    fun testScannedQrCodeDialog_showsCancelAndAcceptButtons() {
        composeTestRule.apply {
            testScannedQrCodeDialog()

            // Verify the "Cancel" and "Accept" buttons are displayed
            onNodeWithText(getString(R.string.cancel)).assertIsDisplayed()
            onNodeWithText(getString(R.string.accept)).assertIsDisplayed()
        }
    }

    @Test
    fun testScannedQrCodeDialog_clickCancelButton() {
        var onDismissClicked = false
        composeTestRule.apply {
            testScannedQrCodeDialog(onDismiss = { onDismissClicked = true })

            // Click the "Cancel" button
            onNodeWithText(getString(R.string.cancel)).performClick()
        }

        // Verify onDismiss is called
        Assert.assertTrue(onDismissClicked)
    }

    @Test
    fun testScannedQrCodeDialog_replaceChannels() {
        var actualChannelSet: ChannelSet? = null
        composeTestRule.apply {
            testScannedQrCodeDialog(onConfirm = { actualChannelSet = it })

            // Click the "Accept" button
            onNodeWithText(getString(R.string.accept)).performClick()
        }

        // Verify onConfirm is called with the correct ChannelSet
        Assert.assertEquals(incoming, actualChannelSet)
    }

    @Test
    fun testScannedQrCodeDialog_addChannels() {
        var actualChannelSet: ChannelSet? = null
        composeTestRule.apply {
            testScannedQrCodeDialog(onConfirm = { actualChannelSet = it })

            // Click the "Add" button then the "Accept" button
            onNodeWithText(getString(R.string.add)).performClick()
            onNodeWithText(getString(R.string.accept)).performClick()
        }

        // Verify onConfirm is called with the correct ChannelSet
        val expectedChannelSet =
            channels.copy {
                val list = LinkedHashSet(settings + incoming.settingsList)
                settings.clear()
                settings.addAll(list)
            }
        Assert.assertEquals(expectedChannelSet, actualChannelSet)
    }
}
