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

import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.message_status_ack_proof_invalid
import org.meshtastic.core.resources.message_status_recipient_delivered
import org.meshtastic.core.resources.message_status_recipient_delivered_proven
import org.meshtastic.proto.MeshPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AckProofStatusTest {

    @Test
    fun `an absent proof shows nothing extra`() {
        assertNull(getAckProofStatusRes(MeshPacket.AckProofStatus.ACK_PROOF_ABSENT.value))
        assertFalse(isAckProofForged(MeshPacket.AckProofStatus.ACK_PROOF_ABSENT.value))
    }

    @Test
    fun `an unknown value reads as absent`() {
        assertNull(getAckProofStatusRes(99))
        assertFalse(isAckProofForged(99))
    }

    @Test
    fun `every carried proof gets its own row whatever the verdict`() {
        listOf(
            MeshPacket.AckProofStatus.ACK_PROOF_VALID,
            MeshPacket.AckProofStatus.ACK_PROOF_INVALID,
            MeshPacket.AckProofStatus.ACK_PROOF_NO_KEY,
        )
            .forEach { assertNotNull(getAckProofStatusRes(it.value), "no row for $it") }
    }

    @Test
    fun `only a failed proof counts as forged`() {
        assertTrue(isAckProofForged(MeshPacket.AckProofStatus.ACK_PROOF_INVALID.value))
        assertFalse(isAckProofForged(MeshPacket.AckProofStatus.ACK_PROOF_VALID.value))
        assertFalse(isAckProofForged(MeshPacket.AckProofStatus.ACK_PROOF_NO_KEY.value))
    }

    @Test
    fun `a proven ack is the only delivery the status line calls proven`() {
        val proven =
            getMessageStatusStringRes(
                status = MessageStatus.RECEIVED,
                routingError = 0,
                isDirectMessage = true,
                ackProofStatus = MeshPacket.AckProofStatus.ACK_PROOF_VALID.value,
            )
        assertEquals(Res.string.message_status_recipient_delivered_proven, proven.second)

        listOf(MeshPacket.AckProofStatus.ACK_PROOF_ABSENT, MeshPacket.AckProofStatus.ACK_PROOF_NO_KEY).forEach {
            val unproven =
                getMessageStatusStringRes(
                    status = MessageStatus.RECEIVED,
                    routingError = 0,
                    isDirectMessage = true,
                    ackProofStatus = it.value,
                )
            assertEquals(Res.string.message_status_recipient_delivered, unproven.second, "wrong text for $it")
        }
    }

    @Test
    fun `a failed proof replaces the delivery text rather than reading as delivered`() {
        val forged =
            getMessageStatusStringRes(
                status = MessageStatus.RECEIVED,
                routingError = 0,
                isDirectMessage = true,
                ackProofStatus = MeshPacket.AckProofStatus.ACK_PROOF_INVALID.value,
            )
        assertEquals(Res.string.message_status_ack_proof_invalid, forged.second)
    }
}
