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
package org.meshtastic.core.model

import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Config
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the gate behind the node-detail Message button. The node has to accept messages, and the radio has to be able
 * to send: an unlicensed radio needs a public key for the destination or it refuses with `PKI_SEND_FAIL_PUBLIC_KEY`,
 * while a licensed radio never uses PKC and sends on the channel.
 */
class CanDirectMessageTest {

    private val key = ByteArray(32) { 0x2B.toByte() }.toByteString()

    private fun node(
        publicKey: okio.ByteString = okio.ByteString.EMPTY,
        isUnmessagable: Boolean? = null,
        role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.CLIENT,
        isLicensed: Boolean = false,
    ) = Node(
        num = 1,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!00000001"
                wb.public_key = publicKey
                wb.is_unmessagable = isUnmessagable
                wb.role = role
                wb.is_licensed = isLicensed
            }
            .build(),
    )

    @Test
    fun allowsMessagableNodeWithAKey() {
        assertTrue(node(publicKey = key).canDirectMessage(localIsLicensed = false))
    }

    @Test
    fun allowsMessagableNodeWithAKeyWhenLocalIsLicensed() {
        assertTrue(node(publicKey = key).canDirectMessage(localIsLicensed = true))
    }

    @Test
    fun blocksNodeWithNoKeyOnFileWhenLocalIsNotLicensed() {
        assertFalse(node().canDirectMessage(localIsLicensed = false))
    }

    @Test
    fun blocksLicensedNodeWithNoKeyWhenLocalIsNotLicensed() {
        assertFalse(node(isLicensed = true).canDirectMessage(localIsLicensed = false))
    }

    @Test
    fun allowsNodeWithNoKeyOnFileWhenLocalIsLicensed() {
        assertTrue(node().canDirectMessage(localIsLicensed = true))
    }

    @Test
    fun blocksUnmessagableNodeEvenWithAKey() {
        assertFalse(node(publicKey = key, isUnmessagable = true).canDirectMessage(localIsLicensed = false))
    }

    @Test
    fun blocksUnmessagableRoleEvenWithAKey() {
        assertFalse(
            node(publicKey = key, role = Config.DeviceConfig.Role.REPEATER).canDirectMessage(localIsLicensed = false),
        )
    }

    @Test
    fun blocksUnmessagableNodeEvenWhenLocalIsLicensed() {
        assertFalse(node(isUnmessagable = true).canDirectMessage(localIsLicensed = true))
    }

    @Test
    fun blocksUnmessagableRoleEvenWhenLocalIsLicensed() {
        assertFalse(node(role = Config.DeviceConfig.Role.REPEATER).canDirectMessage(localIsLicensed = true))
    }

    @Test
    fun readsLicensedStateFromOurNode() {
        assertTrue(node(isLicensed = true).isLicensedLocal)
        assertFalse(node(isLicensed = false).isLicensedLocal)
        assertFalse((null as Node?).isLicensedLocal)
    }

    @Test
    fun stillOffersTheActionWhenAThreadAlreadyExists() {
        val keyless = node()

        assertFalse(keyless.canDirectMessage(localIsLicensed = false))
        assertTrue(keyless.showsDirectMessageAction(hasConversation = true, localIsLicensed = false))
    }

    @Test
    fun hidesTheActionWhenThereIsNoThreadToOpen() {
        assertFalse(node().showsDirectMessageAction(hasConversation = false, localIsLicensed = false))
    }

    @Test
    fun offersTheActionForAKeylessNodeWhenLocalIsLicensedAndNoThreadExists() {
        assertTrue(node().showsDirectMessageAction(hasConversation = false, localIsLicensed = true))
    }

    @Test
    fun directMessagePathNeedsAKeyUnlessLocalIsLicensed() {
        assertTrue(node(publicKey = key).hasDirectMessagePath(localIsLicensed = false))
        assertFalse(node().hasDirectMessagePath(localIsLicensed = false))
        assertTrue(node().hasDirectMessagePath(localIsLicensed = true))
    }

    @Test
    fun directMessagePathIgnoresRole() {
        assertFalse(node(role = Config.DeviceConfig.Role.REPEATER).hasDirectMessagePath(localIsLicensed = false))
        assertTrue(node(publicKey = key, role = Config.DeviceConfig.Role.REPEATER).hasDirectMessagePath(false))
    }
}
