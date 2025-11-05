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

package org.meshtastic.feature.settings.radio.component

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
import org.meshtastic.proto.ClientOnlyProtos.DeviceProfile
import org.meshtastic.proto.deviceProfile
import org.meshtastic.proto.position
import org.meshtastic.core.strings.R as Res

@RunWith(AndroidJUnit4::class)
class EditDeviceProfileDialogTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun getString(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val title = "Export configuration"
    private val deviceProfile = deviceProfile {
        longName = "Long name"
        shortName = "Short name"
        channelUrl = "https://meshtastic.org/e/#CgMSAQESBggBQANIAQ"
        fixedPosition = position {
            latitudeI = 327766650
            longitudeI = -967969890
            altitude = 138
        }
    }

    private fun testEditDeviceProfileDialog(onDismiss: () -> Unit = {}, onConfirm: (DeviceProfile) -> Unit = {}) =
        composeTestRule.setContent {
            EditDeviceProfileDialog(
                title = title,
                deviceProfile = deviceProfile,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
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
            onNodeWithText(getString(Res.string.cancel)).assertIsDisplayed()
            onNodeWithText(getString(Res.string.save)).assertIsDisplayed()
        }
    }

    @Test
    fun testEditDeviceProfileDialog_clickCancelButton() {
        var onDismissClicked = false
        composeTestRule.apply {
            testEditDeviceProfileDialog(onDismiss = { onDismissClicked = true })

            // Click the "Cancel" button
            onNodeWithText(getString(Res.string.cancel)).performClick()
        }

        // Verify onDismiss is called
        Assert.assertTrue(onDismissClicked)
    }

    @Test
    fun testEditDeviceProfileDialog_addChannels() {
        var actualDeviceProfile: DeviceProfile? = null
        composeTestRule.apply {
            testEditDeviceProfileDialog(onConfirm = { actualDeviceProfile = it })

            onNodeWithText(getString(Res.string.save)).performClick()
        }

        // Verify onConfirm is called with the correct DeviceProfile
        Assert.assertEquals(deviceProfile, actualDeviceProfile)
    }
}
