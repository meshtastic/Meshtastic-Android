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
import org.meshtastic.core.resources.ignore
import org.meshtastic.core.resources.message
import org.meshtastic.core.resources.mute_notifications
import org.meshtastic.core.resources.remove
import org.meshtastic.core.resources.set_display_name
import org.meshtastic.core.resources.trace_route
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.DeviceMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The node list menu opens with Message then Set display name, the two things you do about that person (design#171).
 * Ignore and Remove stay last, whether or not Mute is offered.
 */
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

    private fun ComposeUiTest.assertTopToBottom(vararg labels: StringResource) {
        val tops = labels.map { onNodeWithText(getString(it)).fetchSemanticsNode().positionInRoot.y }
        assertEquals(tops.sorted(), tops, "menu order")
        assertEquals(tops.size, tops.toSet().size, "menu order")
    }

    @Test
    fun `message then display name lead and ignore then remove close the menu`() = runComposeUiTest {
        showMenu(ourFirmware = "2.7.18")

        assertTopToBottom(
            Res.string.message,
            Res.string.set_display_name,
            Res.string.add_favorite,
            Res.string.mute_notifications,
            Res.string.trace_route,
            Res.string.ignore,
            Res.string.remove,
        )
    }

    @Test
    fun `the order holds when mute is not offered`() = runComposeUiTest {
        showMenu(ourFirmware = "2.7.15")

        onNodeWithText(getString(Res.string.mute_notifications)).assertDoesNotExist()
        assertTopToBottom(
            Res.string.message,
            Res.string.set_display_name,
            Res.string.add_favorite,
            Res.string.trace_route,
            Res.string.ignore,
            Res.string.remove,
        )
    }
}
