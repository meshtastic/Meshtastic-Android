package com.geeksville.mesh.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.geeksville.mesh.ClientOnlyProtos.DeviceProfile
import com.geeksville.mesh.R
import com.geeksville.mesh.deviceProfile
import com.geeksville.mesh.ui.components.config.EditDeviceProfileDialog
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditDeviceProfileDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun getString(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val title = "Export configuration"
    private val deviceProfile = deviceProfile {
        longName = "Long name"
        shortName = "Short name"
        channelUrl = "https://meshtastic.org/e/#CgMSAQESBggBQANIAQ"
    }

    private fun testEditDeviceProfileDialog(
        onDismissRequest: () -> Unit = {},
        onAddClick: (DeviceProfile) -> Unit = {},
    ) = composeTestRule.setContent {
        EditDeviceProfileDialog(
            title = title,
            deviceProfile = deviceProfile,
            onAddClick = onAddClick,
            onDismissRequest = onDismissRequest,
        )
    }

    @Test
    fun testEditDeviceProfileDialog_showsDialogTitle() {
        composeTestRule.apply {
            testEditDeviceProfileDialog()

            // Verify that the dialog title is displayed
            onNodeWithText(title).assertIsDisplayed()
        }
    }

    @Test
    fun testEditDeviceProfileDialog_showsCancelAndSaveButtons() {
        composeTestRule.apply {
            testEditDeviceProfileDialog()

            // Verify the "Cancel" and "Save" buttons are displayed
            onNodeWithText(getString(R.string.cancel)).assertIsDisplayed()
            onNodeWithText(getString(R.string.save)).assertIsDisplayed()
        }
    }

    @Test
    fun testEditDeviceProfileDialog_clickCancelButton() {
        var onDismissClicked = false
        composeTestRule.apply {
            testEditDeviceProfileDialog(onDismissRequest = { onDismissClicked = true })

            // Click the "Cancel" button
            onNodeWithText(getString(R.string.cancel)).performClick()
        }

        // Verify onDismiss is called
        Assert.assertTrue(onDismissClicked)
    }

    @Test
    fun testEditDeviceProfileDialog_addChannels() {
        var actualDeviceProfile: DeviceProfile? = null
        composeTestRule.apply {
            testEditDeviceProfileDialog(onAddClick = { actualDeviceProfile = it })

            onNodeWithText(getString(R.string.save)).performClick()
        }

        // Verify onConfirm is called with the correct DeviceProfile
        Assert.assertEquals(deviceProfile, actualDeviceProfile)
    }
}