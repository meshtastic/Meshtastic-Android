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
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeRadioConfigRepository
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.NodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemoteNodeDbAdminTest {

    private data class Sent(val target: Int?, val call: String, val node: Int, val flag: Boolean? = null)

    private val serviceRepository = ServiceRepositoryImpl().apply { setConnectionState(ConnectionState.Connected) }
    private val radioConfigRepository = FakeRadioConfigRepository()
    private val nodeManager: NodeManager =
        mock<NodeManager>(MockMode.autofill).also { every { it.myNodeNum } returns MutableStateFlow(MY_NODE) }

    private val sent = mutableListOf<Sent>()
    private val sentContacts = mutableListOf<SharedContact>()
    private var answer: AdminResult<Unit> = AdminResult.Success(Unit)

    /** The session's admin API; [target] is the node [AdminApi.forNode] addressed, null for the local radio. */
    private fun admin(target: Int?): AdminApi = object : AdminApi by mock<AdminApi>(MockMode.autofill) {
        override fun forNode(dest: NodeId): AdminApi = admin(dest.raw)

        override suspend fun addContact(contact: SharedContact): AdminResult<Unit> {
            sent += Sent(target, "addContact", contact.node_num)
            sentContacts += contact
            return answer
        }

        override suspend fun setFavorite(node: NodeId, favorite: Boolean): AdminResult<Unit> {
            sent += Sent(target, "setFavorite", node.raw, favorite)
            return answer
        }

        override suspend fun setIgnored(node: NodeId, ignored: Boolean): AdminResult<Unit> {
            sent += Sent(target, "setIgnored", node.raw, ignored)
            return answer
        }

        override suspend fun removeNode(node: NodeId): AdminResult<Unit> {
            sent += Sent(target, "removeNode", node.raw)
            return answer
        }
    }

    private val radio: RadioInterfaceService =
        mock<RadioInterfaceService>(MockMode.autofill).also {
            every { it.activeSession } returns MutableStateFlow(RadioSessionContext(1L, "tcp:radio"))
            every { it.adminForSession(any()) } returns admin(target = null)
        }

    private fun TestScope.controller() = AdminControllerImpl(
        nodeManager = nodeManager,
        radio = radio,
        sessionAdmin = SessionAdmin(radio, backgroundScope.asServiceScope()),
        heardOnCurrentLoraRefresher =
        HeardOnCurrentLoraRefresher(
            nodeManager = nodeManager,
            meshConfigFlowManager = lazy { mock<MeshConfigFlowManager>(MockMode.autofill) },
            serviceRepository = serviceRepository,
            radioConfigRepository = radioConfigRepository,
            radioOperationLock = RadioOperationLock(),
            scope = backgroundScope,
        ),
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

        assertEquals(listOf(Sent(REMOTE, "addContact", CONTACT)), sent)
        assertEquals(KEY, sentContacts.single().user?.public_key)
    }

    @Test
    fun addRemoteContactRefusesAKeylessOrZeroKeyContactWithoutSending() = runTest {
        val controller = controller()
        assertFalse(controller.addRemoteContact(REMOTE, contact(key = ByteString.EMPTY)))
        assertFalse(controller.addRemoteContact(REMOTE, contact(key = Node.ERROR_BYTE_STRING)))

        assertTrue(sent.isEmpty())
    }

    @Test
    fun setRemoteFavoriteSendsSetOrRemoveFavoriteToTheRemoteNode() = runTest {
        val controller = controller()
        controller.setRemoteFavorite(REMOTE, CONTACT, favorite = true)
        controller.setRemoteFavorite(REMOTE, CONTACT, favorite = false)

        assertEquals(
            listOf(Sent(REMOTE, "setFavorite", CONTACT, true), Sent(REMOTE, "setFavorite", CONTACT, false)),
            sent,
        )
    }

    @Test
    fun setRemoteIgnoredSendsSetOrRemoveIgnoredToTheRemoteNode() = runTest {
        val controller = controller()
        controller.setRemoteIgnored(REMOTE, CONTACT, ignored = true)
        controller.setRemoteIgnored(REMOTE, CONTACT, ignored = false)

        assertEquals(
            listOf(Sent(REMOTE, "setIgnored", CONTACT, true), Sent(REMOTE, "setIgnored", CONTACT, false)),
            sent,
        )
    }

    @Test
    fun removeRemoteNodeSendsRemoveByNodenumToTheRemoteNode() = runTest {
        assertTrue(controller().removeRemoteNode(REMOTE, CONTACT))

        assertEquals(listOf(Sent(REMOTE, "removeNode", CONTACT)), sent)
    }

    @Test
    fun remoteNodeDbWritesReportFailureWhenTheRemoteNodeDoesNotAcknowledge() = runTest {
        answer = AdminResult.Timeout
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
