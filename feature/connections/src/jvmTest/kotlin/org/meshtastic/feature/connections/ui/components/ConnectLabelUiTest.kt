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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.label_print_action
import org.meshtastic.core.resources.label_print_band_connect
import org.meshtastic.core.ui.label.LabelSpec
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** design#185 offers Print label only where the app can reach the printer, and only for a radio it can describe. */
@OptIn(ExperimentalTestApi::class)
class ConnectLabelUiTest {
    private val ble = "xAA:BB:CC:DD:EE:FF"
    private val node =
        Node(
            num = 1,
            user =
            User.Builder()
                .also {
                    it.id = "!00000001"
                    it.long_name = "Test Node"
                    it.short_name = "TST"
                }
                .build(),
        )

    private val text =
        CurrentlyConnectedText(
            unknownLabel = "Unknown",
            rssiLabel = "RSSI",
            disconnectLabel = "Disconnect",
            firmwareVersion = null,
        )

    @Test
    fun `the connected card offers Print label when it is given a label to print`() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme {
                CurrentlyConnectedInfo(
                    node = node,
                    text = text,
                    onNavigateToNodeDetails = {},
                    onClickDisconnect = {},
                    onPrintLabel = { clicks++ },
                )
            }
        }

        onNodeWithText(getString(Res.string.label_print_action)).performClick()
        waitForIdle()

        assertEquals(1, clicks)
    }

    @Test
    fun `the connected card offers no Print label without a label to print`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                CurrentlyConnectedInfo(node = node, text = text, onNavigateToNodeDetails = {}, onClickDisconnect = {})
            }
        }

        onNodeWithText("Disconnect").assertExists()
        onNodeWithText(getString(Res.string.label_print_action)).assertDoesNotExist()
    }

    @Test
    fun `no connect label is made where the platform cannot print`() = runComposeUiTest {
        var label: LabelSpec.Connect? = null
        setContent {
            label =
                rememberConnectLabel(
                    node = node,
                    address = ble,
                    connectedAddress = ble,
                    bluetooth = null,
                    nodeIsThisSession = true,
                    configIsThisSession = true,
                )
        }
        waitForIdle()

        assertNull(label)
    }

    @Test
    fun `a connect label where the platform can print carries the connect band`() = runComposeUiTest {
        var label: LabelSpec.Connect? = null
        setContent {
            label =
                rememberConnectLabel(
                    node = node,
                    address = ble,
                    connectedAddress = ble,
                    bluetooth = null,
                    nodeIsThisSession = true,
                    configIsThisSession = true,
                    supported = true,
                )
        }
        waitForIdle()

        assertEquals(
            getString(Res.string.label_print_band_connect) to "meshtastic://meshtastic/connections?address=$ble",
            label?.band to label?.link,
        )
    }
}
