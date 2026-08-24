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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.model.DataPacket

/**
 * A test double for message/packet repository operations.
 *
 * Tracks sent packets and provides test helpers for messaging scenarios.
 */
class FakePacketRepository {
    val sentPackets = mutableListOf<DataPacket>()
    private val _packetsFlow = MutableStateFlow<List<DataPacket>>(emptyList())
    val packetsFlow: Flow<List<DataPacket>> = _packetsFlow

    suspend fun sendPacket(packet: DataPacket) {
        sentPackets.add(packet)
        _packetsFlow.value = sentPackets.toList()
    }

    fun getPacketCount() = sentPackets.size

    fun clear() {
        sentPackets.clear()
        _packetsFlow.value = emptyList()
    }
}

/**
 * A test double for contact management operations.
 *
 * Maintains a list of contacts and provides helpers for contact-related tests.
 */
class FakeContactRepository {
    data class Contact(val userId: String, val name: String, val lastMessageTime: Long = 0)

    private val contacts = mutableMapOf<String, Contact>()
    private val _contactsFlow = MutableStateFlow<List<Contact>>(emptyList())
    val contactsFlow: Flow<List<Contact>> = _contactsFlow

    suspend fun addContact(contact: Contact) {
        contacts[contact.userId] = contact
        _contactsFlow.value = contacts.values.toList()
    }

    suspend fun removeContact(userId: String) {
        contacts.remove(userId)
        _contactsFlow.value = contacts.values.toList()
    }

    suspend fun getContact(userId: String): Contact? = contacts[userId]

    suspend fun updateContactLastMessage(userId: String, time: Long) {
        contacts[userId]?.let { existing ->
            contacts[userId] = existing.copy(lastMessageTime = time)
            _contactsFlow.value = contacts.values.toList()
        }
    }

    fun getContactCount() = contacts.size

    fun getAllContacts() = contacts.values.toList()

    fun clear() {
        contacts.clear()
        _contactsFlow.value = emptyList()
    }
}

/** Test helper for creating test contact objects. */
fun createTestContact(
    userId: String = "!test001",
    name: String = "Test Contact",
    lastMessageTime: Long = 0,
): FakeContactRepository.Contact =
    FakeContactRepository.Contact(userId = userId, name = name, lastMessageTime = lastMessageTime)
