package com.geeksville.mesh.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.AppOnlyProtos.ChannelSet
import com.geeksville.mesh.ConfigProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSet
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.ScannedQrCodeDialog
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannedQrCodeDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun getString(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun getRandomKey() = Channel.getRandomKey()

    private val channels = channelSet {
        settings.add(Channel.default.settings)
        loraConfig = Channel.default.loraConfig
    }

    private val incoming = channelSet {
        settings.addAll(
            listOf(
                Channel.default.settings,
                channelSettings { name = "2"; psk = getRandomKey() },
                channelSettings { name = "3"; psk = getRandomKey() },
                channelSettings { name = "admin"; psk = getRandomKey() },
            )
        )
        loraConfig = Channel.default.loraConfig
            .copy { modemPreset = ConfigProtos.Config.LoRaConfig.ModemPreset.SHORT_FAST }
    }

    private fun testScannedQrCodeDialog(
        onDismiss: () -> Unit = {},
        onConfirm: (ChannelSet) -> Unit = {},
    ) = composeTestRule.setContent {
        ScannedQrCodeDialog(
            channels = channels,
            incoming = incoming,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
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
        val expectedChannelSet = channels.copy {
            val list = LinkedHashSet(settings + incoming.settingsList)
            settings.clear()
            settings.addAll(list)
        }
        Assert.assertEquals(expectedChannelSet, actualChannelSet)
    }
}
