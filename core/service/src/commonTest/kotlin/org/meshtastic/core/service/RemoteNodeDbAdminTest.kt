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
package org.meshtastic.core.service

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AwaitedSendResult
import org.meshtastic.core.repository.AwaitedSendStatus
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.testing.FakeCommandSender
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteNodeDbAdminTest {

    private val commandSender = FakeCommandSender()
    private val serviceRepository = ServiceRepositoryImpl().apply { setConnectionState(ConnectionState.Connected) }
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val nodeManager: NodeManager =
        mock<NodeManager>(MockMode.autofill).also { every { it.myNodeNum } returns MutableStateFlow(MY_NODE) }

    private fun TestScope.controller() = AdminControllerImpl(
        commandSender = commandSender,
        nodeManager = nodeManager,
        radioConfigRepository = radioConfigRepository,
        connectionStateProvider = serviceRepository,
        heardOnCurrentLoraRefresher =
        HeardOnCurrentLoraRefresher(
            nodeManager = nodeManager,
            meshConfigFlowManager = lazy { mock<MeshConfigFlowManager>(MockMode.autofill) },
            serviceRepository = serviceRepository,
            radioConfigRepository = radioConfigRepository,
            radioOperationLock = RadioOperationLock(),
            scope = backgroundScope,
        ),
        scope = backgroundScope,
    )

    private fun contact(key: ByteString = KEY) = SharedContact.Builder()
        .also { wb ->
            wb.node_num = CONTACT
            wb.user = User.Builder().also { u -> u.id = "!5264ff52" }.also { u -> u.public_key = key }.build()
        }
        .build()

    @Test
    fun addRemoteContactSendsAddContactToTheRemoteNodeNotTheLocalOne() = runTest {
        assertTrue(controller().addRemoteContact(REMOTE, contact()))

        val request = commandSender.adminRequests.single()
        assertEquals(REMOTE, request.destNum)
        assertEquals(CONTACT, request.message.add_contact?.node_num)
        assertEquals(KEY, request.message.add_contact?.user?.public_key)
    }

    @Test
    fun addRemoteContactRefusesAKeylessContactWithoutSending() = runTest {
        assertFalse(controller().addRemoteContact(REMOTE, contact(key = ByteString.EMPTY)))

        assertTrue(commandSender.adminRequests.isEmpty())
    }

    @Test
    fun setRemoteFavoriteSendsSetOrRemoveFavoriteToTheRemoteNode() = runTest {
        val controller = controller()
        controller.setRemoteFavorite(REMOTE, CONTACT, favorite = true)
        controller.setRemoteFavorite(REMOTE, CONTACT, favorite = false)

        val (set, remove) = commandSender.adminRequests
        assertEquals(REMOTE, set.destNum)
        assertEquals(CONTACT, set.message.set_favorite_node)
        assertNull(set.message.remove_favorite_node)
        assertEquals(REMOTE, remove.destNum)
        assertEquals(CONTACT, remove.message.remove_favorite_node)
        assertNull(remove.message.set_favorite_node)
    }

    @Test
    fun setRemoteIgnoredSendsSetOrRemoveIgnoredToTheRemoteNode() = runTest {
        val controller = controller()
        controller.setRemoteIgnored(REMOTE, CONTACT, ignored = true)
        controller.setRemoteIgnored(REMOTE, CONTACT, ignored = false)

        val (ignore, unignore) = commandSender.adminRequests
        assertEquals(REMOTE, ignore.destNum)
        assertEquals(CONTACT, ignore.message.set_ignored_node)
        assertNull(ignore.message.remove_ignored_node)
        assertEquals(REMOTE, unignore.destNum)
        assertEquals(CONTACT, unignore.message.remove_ignored_node)
        assertNull(unignore.message.set_ignored_node)
    }

    @Test
    fun removeRemoteNodeSendsRemoveByNodenumToTheRemoteNode() = runTest {
        assertTrue(controller().removeRemoteNode(REMOTE, CONTACT))

        val request = commandSender.adminRequests.single()
        assertEquals(REMOTE, request.destNum)
        assertEquals(CONTACT, request.message.remove_by_nodenum)
    }

    @Test
    fun remoteNodeDbWritesReportFailureWhenTheRemoteNodeDoesNotAcknowledge() = runTest {
        commandSender.awaitedAdminResult = AwaitedSendResult(AwaitedSendStatus.TIMED_OUT, departureEpochAtDispatch = 0)
        val controller = controller()

        assertFalse(controller.addRemoteContact(REMOTE, contact()))
        assertFalse(controller.setRemoteFavorite(REMOTE, CONTACT, favorite = true))
    }

    private companion object {
        const val MY_NODE = 0x3F8B0E01
        const val REMOTE = 0x42C91376
        const val CONTACT = 0x5264FF52
        val KEY = ByteArray(32) { it.toByte() }.toByteString()
    }
}
