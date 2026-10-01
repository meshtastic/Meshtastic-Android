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
package org.meshtastic.feature.node.metrics.terminal

import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.RemoteShell
import org.meshtastic.proto.RemoteShell.OpCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SID = 0x1234

private fun server(
    op: OpCode,
    seq: Int,
    ack: Int = 0,
    payload: String = "",
    lastRx: Int = 0,
    lastTx: Int = 0,
    session: Int = SID,
): RemoteShell = shellFrame(op) { wb ->
    wb.session_id = session
    wb.seq = seq
    wb.ack_seq = ack
    wb.payload = payload.encodeUtf8()
    wb.last_rx_seq = lastRx
    wb.last_tx_seq = lastTx
}

/** OPEN at t=0, OPEN_OK (seq 1, acking our OPEN) at t=100. */
private fun openedLink(): RemoteShellLink = RemoteShellLink(SID).apply {
    open(cols = 80, rows = 24, nowMs = 0)
    receive(server(OpCode.OPEN_OK, seq = 1, ack = 1), nowMs = 100)
}

private fun ShellStep.only(op: OpCode) = send.filter { it.op == op }

private fun typed(bytes: Int) = "x".repeat(bytes).encodeUtf8()

class RemoteShellLinkTest {

    @Test
    fun openCarriesTheTerminalSizeAsSequenceOne() {
        val step = RemoteShellLink(SID).open(cols = 54, rows = 29, nowMs = 0)
        val open = step.send.single()
        assertEquals(OpCode.OPEN, open.op)
        assertEquals(1, open.seq)
        assertEquals(54, open.cols)
        assertEquals(29, open.rows)
        assertEquals(0, open.flags)
    }

    @Test
    fun openOkOpensTheSession() {
        val link = RemoteShellLink(SID)
        link.open(80, 24, 0)
        val step = link.receive(server(OpCode.OPEN_OK, seq = 1, ack = 1), 100)
        assertTrue(link.isOpen)
        assertEquals(listOf<ShellEvent>(ShellEvent.Opened), step.events)
    }

    @Test
    fun aOneWayStreamIsAcknowledgedEverySecondFrame() {
        val link = openedLink()
        val first = link.receive(server(OpCode.OUTPUT, seq = 2, ack = 1, payload = "a"), 200)
        val ack = first.only(OpCode.ACK).single()
        assertEquals(2, ack.ack_seq)
        assertEquals(0, ack.last_rx_seq)

        assertTrue(link.receive(server(OpCode.OUTPUT, seq = 3, ack = 1, payload = "b"), 300).send.isEmpty())
        assertEquals(4, link.receive(server(OpCode.OUTPUT, seq = 4, ack = 1, payload = "c"), 400).send.single().ack_seq)
    }

    @Test
    fun aDuplicateIsAnsweredWithTheCursor() {
        val link = openedLink()
        link.receive(server(OpCode.OUTPUT, seq = 2, ack = 1, payload = "a"), 200)
        val ack = link.receive(server(OpCode.OUTPUT, seq = 2, ack = 1, payload = "a"), 1_200).send.single()
        assertEquals(OpCode.ACK, ack.op)
        assertEquals(2, ack.ack_seq)
        assertEquals(0, ack.last_rx_seq)
    }

    @Test
    fun aGapAsksForTheMissingFrameAndDeliversInOrderOnceFilled() {
        val link = openedLink()
        val gap = link.receive(server(OpCode.OUTPUT, seq = 3, ack = 1, payload = "c"), 200)
        assertEquals(1, gap.only(OpCode.ACK).single().last_rx_seq)
        assertTrue(gap.events.isEmpty())

        val filled = link.receive(server(OpCode.OUTPUT, seq = 2, ack = 1, payload = "b"), 300)
        assertEquals(
            listOf("b", "c"),
            filled.events.filterIsInstance<ShellEvent.Output>().map { it.bytes.utf8() },
        )
    }

