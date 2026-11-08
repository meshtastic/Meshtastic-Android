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
package org.meshtastic.feature.node.component

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_favorite
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.message
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.resources.trace_route
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test
import kotlin.test.assertTrue

/** Message leads the node list menu on every client (design#171), whether or not Mute is offered. */
@OptIn(ExperimentalTestApi::class)
class NodeContextMenuOrderTest {

    private fun nodeOn(num: Int, firmware: String?) = Node(
        num = num,
        metadata = firmware?.let { DeviceMetadata.Builder().also { wb -> wb.firmware_version = it }.build() },
    )

    private fun ComposeUiTest.showMenu(ourFirmware: String) = setContent {
        AppTheme {
            NodeContextMenu(
                expanded = true,
                node = nodeOn(num = 2, firmware = null),
                ourNode = nodeOn(num = 1, firmware = ourFirmware),
                onFavorite = {},
                onMute = {},
                onMessage = {},
                onTraceRoute = {},
                lastTracerouteTime = null,
                onIgnore = {},
                onRemove = {},
                onDismiss = {},
            )
        }
    }

    private fun ComposeUiTest.top(label: StringResource): Float =
        onNodeWithText(getString(label)).fetchSemanticsNode().positionInRoot.y

    @Test
    fun `message comes before favorite mute and trace route`() = runComposeUiTest {
        showMenu(ourFirmware = "2.7.18")

        val message = top(Res.string.message)
        assertTrue(message < top(Res.string.add_favorite))
        assertTrue(message < top(Res.string.mute_notifications))
        assertTrue(message < top(Res.string.trace_route))
    }

    @Test
    fun `message comes first when mute is not offered`() = runComposeUiTest {
        showMenu(ourFirmware = "2.7.15")

        onNodeWithText(getString(Res.string.mute_notifications)).assertDoesNotExist()
        val message = top(Res.string.message)
        assertTrue(message < top(Res.string.add_favorite))
        assertTrue(message < top(Res.string.trace_route))
    }
}
