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
package org.meshtastic.feature.node.model

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Node
import org.meshtastic.proto.Config
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the gate behind the node-detail Message button. Both signals have to pass: the node has to accept messages,
 * and we need a public key for it or the sending radio refuses with `PKI_SEND_FAIL_PUBLIC_KEY`.
 */
class CanDirectMessageTest {

    private val key = ByteArray(32) { 0x2B.toByte() }.toByteString()

    private fun node(
        publicKey: okio.ByteString = okio.ByteString.EMPTY,
        isUnmessagable: Boolean? = null,
        role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.CLIENT,
    ) = Node(
        num = 1,
        user = User(id = "!00000001", public_key = publicKey, is_unmessagable = isUnmessagable, role = role),
    )

    @Test
    fun allowsMessagableNodeWithAKey() {
        assertTrue(node(publicKey = key).canDirectMessage)
    }

    @Test
    fun blocksNodeWithNoKeyOnFile() {
        assertFalse(node().canDirectMessage)
    }

    @Test
    fun blocksUnmessagableNodeEvenWithAKey() {
        assertFalse(node(publicKey = key, isUnmessagable = true).canDirectMessage)
    }

    @Test
    fun blocksUnmessagableRoleEvenWithAKey() {
        assertFalse(node(publicKey = key, role = Config.DeviceConfig.Role.REPEATER).canDirectMessage)
    }

    @Test
    fun stillOffersTheActionWhenAThreadAlreadyExists() {
        val keyless = node()

        assertFalse(keyless.canDirectMessage)
        assertTrue(keyless.showsDirectMessageAction(hasConversation = true))
    }

    @Test
    fun hidesTheActionWhenThereIsNoThreadToOpen() {
        assertFalse(node().showsDirectMessageAction(hasConversation = false))
    }
}
