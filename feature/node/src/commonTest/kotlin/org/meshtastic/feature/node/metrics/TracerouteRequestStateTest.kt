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
package org.meshtastic.feature.node.metrics

import org.meshtastic.core.model.MeshLog
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals

class TracerouteRequestStateTest {

    private fun log(uuid: String, packet: MeshPacket, receivedDate: Long) = MeshLog(
        uuid = uuid,
        message_type = "Packet",
        received_date = receivedDate,
        raw_message = "",
        fromRadio = FromRadio.Builder().also { wb -> wb.packet = packet }.build(),
    )

    private fun packet(id: Int) = MeshPacket.Builder().also { wb -> wb.id = id }.build()

    private fun requested(sentAt: Long, answered: Boolean = false) = TraceroutePoint(
        kind = TracerouteKind.REQUESTED,
        request = log("req", packet(REQUEST_ID), sentAt),
        result = if (answered) log("res", packet(2), sentAt + 1_000) else null,
        timeSeconds = sentAt / 1000.0,
        forwardHops = null,
        returnHops = null,
        roundTripSeconds = null,
    )

    @Test
    fun `an unanswered request is waiting inside the answer window`() {
        assertEquals(
            TracerouteRequestState.WAITING,
            tracerouteRequestState(requested(SENT), emptyMap(), SENT + TRACEROUTE_ANSWER_WINDOW_MS - 1),
        )
    }

    @Test
    fun `an unanswered request reads as no response once the window passes`() {
        assertEquals(
            TracerouteRequestState.NO_RESPONSE,
            tracerouteRequestState(requested(SENT), emptyMap(), SENT + TRACEROUTE_ANSWER_WINDOW_MS),
        )
    }

    @Test
    fun `a rate limit refusal reads differently from silence`() {
        val naks = mapOf(REQUEST_ID to Routing.Error.RATE_LIMIT_EXCEEDED.value)

        assertEquals(TracerouteRequestState.RATE_LIMITED, tracerouteRequestState(requested(SENT), naks, SENT + 1))
    }

    @Test
    fun `any other refusal is no response without waiting out the window`() {
        val naks = mapOf(REQUEST_ID to Routing.Error.NO_RESPONSE.value)

        assertEquals(TracerouteRequestState.NO_RESPONSE, tracerouteRequestState(requested(SENT), naks, SENT + 1))
    }

    @Test
    fun `a refusal for another request leaves this one waiting`() {
        val naks = mapOf(REQUEST_ID + 1 to Routing.Error.RATE_LIMIT_EXCEEDED.value)

        assertEquals(TracerouteRequestState.WAITING, tracerouteRequestState(requested(SENT), naks, SENT + 1))
    }

    @Test
    fun `an answer wins over a stale refusal`() {
        val naks = mapOf(REQUEST_ID to Routing.Error.RATE_LIMIT_EXCEEDED.value)

        assertEquals(
            TracerouteRequestState.ANSWERED,
            tracerouteRequestState(requested(SENT, answered = true), naks, SENT + 1),
        )
    }

    private companion object {
        const val REQUEST_ID = 42
        const val SENT = 1_700_000_000_000L
    }
}
