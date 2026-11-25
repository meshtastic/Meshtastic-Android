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
package org.meshtastic.core.model.util

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToSharedContactTest {

    private fun node(
        manuallyVerified: Boolean = false,
        publicKey: ByteString? = null,
        userPublicKey: ByteString = ByteString.EMPTY,
    ) = Node(
        num = 7,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!7"
                wb.long_name = "Seven"
                wb.public_key = userPublicKey
            }
            .build(),
        publicKey = publicKey,
        manuallyVerified = manuallyVerified,
    )

    @Test
    fun `sharing your own contact marks it manually verified`() {
        assertTrue(node().toSharedContact(isOwnContact = true).manually_verified)
    }

    @Test
    fun `relaying someone else's contact asserts nothing on their behalf`() {
        assertFalse(node().toSharedContact(isOwnContact = false).manually_verified)
    }

    @Test
    fun `a contact already verified in person stays verified when relayed`() {
        assertTrue(node(manuallyVerified = true).toSharedContact(isOwnContact = false).manually_verified)
    }

    @Test
    fun `a local display name never reaches the shared contact`() {
        val shared = node().copy(displayName = "Secret").toSharedContact()

        assertEquals("Seven", shared.user?.long_name)
        assertFalse(SharedContact.ADAPTER.encode(shared).decodeToString().contains("Secret"))
        assertFalse(shared.getSharedContactUrl().toString().contains("Secret"))
    }

    @Test
    fun `carries the node number and user through`() {
        val shared = node().toSharedContact()
        assertEquals(7, shared.node_num)
        assertEquals("Seven", shared.user?.long_name)
    }

    @Test
    fun `populates user public_key from Node publicKey when user public_key is empty`() {
        val key = ByteArray(Node.PUBLIC_KEY_SIZE) { (it + 1).toByte() }.toByteString()
        val shared = node(publicKey = key).toSharedContact()
        assertEquals(key, shared.user?.public_key)
    }

    @Test
    fun `strips ERROR_BYTE_STRING instead of exporting sentinel key`() {
        val shared = node(userPublicKey = Node.ERROR_BYTE_STRING).toSharedContact()
        assertEquals(ByteString.EMPTY, shared.user?.public_key)
    }
}
