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

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.util.byteArrayOfInts
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelPublicKeyTest {
    private val defaultKey =
        byteArrayOfInts(0xD4, 0xF1, 0xBB, 0x3A, 0x20, 0x29, 0x07, 0x59, 0xF0, 0xBC, 0xFF, 0xAB, 0xCF, 0x4E, 0x69, 0x01)

    private fun settings(psk: ByteString) = ChannelSettings.Builder().also { wb -> wb.psk = psk }.build()

    private val unset = settings(ByteString.EMPTY)
    private val private16 = settings(ByteArray(16) { it.toByte() }.toByteString())
    private val private32 = settings(ByteArray(32) { it.toByte() }.toByteString())

    @Test
    fun `the shipped default channel is public`() {
        assertTrue(Channel.usesPublicKey(Channel.default.settings))
    }

    @Test
    fun `every one byte key alias is public`() {
        listOf(0, 1, 2, 255).forEach { alias ->
            assertTrue(Channel.usesPublicKey(settings(byteArrayOfInts(alias).toByteString())), "alias $alias")
        }
    }

    @Test
    fun `the expanded default key is public whatever its last byte`() {
        assertTrue(Channel.usesPublicKey(settings(defaultKey.toByteString())))
        val sibling = defaultKey.copyOf().also { it[it.lastIndex] = 0x0A }
        assertTrue(Channel.usesPublicKey(settings(sibling.toByteString())))
    }

    @Test
    fun `a key that differs from the default before its last byte is private`() {
        val other = defaultKey.copyOf().also { it[0] = 0x00 }
        assertFalse(Channel.usesPublicKey(settings(other.toByteString())))
        assertFalse(Channel.usesPublicKey(private16))
        assertFalse(Channel.usesPublicKey(private32))
    }

    @Test
    fun `an unset primary key is public`() {
        assertTrue(Channel.usesPublicKey(unset))
    }

    @Test
    fun `an unset secondary key follows the primary`() {
        assertTrue(Channel.usesPublicKey(unset, primary = Channel.default.settings))
        assertTrue(Channel.usesPublicKey(unset, primary = unset))
        assertFalse(Channel.usesPublicKey(unset, primary = private32))
    }

    @Test
    fun `a secondary with its own key ignores the primary`() {
        assertFalse(Channel.usesPublicKey(private32, primary = Channel.default.settings))
        assertTrue(Channel.usesPublicKey(Channel.default.settings, primary = private32))
    }
}
