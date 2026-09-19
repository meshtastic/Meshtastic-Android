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
import org.meshtastic.core.model.util.byteArrayOfInts
import org.meshtastic.proto.ChannelSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ChannelHashTest {
    private val psk =
        byteArrayOfInts(0xD4, 0xF1, 0xBB, 0x3A, 0x20, 0x29, 0x07, 0x59, 0xF0, 0xBC, 0xFF, 0xAB, 0xCF, 0x4E, 0x69, 0x01)
            .toByteString()

    private val plainSettings =
        ChannelSettings.Builder()
            .also { wb ->
                wb.name = "Secret"
                wb.psk = psk
            }
            .build()

    private val aeadSettings = plainSettings.newBuilder().also { wb -> wb.use_aead = true }.build()

    @Test
    fun defaultChannelHashMatchesFirmware() {
        assertEquals(8, Channel.default.hash)
    }

    @Test
    fun aeadFoldsMarkerIntoHash() {
        val plain = Channel(plainSettings)
        val aead = Channel(aeadSettings)

        assertEquals(plain.hash xor 0xAE, aead.hash)
        assertNotEquals(plain.hash, aead.hash)
    }

    @Test
    fun aeadDoesNotChangeIdentity() {
        assertEquals(Channel(plainSettings), Channel(aeadSettings))
    }
}
