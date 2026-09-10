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

import org.meshtastic.core.model.Node
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToSharedContactTest {

    private fun node(manuallyVerified: Boolean = false) =
        Node(num = 7, user = User(id = "!7", long_name = "Seven"), manuallyVerified = manuallyVerified)

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
    fun `carries the node number and user through`() {
        val shared = node().toSharedContact()
        assertEquals(7, shared.node_num)
        assertEquals("Seven", shared.user?.long_name)
    }
}
