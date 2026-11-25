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
package org.meshtastic.feature.settings.radio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.doc_no_results
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.node_database
import org.meshtastic.core.resources.remote_admin_add_and_favorite_node
import org.meshtastic.core.resources.remote_admin_add_node
import org.meshtastic.core.resources.remote_admin_node_no_public_key
import org.meshtastic.core.resources.remote_admin_remove_favorite
import org.meshtastic.core.resources.remote_admin_remove_node
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.AdministrationContent
import org.meshtastic.feature.settings.radio.component.RemoteNodePickerContent
import org.meshtastic.feature.settings.radio.component.sortRemoteNodeDbCandidates
import org.meshtastic.proto.Config
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RemoteNodeDbManagementTest {

    private val validKey: ByteString = ByteArray(Node.PUBLIC_KEY_SIZE) { 0x1A.toByte() }.toByteString()

    private fun testNode(
        num: Int,
        longName: String,
        shortName: String = "N$num",
        role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.CLIENT,
        hasKey: Boolean = true,
        isFavorite: Boolean = false,
        lastHeard: Int = 100,
    ) = Node(
        num = num,
        user =
        User.Builder()
            .also { wb ->
                wb.id = NodeAddress.numToDefaultId(num)
                wb.long_name = longName
                wb.short_name = shortName
                wb.role = role
                if (hasKey) wb.public_key = validKey
            }
            .build(),
        publicKey = if (hasKey) validKey else null,
        isFavorite = isFavorite,
        lastHeard = lastHeard,
    )

    @Test
    fun `remoteNodeDbActionsForRole offers Add node only for CLIENT_BASE`() {
        assertEquals(
            listOf(
                RemoteNodeDbAction.ADD_AND_FAVORITE,
                RemoteNodeDbAction.ADD_NODE,
                RemoteNodeDbAction.REMOVE_FAVORITE,
                RemoteNodeDbAction.REMOVE_NODE,
            ),
            remoteNodeDbActionsForRole(Config.DeviceConfig.Role.CLIENT_BASE),
        )

        val nonClientBaseExpected =
            listOf(
                RemoteNodeDbAction.ADD_AND_FAVORITE,
                RemoteNodeDbAction.REMOVE_FAVORITE,
                RemoteNodeDbAction.REMOVE_NODE,
            )
        assertEquals(nonClientBaseExpected, remoteNodeDbActionsForRole(Config.DeviceConfig.Role.CLIENT))
        assertEquals(nonClientBaseExpected, remoteNodeDbActionsForRole(Config.DeviceConfig.Role.ROUTER))
        assertEquals(nonClientBaseExpected, remoteNodeDbActionsForRole(null))
    }

    @Test
    fun `sortRemoteNodeDbCandidates excludes destNum and prioritizes own node and valid keys`() {
        val destNode = testNode(num = 99, longName = "Target Base", lastHeard = 999)
        val ownNode = testNode(num = 1, longName = "My Phone Node", isFavorite = false, lastHeard = 10)
        val favNode = testNode(num = 2, longName = "Fav Peer", isFavorite = true, lastHeard = 50)
        val recentNode = testNode(num = 3, longName = "Recent Peer", isFavorite = false, lastHeard = 500)
        val keylessNode =
            testNode(num = 4, longName = "Keyless Peer", hasKey = false, isFavorite = true, lastHeard = 800)

        val candidates = listOf(destNode, keylessNode, recentNode, favNode, ownNode)

        val addSorted =
            sortRemoteNodeDbCandidates(
                candidates = candidates,
                destNum = 99,
                myNodeNum = 1,
                query = "",
                requiresPublicKey = true,
            )
        assertEquals(listOf(1, 2, 3, 4), addSorted.map { it.num })

        val removeSorted =
            sortRemoteNodeDbCandidates(
                candidates = candidates,
                destNum = 99,
                myNodeNum = 1,
                query = "",
                requiresPublicKey = false,
            )
        assertEquals(listOf(1, 4, 2, 3), removeSorted.map { it.num })

        val searched =
            sortRemoteNodeDbCandidates(
                candidates = candidates,
                destNum = 99,
                myNodeNum = 1,
                query = "recent",
                requiresPublicKey = true,
            )
        assertEquals(listOf(3), searched.map { it.num })
    }

    @Test
    fun `AdministrationContent hides Node database when local`() = runComposeUiTest {
        val localNode = testNode(num = 1, longName = "Local Node", role = Config.DeviceConfig.Role.CLIENT_BASE)
        setContent {
            AppTheme {
                AdministrationContent(
                    state = RadioConfigState(isLocal = true, connected = true),
                    destNode = localNode,
                    myNodeNum = 1,
                    getRemoteNodeDbCandidates = { emptyList() },
                    onConfirmAdminRoute = {},
                    onPreserveFavoritesChange = {},
                    onConfirmRemoteNodeDbAction = { _, _ -> },
                    onDismissPacketResponse = {},
                    onBack = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.node_database)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.remote_admin_add_and_favorite_node)).assertDoesNotExist()
    }

    @Test
    fun `AdministrationContent shows Add node only when remote target is CLIENT_BASE`() = runComposeUiTest {
        val clientBaseTarget = testNode(num = 99, longName = "Roof Base", role = Config.DeviceConfig.Role.CLIENT_BASE)
        setContent {
            AppTheme {
                AdministrationContent(
                    state = RadioConfigState(isLocal = false, connected = true),
                    destNode = clientBaseTarget,
                    myNodeNum = 1,
                    getRemoteNodeDbCandidates = { emptyList() },
                    onConfirmAdminRoute = {},
                    onPreserveFavoritesChange = {},
                    onConfirmRemoteNodeDbAction = { _, _ -> },
                    onDismissPacketResponse = {},
                    onBack = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.node_database)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_add_and_favorite_node)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_add_node)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_remove_favorite)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_remove_node)).assertIsDisplayed()
    }

    @Test
    fun `AdministrationContent omits Add node for non-CLIENT_BASE remote target`() = runComposeUiTest {
        val clientTarget = testNode(num = 99, longName = "Remote Client", role = Config.DeviceConfig.Role.CLIENT)
        setContent {
            AppTheme {
                AdministrationContent(
                    state = RadioConfigState(isLocal = false, connected = true),
                    destNode = clientTarget,
                    myNodeNum = 1,
                    getRemoteNodeDbCandidates = { emptyList() },
                    onConfirmAdminRoute = {},
                    onPreserveFavoritesChange = {},
                    onConfirmRemoteNodeDbAction = { _, _ -> },
                    onDismissPacketResponse = {},
                    onBack = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.node_database)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_add_and_favorite_node)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_add_node)).assertDoesNotExist()
        onNodeWithText(getString(Res.string.remote_admin_remove_favorite)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.remote_admin_remove_node)).assertIsDisplayed()
    }

    @Test
    fun `RemoteNodePickerContent blocks keyless node click on ADD and allows on REMOVE`() = runComposeUiTest {
        val validPeer = testNode(num = 10, longName = "Valid Peer", hasKey = true)
        val keylessPeer = testNode(num = 20, longName = "Keyless Peer", hasKey = false)
        val selected = mutableListOf<Int>()

        setContent {
            AppTheme {
                RemoteNodePickerContent(
                    action = RemoteNodeDbAction.ADD_AND_FAVORITE,
                    destNum = 99,
                    myNodeNum = 10,
                    candidates = listOf(validPeer, keylessPeer),
                    query = "",
                    onQueryChange = {},
                    onSelectNode = { selected += it.num },
                )
            }
        }

        onNodeWithText(getString(Res.string.remote_admin_node_no_public_key)).assertIsDisplayed()
        onNodeWithText("Keyless Peer").performClick()
        assertTrue(selected.isEmpty())

        onNodeWithText("Valid Peer").performClick()
        assertEquals(listOf(10), selected)
    }

    @Test
    fun `RemoteNodePickerContent shows empty search state when query matches nothing`() = runComposeUiTest {
        val validPeer = testNode(num = 10, longName = "Alpha", hasKey = true)
        setContent {
            AppTheme {
                RemoteNodePickerContent(
                    action = RemoteNodeDbAction.REMOVE_FAVORITE,
                    destNum = 99,
                    myNodeNum = 10,
                    candidates = listOf(validPeer),
                    query = "zzz",
                    onQueryChange = {},
                    onSelectNode = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.doc_no_results)).assertIsDisplayed()
    }
}
