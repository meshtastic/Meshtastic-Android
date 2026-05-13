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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.save
import org.meshtastic.proto.DeviceProfile
import org.meshtastic.proto.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EditDeviceProfileDialogTest {

    private val title = "Export configuration"
    private val deviceProfile =
        DeviceProfile(
            long_name = "Long name",
            short_name = "Short name",
            channel_url = "https://meshtastic.org/e/#CgMSAQESBggBQANIAQ",
            fixed_position = Position(latitude_i = 327766650, longitude_i = -967969890, altitude = 138),
        )

    @Test
    fun testEditDeviceProfileDialog_showsDialogTitle() = runComposeUiTest {
        setContent {
            EditDeviceProfileDialog(title = title, deviceProfile = deviceProfile, onConfirm = {}, onDismiss = {})
        }

        // Verify that the dialog title is displayed
        onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun testEditDeviceProfileDialog_showsCancelAndSaveButtons() = runComposeUiTest {
        setContent {
            EditDeviceProfileDialog(title = title, deviceProfile = deviceProfile, onConfirm = {}, onDismiss = {})
        }

        // Verify the "Cancel" and "Save" buttons are displayed
        onNodeWithText(getString(Res.string.cancel)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.save)).assertIsDisplayed()
    }

    @Test
    fun testEditDeviceProfileDialog_clickCancelButton() = runComposeUiTest {
        var onDismissClicked = false
        setContent {
            EditDeviceProfileDialog(
                title = title,
                deviceProfile = deviceProfile,
                onConfirm = {},
                onDismiss = { onDismissClicked = true },
            )
        }

        // Click the "Cancel" button
        onNodeWithText(getString(Res.string.cancel)).performClick()

        // Verify onDismiss is called
        assertTrue(onDismissClicked)
    }

    @Test
    fun testEditDeviceProfileDialog_addChannels() = runComposeUiTest {
        var actualDeviceProfile: DeviceProfile? = null
        setContent {
            EditDeviceProfileDialog(
                title = title,
                deviceProfile = deviceProfile,
                onConfirm = { actualDeviceProfile = it },
                onDismiss = {},
            )
        }

        onNodeWithText(getString(Res.string.save)).performClick()

        // Verify onConfirm is called with the correct DeviceProfile
        assertEquals(deviceProfile, actualDeviceProfile)
    }
}
