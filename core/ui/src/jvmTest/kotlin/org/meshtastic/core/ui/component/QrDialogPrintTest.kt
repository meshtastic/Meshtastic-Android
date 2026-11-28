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
package org.meshtastic.core.ui.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.label_print_action
import org.meshtastic.core.resources.share_action
import org.meshtastic.core.ui.label.ChannelLabelContent
import org.meshtastic.core.ui.label.LabelSpec
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test

/**
 * design#185 offers Print only where the app can reach the printer. The desktop app has no printer support, so a share
 * dialog given a label still shows no Print there; where the platform can print, the dialog is told so.
 */
@OptIn(ExperimentalTestApi::class)
class QrDialogPrintTest {
    private val label =
        LabelSpec.Channels(
            content = ChannelLabelContent("Hiking", "US · LongFast", "Adds 1 channel"),
            url = "https://meshtastic.org/e/#x",
            band = "JOIN MY MESH",
        )

    @Test
    fun `a share dialog given a label offers no Print where the platform cannot print`() = runComposeUiTest {
        setContent {
            AppTheme { QrDialog(title = "Hiking", uriString = label.url, onDismiss = {}, label = label) }
        }

        onNodeWithText(getString(Res.string.share_action)).assertExists()
        onNodeWithText(getString(Res.string.label_print_action)).assertDoesNotExist()
    }

    @Test
    fun `a share dialog given a label offers Print where the platform can print`() = runComposeUiTest {
        setContent {
            AppTheme {
                QrDialog(title = "Hiking", uriString = label.url, onDismiss = {}, label = label, printSupported = true)
            }
        }

        onNodeWithText(getString(Res.string.label_print_action)).assertExists()
    }

    @Test
    fun `a share dialog without a label offers no Print even where the platform can print`() = runComposeUiTest {
        setContent {
            AppTheme { QrDialog(title = "Hiking", uriString = label.url, onDismiss = {}, printSupported = true) }
        }

        onNodeWithText(getString(Res.string.share_action)).assertExists()
        onNodeWithText(getString(Res.string.label_print_action)).assertDoesNotExist()
    }
}
