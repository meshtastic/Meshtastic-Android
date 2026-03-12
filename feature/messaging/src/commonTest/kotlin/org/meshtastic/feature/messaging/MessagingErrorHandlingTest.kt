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

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.testing.FakeContactRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.core.testing.createTestContact
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Error handling tests for messaging feature.
 *
 * Tests failure scenarios, recovery paths, and edge cases.
 */
class MessagingErrorHandlingTest {

    private lateinit var nodeRepository: FakeNodeRepository
    private lateinit var contactRepository: FakeContactRepository
    private lateinit var radioController: FakeRadioController

    @BeforeTest
    fun setUp() {
        nodeRepository = FakeNodeRepository()
        contactRepository = FakeContactRepository()
        radioController = FakeRadioController()
    }

    @Test
    fun testMessagingWhenDisconnected() = runTest {
        // Set radio to disconnected
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Try to add contact (should still work for local storage)
        val contact = createTestContact(userId = "!test001")
        contactRepository.addContact(contact)

        // Verify contact was added despite disconnection
        assertEquals(1, contactRepository.getContactCount())
    }

    @Test
    fun testRetrievingNonexistentContact() = runTest {
        // Try to get contact that doesn't exist
        val contact = contactRepository.getContact("!nonexistent")

        // Should return null gracefully
        assertTrue(contact == null)
    }

    @Test
    fun testRemovingNonexistentContact() = runTest {
        // Remove contact that was never added
        contactRepository.removeContact("!nonexistent")

        // Should not crash, just be a no-op
        assertEquals(0, contactRepository.getContactCount())
    }

    @Test
    fun testClearingEmptyContactList() = runTest {
        // Clear empty contacts
        contactRepository.clear()

        // Should remain empty without errors
        assertEquals(0, contactRepository.getContactCount())
    }

    @Test
    fun testAddingContactWhileDisconnected() = runTest {
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Add multiple contacts
        repeat(3) { i -> contactRepository.addContact(createTestContact(userId = "!contact00${i + 1}")) }

        // Should still work (local operation)
        assertEquals(3, contactRepository.getContactCount())
    }

    @Test
    fun testReconnectionAfterDisconnection() = runTest {
        // Start disconnected
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Disconnected)

        // Add contacts while disconnected
        contactRepository.addContact(createTestContact(userId = "!contact001"))

        // Verify added
        assertEquals(1, contactRepository.getContactCount())

        // Now reconnect
        radioController.setConnectionState(org.meshtastic.core.model.ConnectionState.Connected)

        // Contacts should still be there
        assertEquals(1, contactRepository.getContactCount())
    }

    @Test
    fun testLargeContactListHandling() = runTest {
        // Add many contacts
        repeat(100) { i ->
            contactRepository.addContact(
                createTestContact(userId = "!contact${i.toString().padStart(4, '0')}", name = "Contact $i"),
            )
        }

        // Should handle large list
        assertEquals(100, contactRepository.getContactCount())

        // Should be able to retrieve any contact
        val contact = contactRepository.getContact("!contact0050")
        assertTrue(contact != null)
        assertEquals("Contact 50", contact?.name)
    }

    @Test
    fun testDuplicateContactHandling() = runTest {
        val contact = createTestContact(userId = "!contact001", name = "Alice")

        // Add same contact twice
        contactRepository.addContact(contact)
        contactRepository.addContact(contact)

        // Should overwrite, not duplicate
        assertEquals(1, contactRepository.getContactCount())
    }

    @Test
    fun testContactMessageTimeUpdate() = runTest {
        val contact = createTestContact(userId = "!contact001")
        contactRepository.addContact(contact)

        // Update message time multiple times
        contactRepository.updateContactLastMessage("!contact001", 1000L)
        contactRepository.updateContactLastMessage("!contact001", 2000L)
        contactRepository.updateContactLastMessage("!contact001", 3000L)

        // Should have latest time
        val updated = contactRepository.getContact("!contact001")
        assertEquals(3000L, updated?.lastMessageTime)
    }

    @Test
    fun testClearAndRebuild() = runTest {
        // Add contacts
        contactRepository.addContact(createTestContact(userId = "!contact001"))
        contactRepository.addContact(createTestContact(userId = "!contact002"))
        assertEquals(2, contactRepository.getContactCount())

        // Clear all
        contactRepository.clear()
        assertEquals(0, contactRepository.getContactCount())

        // Add new contacts
        contactRepository.addContact(createTestContact(userId = "!contact003"))
        assertEquals(1, contactRepository.getContactCount())
    }
}
