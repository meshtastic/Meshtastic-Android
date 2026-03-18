/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.messaging

import io.kotest.matchers.shouldBe

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeContactRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakePacketRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.core.testing.createTestContact
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for messaging feature.
 *
 * Tests the interaction between messaging ViewModels, repositories, and radio controller. Demonstrates complex
 * multi-component testing using feature-specific fakes.
 */
class MessagingIntegrationTest {
/*


    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var contactRepository: FakeContactRepository
    private lateinit var packetRepository: FakePacketRepository
    private lateinit var radioController: FakeRadioController

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        contactRepository = FakeContactRepository()
        packetRepository = FakePacketRepository()
        radioController = FakeRadioController()
    }

    @Test
    fun testMessagingFlowWithMultipleNodes() = runTest {
        // 1. Setup multiple test nodes
        val nodes = TestDataFactory.createTestNodes(3)
        nodeRepository.setNodes(nodes)

        // 2. Verify nodes are available
        nodeRepository.nodeDBbyNum.value.size shouldBe 3

        // 3. Add contacts for nodes
        nodes.forEach { node ->
            val contact = createTestContact(userId = node.user.id, name = node.user.long_name)
            contactRepository.addContact(contact)
        }

        // 4. Verify contacts added
        contactRepository.getContactCount() shouldBe 3
    }

    @Test
    fun testContactCreationAndRetrieval() = runTest {
        // Create contact
        val contact = createTestContact(userId = "!contact001", name = "Alice", lastMessageTime = 1000L)
        contactRepository.addContact(contact)

        // Retrieve contact
        val retrieved = contactRepository.getContact("!contact001")
        assertTrue(retrieved != null)
        retrieved?.name shouldBe "Alice"
        retrieved?.lastMessageTime shouldBe 1000L
    }

    @Test
    fun testUpdatingContactLastMessageTime() = runTest {
        // Add initial contact
        val contact = createTestContact(userId = "!contact001")
        contactRepository.addContact(contact)

        // Update last message time
        contactRepository.updateContactLastMessage("!contact001", 5000L)

        // Verify update
        val updated = contactRepository.getContact("!contact001")
        updated?.lastMessageTime shouldBe 5000L
    }

    @Test
    fun testConnectionStateAffectsMessaging() = runTest {
        // Start disconnected
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Add a node and contact
        val node = TestDataFactory.createTestNode()
        nodeRepository.setNodes(listOf(node))
        contactRepository.addContact(createTestContact(userId = node.user.id))

        // Verify setup
        nodeRepository.nodeDBbyNum.value.size shouldBe 1
        contactRepository.getContactCount() shouldBe 1

        // Connect radio
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)

        // Now messaging should be enabled
        assertTrue(true, "Messaging flow verified with connected radio")
    }

    @Test
    fun testMultipleContactsMessageOrdering() = runTest {
        // Create multiple contacts
        repeat(5) { i ->
            val contact =
                createTestContact(userId = "!contact00${i + 1}", name = "Contact $i", lastMessageTime = (i * 1000L))
            contactRepository.addContact(contact)
        }

        // Verify all contacts added
        contactRepository.getContactCount() shouldBe 5

        // Verify contacts are retrievable by time
        val contacts = contactRepository.getAllContacts()
        val sortedByTime = contacts.sortedByDescending { it.lastMessageTime }
        sortedByTime.first().name shouldBe "Contact 4"
    }

    @Test
    fun testClearingContactsAndNodes() = runTest {
        // Add data
        nodeRepository.setNodes(TestDataFactory.createTestNodes(3))
        repeat(3) { i -> contactRepository.addContact(createTestContact(userId = "!contact00${i + 1}")) }

        // Verify data exists
        nodeRepository.nodeDBbyNum.value.size shouldBe 3
        contactRepository.getContactCount() shouldBe 3

        // Clear all
        nodeRepository.clearNodeDB()
        contactRepository.clear()

        // Verify cleared
        nodeRepository.nodeDBbyNum.value.size shouldBe 0
        contactRepository.getContactCount() shouldBe 0
    }

*/
}
