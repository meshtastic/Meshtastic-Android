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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminActionsUseCaseTest {

    private lateinit var radioController: FakeRadioController
    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var useCase: AdminActionsUseCase

    @BeforeTest
    fun setUp() {
        radioController = FakeRadioController()
        nodeRepository = FakeNodeRepository()
        useCase = AdminActionsUseCase(radioController, nodeRepository)
    }

    @Test
    fun `reboot calls radioController`() = runTest {
        val packetId = useCase.reboot(1234)
        assertEquals(1, packetId)
    }

    @Test
    fun `shutdown calls radioController`() = runTest {
        val packetId = useCase.shutdown(1234)
        assertEquals(1, packetId)
    }

    @Test
    fun `factoryReset local node clears local NodeDB`() = runTest {
        nodeRepository.upsert(Node(num = 1))
        useCase.factoryReset(1234, isLocal = true)
        assertTrue(nodeRepository.nodeDBbyNum.value.isEmpty())
    }

    @Test
    fun `nodedbReset local node clears local NodeDB with preserveFavorites`() = runTest {
        nodeRepository.setNodes(listOf(Node(num = 1, isFavorite = true), Node(num = 2, isFavorite = false)))
        useCase.nodedbReset(1234, preserveFavorites = true, isLocal = true)
        assertEquals(1, nodeRepository.nodeDBbyNum.value.size)
        assertTrue(nodeRepository.nodeDBbyNum.value.containsKey(1))
    }

    @Test
    fun `addContact registers packetId and delegates to radioController`() = runTest {
        val contact =
            SharedContact.Builder()
                .also { wb ->
                    wb.node_num = 5678
                    wb.user =
                        User.Builder()
                            .also { u ->
                                u.id = "!0000162e"
                                u.public_key = ByteArray(Node.PUBLIC_KEY_SIZE) { 1 }.toByteString()
                            }
                            .build()
                }
                .build()
        var registeredId: Int? = null

        val packetId = useCase.addContact(destNum = 1234, contact = contact) { registeredId = it }

        assertEquals(packetId, registeredId)
        val req = radioController.adminRequests.single()
        assertEquals("addContact", req.kind)
        assertEquals(1234, req.destNum)
        assertEquals(contact, req.payload)
    }

    @Test
    fun `setRemoteFavorite and removeRemoteNode register packetId and delegate to radioController`() = runTest {
        val favPacketId = useCase.setRemoteFavorite(destNum = 1234, targetNodeNum = 99, favorite = false)
        val removePacketId = useCase.removeRemoteNode(destNum = 1234, targetNodeNum = 77)

        assertEquals(1, favPacketId)
        assertEquals(1, removePacketId)
        assertEquals(2, radioController.adminRequests.size)
        assertEquals("setRemoteFavorite", radioController.adminRequests[0].kind)
        assertEquals(99 to false, radioController.adminRequests[0].payload)
        assertEquals("removeRemoteNode", radioController.adminRequests[1].kind)
        assertEquals(77, radioController.adminRequests[1].payload)
    }
}
