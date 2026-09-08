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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataLayerHeartbeatSenderTest {

    @Test
    fun `nonces are strictly increasing and never 1 the firmware NodeInfo-ping trigger`() {
        val sentPackets = mutableListOf<ToRadio>()
        val packetHandler = mock<PacketHandler>(MockMode.autofill)
        every { packetHandler.sendToRadio(any<ToRadio>()) } calls { call -> sentPackets.add(call.arg(0)) }
        val sender = DataLayerHeartbeatSender(packetHandler)

        repeat(5) { sender.sendHeartbeat("test") }

        val nonces = sentPackets.map { assertNotNull(it.heartbeat).nonce }
        assertEquals(5, nonces.size)
        nonces.zipWithNext().forEachIndexed { index, (previous, next) ->
            assertTrue(next > previous, "nonce at index ${index + 1} ($next) must exceed its predecessor ($previous)")
        }
        assertFalse(1 in nonces, "nonce 1 makes the firmware broadcast a NodeInfo ping; got $nonces")
    }
}
