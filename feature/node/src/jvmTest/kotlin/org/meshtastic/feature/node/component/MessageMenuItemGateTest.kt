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
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.message
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.proto.User
import kotlin.test.Test

/**
 * The Message item follows what our radio will do: a licensed radio sends a direct message on the channel without a
 * key, an unlicensed one refuses without a key.
 */
@OptIn(ExperimentalTestApi::class)
class MessageMenuItemGateTest {

    private fun nodeWith(num: Int, publicKey: ByteString = ByteString.EMPTY, isLicensed: Boolean = false) = Node(
        num = num,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!%08x".format(num)
                wb.public_key = publicKey
                wb.is_licensed = isLicensed
            }
            .build(),
    )

    private fun ComposeUiTest.showMenu(node: Node, ourNode: Node) = setContent {
        AppTheme {
            NodeContextMenu(
                expanded = true,
                node = node,
                ourNode = ourNode,
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

    @Test
    fun `message is enabled for a keyless node when our node is licensed`() = runComposeUiTest {
        showMenu(node = nodeWith(2), ourNode = nodeWith(1, isLicensed = true))

        onNodeWithText(getString(Res.string.message)).assertIsEnabled()
    }

    @Test
    fun `message is disabled for a keyless node when our node is not licensed`() = runComposeUiTest {
        showMenu(node = nodeWith(2), ourNode = nodeWith(1))

        onNodeWithText(getString(Res.string.message)).assertIsNotEnabled()
    }

    @Test
    fun `message is enabled for a keyed node when our node is not licensed`() = runComposeUiTest {
        val key = ByteArray(32) { 0x2B.toByte() }.toByteString()
        showMenu(node = nodeWith(2, publicKey = key), ourNode = nodeWith(1))

        onNodeWithText(getString(Res.string.message)).assertIsEnabled()
    }
}