    @Test
    fun aReplayRequestResendsThatFrame() {
        val link = openedLink()
        link.input("ls\r".encodeUtf8(), 200)
        val replay = link.receive(server(OpCode.ACK, seq = 0, ack = 1, lastRx = 1), 300).send.single()
        assertEquals(OpCode.INPUT, replay.op)
        assertEquals(2, replay.seq)
        assertEquals("ls\r", replay.payload.utf8())
    }

    @Test
    fun inputBeyondTheWindowWaitsForTheCursor() {
        val link = openedLink()
        val burst = link.input(typed(MAX_INPUT_CHUNK_BYTES * 6), 200)
        assertEquals(listOf(2, 3, 4, 5), burst.only(OpCode.INPUT).map { it.seq })

        val reopened = link.receive(server(OpCode.OUTPUT, seq = 2, ack = 3, payload = "x"), 300)
        assertEquals(listOf(6, 7), reopened.only(OpCode.INPUT).map { it.seq })
    }

    @Test
    fun aShutWindowRetransmitsItsOldestFrameThenGivesUp() {
        val link = openedLink()
        link.input(typed(MAX_INPUT_CHUNK_BYTES * 4), 200)

        var resends = 0
        var closed: ShellEvent.Closed? = null
        var sentClose = false
        var now = 250L
        while (now < 600_000 && closed == null) {
            val step = link.tick(now)
            resends += step.send.count { it.op == OpCode.INPUT && it.seq == 2 }
            sentClose = sentClose || step.send.any { it.op == OpCode.CLOSE }
            closed = step.events.filterIsInstance<ShellEvent.Closed>().firstOrNull()
            now += 250
        }
        assertEquals(MAX_RETRANSMITS, resends)
        assertTrue(sentClose)
        assertTrue(link.isClosed)
    }

    @Test
    fun aMissingOpenOkIsRecoveredByRepeatingOpenThenTimesOut() {
        val link = RemoteShellLink(SID)
        link.open(80, 24, 0)
        val opens = mutableListOf<Long>()
        var closed = false
        var now = 250L
        while (!closed) {
            val step = link.tick(now)
            step.only(OpCode.OPEN).forEach {
                assertEquals(1, it.seq)
                opens += now
            }
            closed = step.events.any { it is ShellEvent.Closed }
            now += 250
        }
        assertEquals(listOf(5_000L, 15_000L, 35_000L), opens)
        assertEquals(OPEN_TIMEOUT_MS, now - 250)
    }

    @Test
    fun closedIsActedOnOutOfOrder() {
        val link = openedLink()
        val step = link.receive(server(OpCode.CLOSED, seq = 9, ack = 1, payload = "pty_eof"), 200)
        assertEquals(listOf<ShellEvent>(ShellEvent.Closed("pty_eof")), step.events)
        assertTrue(link.isClosed)
    }

    @Test
    fun aSequencedErrorKeepsTheSessionAndASessionlessOneEndsIt() {
        val link = openedLink()
        val error = link.receive(server(OpCode.ERROR, seq = 2, ack = 1, payload = "unsupported_op"), 200)
        assertEquals(listOf<ShellEvent>(ShellEvent.RemoteError("unsupported_op")), error.events)
        assertFalse(link.isClosed)

        link.receive(server(OpCode.ERROR, seq = 0, payload = "invalid_session"), 300)
        assertTrue(link.isClosed)
    }

    @Test
    fun inboundSilenceSendsAPingWithBothCursors() {
        val link = openedLink()
        assertTrue(link.tick(5_000).send.isEmpty())
        val ping = link.tick(5_100).only(OpCode.PING).single()
        assertEquals(1, ping.last_rx_seq)
        assertEquals(1, ping.last_tx_seq)
        assertTrue(link.tick(5_350).only(OpCode.PING).isEmpty())
        assertEquals(1, link.tick(20_100).only(OpCode.PING).size)
    }

    @Test
    fun framesForAnotherSessionAreIgnored() {
        val link = openedLink()
        val step = link.receive(server(OpCode.OUTPUT, seq = 2, payload = "x", session = SID + 1), 200)
        assertTrue(step.send.isEmpty() && step.events.isEmpty())
    }
}
