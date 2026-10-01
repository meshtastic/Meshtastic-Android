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

import okio.Buffer
import okio.ByteString
import org.meshtastic.proto.RemoteShell

/** Frames a peer may hold unacknowledged before it must wait; the firmware's `DEFAULT_TX_WINDOW_FRAMES`. */
internal const val INPUT_WINDOW_FRAMES = 4

/** In-order frames received with nothing sent back before a bare ACK is owed; the firmware's `ACK_AFTER_RX_FRAMES`. */
internal const val ACK_AFTER_FRAMES = 2

/** Repeats of one unacknowledged frame before the peer is declared gone; the firmware's bound too. */
internal const val MAX_RETRANSMITS = 25

internal const val MAX_INPUT_CHUNK_BYTES = 64
internal const val PENDING_INPUT_MAX_BYTES = 4096
internal const val TX_HISTORY_LEN = 50
internal const val REORDER_SLOTS = 8

internal const val RETRY_MIN_MS = 1_000L
internal const val RETRY_MAX_MS = 8_000L
internal const val RETRY_FLAT_ATTEMPTS = 4
private const val RETRANSMIT_LATENCY_MULTIPLIER = 2.0
private const val ACK_LATENCY_SMOOTHING = 0.25

internal const val OPEN_RETRY_FIRST_MS = 5_000L
internal const val OPEN_RETRY_MAX_MS = 30_000L
internal const val OPEN_TIMEOUT_MS = 60_000L

internal const val HEARTBEAT_IDLE_DELAY_MS = 5_000L
internal const val HEARTBEAT_REPEAT_MS = 15_000L

internal sealed interface ShellEvent {
    data object Opened : ShellEvent

    data class Output(val bytes: ByteString) : ShellEvent

    /** A sequenced ERROR: the session survives it. */
    data class RemoteError(val message: String) : ShellEvent

    data class Closed(val reason: String) : ShellEvent

    data class InputDropped(val bytes: Int) : ShellEvent
}

internal class ShellStep(val send: List<RemoteShell>, val events: List<ShellEvent>)

internal fun shellFrame(op: RemoteShell.OpCode, configure: (RemoteShell.Builder) -> Unit = {}): RemoteShell =
    RemoteShell.Builder()
        .also { wb ->
            wb.op = op
            configure(wb)
        }
        .build()

/**
 * The client half of the firmware DMShell reliability layer, as `bin/dmshell_client.py` implements it.
 *
 * Every sequenced frame carries our receive cursor in `ack_seq`; the peer's cursor over our frames is the larger of
 * `ack_seq` and `last_rx_seq` on anything it sends. Either side holds at most [INPUT_WINDOW_FRAMES] unacknowledged
 * frames, so a stream only keeps flowing while the receiver answers it - hence the bare ACK every [ACK_AFTER_FRAMES]
 * in-order frames and on every duplicate. An ACK with `last_rx_seq` N > 0 asks for a replay from N + 1.
 *
 * Not thread-safe, and owns no clock: callers serialise access and pass `nowMs`.
 */
@Suppress("TooManyFunctions")
internal class RemoteShellLink(val sessionId: Int, private val inputWindowFrames: Int = INPUT_WINDOW_FRAMES) {

    private class Sent(val frame: RemoteShell, var sentMs: Long)

    var isOpen = false
        private set

    var isClosed = false
        private set

    private var nextTxSeq = 1
    private val txHistory = ArrayDeque<Sent>()
    private var peerAcked = 0

    private var lastRxSeq = 0
    private var nextExpectedRxSeq = 1
    private var highestSeenRxSeq = 0
    private val pendingRx = mutableMapOf<Int, RemoteShell>()
    private var framesSinceOutbound = 0

    private var lastRequestedMissingSeq = 0
    private var lastMissingRequestMs = 0L
    private var missingRequestIntervalMs = RETRY_MIN_MS
    private var missingRequestAttempts = 0

    private var ackLatencyMs: Double? = null
    private var retransmitSeq = 0
    private var retransmits = 0
    private var retransmitIntervalMs = RETRY_MIN_MS
    private var nextRetransmitMs = 0L

    private val pendingInput = Buffer()

    private var lastInboundMs = 0L
    private var lastHeartbeatMs = 0L

    private var openSeq = 0
    private var openDeadlineMs = 0L
    private var nextOpenRetryMs = 0L
    private var openRetryIntervalMs = OPEN_RETRY_FIRST_MS

    private val out = mutableListOf<RemoteShell>()
    private val events = mutableListOf<ShellEvent>()

    // region --- Commands ---

    fun open(cols: Int, rows: Int, nowMs: Long): ShellStep = step {
        lastInboundMs = nowMs
        openDeadlineMs = nowMs + OPEN_TIMEOUT_MS
        nextOpenRetryMs = nowMs + OPEN_RETRY_FIRST_MS
        openSeq =
            sendSequenced(
                shellFrame(RemoteShell.OpCode.OPEN) { wb ->
                    wb.cols = cols
                    wb.rows = rows
                },
                nowMs,
            )
    }

    /** Queue typed bytes and send what the window allows. Everything goes through the queue, so order holds. */
    fun input(bytes: ByteString, nowMs: Long): ShellStep = step {
        if (isClosed) return@step
        val room = (PENDING_INPUT_MAX_BYTES - pendingInput.size).toInt().coerceAtLeast(0)
        val kept = bytes.size.coerceAtMost(room)
        pendingInput.write(bytes, 0, kept)
        if (kept < bytes.size) events += ShellEvent.InputDropped(bytes.size - kept)
        flushInput(nowMs)
    }

    fun resize(cols: Int, rows: Int, nowMs: Long): ShellStep = step {
        if (isOpen && !isClosed) {
            sendSequenced(
                shellFrame(RemoteShell.OpCode.RESIZE) { wb ->
                    wb.cols = cols
                    wb.rows = rows
                },
                nowMs,
            )
        }
    }

    fun close(): ShellStep = step { if (!isClosed) closeLocally("") }

    // endregion

    // region --- Inbound ---

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    fun receive(frame: RemoteShell, nowMs: Long): ShellStep = step {
        if (isClosed || frame.session_id != sessionId) return@step
        lastInboundMs = nowMs
        // Before any ordering: an out-of-order frame still carries a valid cursor, and during a gap it may be the
        // only kind arriving.
        notePeerCursor(maxOf(frame.ack_seq, frame.last_rx_seq), nowMs)

        // Terminal, so not held behind a gap the peer has just said it cannot fill.
        if (frame.op == RemoteShell.OpCode.CLOSED) {
            isClosed = true
            events += ShellEvent.Closed(frame.payload.utf8())
            return@step
        }

        if (frame.op == RemoteShell.OpCode.ACK) {
            if (frame.last_rx_seq > 0) replayFrom(frame.last_rx_seq + 1, nowMs)
            flushInput(nowMs)
            return@step
        }

        when (noteReceivedSeq(frame.seq)) {
            RxAction.DUPLICATE -> {
                // Already in order here, so the peer has not seen our cursor: a sender with its window shut.
                sendAck(requestMissingSeqOnce(nowMs))
                return@step
            }

            RxAction.GAP -> {
                rememberOutOfOrder(frame)
                requestMissingSeqOnce(nowMs)?.let { sendAck(it) }
                return@step
            }

            RxAction.PROCESS -> Unit
        }

        handleInOrder(frame, nowMs)
        drainPendingRx(nowMs)
        if (isClosed) return@step
        flushInput(nowMs)
        val missing = requestMissingSeqOnce(nowMs)
        if (missing != null) {
            sendAck(missing)
        } else if (framesSinceOutbound >= ACK_AFTER_FRAMES) {
            sendAck(null)
        }
    }

    private fun handleInOrder(frame: RemoteShell, nowMs: Long) {
        when (frame.op) {
            RemoteShell.OpCode.OPEN_OK ->
                if (!isOpen) {
                    isOpen = true
                    events += ShellEvent.Opened
                }

            RemoteShell.OpCode.OUTPUT -> if (frame.payload.size > 0) events += ShellEvent.Output(frame.payload)

            RemoteShell.OpCode.ERROR ->
                if (frame.seq == 0) {
                    // Sessionless: the node has no session for us (open_failed, invalid_session).
                    isClosed = true
                    events += ShellEvent.Closed(frame.payload.utf8())
                } else {
                    events += ShellEvent.RemoteError(frame.payload.utf8())
                }

            RemoteShell.OpCode.PONG -> {
                if (frame.last_rx_seq != 0 && frame.last_rx_seq < highestSentSeq()) {
                    replayFrom(frame.last_rx_seq + 1, nowMs)
                }
                if (frame.last_tx_seq > lastRxSeq && frame.last_tx_seq > highestSeenRxSeq) {
                    highestSeenRxSeq = frame.last_tx_seq
                }
            }

            else -> Unit
        }
    }

    private fun drainPendingRx(nowMs: Long) {
        while (!isClosed) {
            val next = pendingRx.remove(nextExpectedRxSeq) ?: return
            if (noteReceivedSeq(next.seq) != RxAction.PROCESS) {
                rememberOutOfOrder(next)
                return
            }
            handleInOrder(next, nowMs)
        }
    }

    // endregion

    // region --- Timers ---

    /** Drive OPEN retries, a shut window, and the heartbeat. Call every few hundred milliseconds. */
    fun tick(nowMs: Long): ShellStep = step {
        if (isClosed) return@step
        if (!isOpen) {
            serviceOpen(nowMs)
            return@step
        }
        flushInput(nowMs)
        if (!windowOpen()) serviceShutWindow(nowMs)
        if (!isClosed && heartbeatDue(nowMs)) {
            lastHeartbeatMs = nowMs
            sendSequenced(
                shellFrame(RemoteShell.OpCode.PING) { wb ->
                    wb.last_tx_seq = highestSentSeq()
                    wb.last_rx_seq = lastRxSeq
                },
                nowMs,
            )
        }
    }

    private fun serviceOpen(nowMs: Long) {
        if (nowMs >= openDeadlineMs) {
            // The node may have opened a session and be streaming into it; CLOSE is acted on out of order.
            closeLocally("no reply from the node")
            return
        }
        if (nowMs < nextOpenRetryMs) return
        // Same session id and seq, which is how the firmware tells a lost OPEN_OK from a new session.
        txHistory.firstOrNull { it.frame.seq == openSeq }?.let { resend(it, nowMs) }
        openRetryIntervalMs = (openRetryIntervalMs * 2).coerceAtMost(OPEN_RETRY_MAX_MS)
        nextOpenRetryMs = nowMs + openRetryIntervalMs
    }

    /**
     * With our window full the peer never sees a seq above the gap, so it never asks for the replay that would reopen
     * us. Repeat the oldest unacknowledged frame instead, and give up after [MAX_RETRANSMITS].
     */
    @Suppress("ReturnCount")
    private fun serviceShutWindow(nowMs: Long) {
        val missing = peerAcked + 1
        if (missing > highestSentSeq() || nowMs < nextRetransmitMs) return
        if (missing != retransmitSeq) {
            retransmitSeq = missing
            retransmits = 0
            retransmitIntervalMs = baseIntervalMs()
            // The wait belongs to the frame, not to the tick that noticed it.
            val sentAt = txHistory.firstOrNull { it.frame.seq == missing }?.sentMs
            if (sentAt != null && nowMs < sentAt + retransmitIntervalMs) {
                nextRetransmitMs = sentAt + retransmitIntervalMs
                return
            }
        }
        if (retransmits >= MAX_RETRANSMITS) {
            closeLocally("the node stopped answering")
            return
        }
        retransmits++
        if (retransmits > RETRY_FLAT_ATTEMPTS) {
            retransmitIntervalMs = (retransmitIntervalMs * 2).coerceAtMost(RETRY_MAX_MS)
        }
        nextRetransmitMs = nowMs + retransmitIntervalMs
        replayFrom(missing, nowMs)
    }

    /** Keyed on inbound silence alone, so typing into a stalled session cannot suppress its own recovery. */
    private fun heartbeatDue(nowMs: Long): Boolean {
        if (nowMs - lastInboundMs < HEARTBEAT_IDLE_DELAY_MS) return false
        return lastHeartbeatMs <= lastInboundMs || nowMs - lastHeartbeatMs >= HEARTBEAT_REPEAT_MS
    }

    // endregion

    // region --- Sending ---

    private fun windowOpen(): Boolean = inputWindowFrames == 0 || highestSentSeq() - peerAcked < inputWindowFrames

    private fun flushInput(nowMs: Long) {
        while (isOpen && !isClosed && pendingInput.size > 0 && windowOpen()) {
            val chunk = pendingInput.readByteString(pendingInput.size.coerceAtMost(MAX_INPUT_CHUNK_BYTES.toLong()))
            sendSequenced(shellFrame(RemoteShell.OpCode.INPUT) { wb -> wb.payload = chunk }, nowMs)
        }
    }

    private fun sendSequenced(template: RemoteShell, nowMs: Long): Int {
        val seq = nextTxSeq++
        val frame =
            template
                .newBuilder()
                .also { wb ->
                    wb.session_id = sessionId
                    wb.seq = seq
                    wb.ack_seq = lastRxSeq
                }
                .build()
        txHistory.addLast(Sent(frame, nowMs))
        if (txHistory.size > TX_HISTORY_LEN) txHistory.removeFirst()
        emit(frame)
        return seq
    }

    /**
     * [replayFrom] null is a bare ACK: `last_rx_seq` 0 is not a replay request, but `ack_seq` still moves the window.
     */
    private fun sendAck(replayFrom: Int?) {
        emit(
            shellFrame(RemoteShell.OpCode.ACK) { wb ->
                wb.session_id = sessionId
                wb.seq = 0
                wb.ack_seq = lastRxSeq
                wb.last_rx_seq = replayFrom?.let { it - 1 } ?: 0
            },
        )
    }

    private fun replayFrom(startSeq: Int, nowMs: Long) {
        val sent = txHistory.firstOrNull { it.frame.seq == startSeq }
        if (sent != null) {
            resend(sent, nowMs)
            return
        }
        val oldest = txHistory.firstOrNull()?.frame?.seq ?: return
        if (startSeq in 1..<oldest) {
            // Aged out of our ring: the peer can never get past this hole and would ask until its idle timeout.
            closeLocally("the node asked for input this phone no longer holds")
        }
    }

    private fun resend(sent: Sent, nowMs: Long) {
        sent.sentMs = nowMs
        emit(sent.frame.newBuilder().also { wb -> wb.ack_seq = lastRxSeq }.build())
    }

    private fun closeLocally(reason: String) {
        emit(
            shellFrame(RemoteShell.OpCode.CLOSE) { wb ->
                wb.session_id = sessionId
                wb.ack_seq = lastRxSeq
            },
        )
        isClosed = true
        events += ShellEvent.Closed(reason)
    }

    /** Anything we send carries our receive cursor, so it settles the flow-control debt whatever its op. */
    private fun emit(frame: RemoteShell) {
        framesSinceOutbound = 0
        out += frame
    }

    private fun highestSentSeq(): Int = nextTxSeq - 1

    // endregion

    // region --- Sequence bookkeeping ---

    private enum class RxAction {
        PROCESS,
        GAP,
        DUPLICATE,
    }

    @Suppress("ReturnCount")
    private fun noteReceivedSeq(seq: Int): RxAction {
        if (seq == 0) return RxAction.PROCESS
        if (seq < nextExpectedRxSeq) {
            return if (highestSeenRxSeq >= nextExpectedRxSeq) RxAction.GAP else RxAction.DUPLICATE
        }
        if (seq > nextExpectedRxSeq) {
            if (seq > highestSeenRxSeq) highestSeenRxSeq = seq
            return RxAction.GAP
        }
        lastRxSeq = seq
        nextExpectedRxSeq = seq + 1
        if (lastRequestedMissingSeq != 0 && nextExpectedRxSeq > lastRequestedMissingSeq) {
            lastRequestedMissingSeq = 0
            missingRequestIntervalMs = baseIntervalMs()
            missingRequestAttempts = 0
        }
        if (seq > highestSeenRxSeq) highestSeenRxSeq = seq
        if (highestSeenRxSeq < nextExpectedRxSeq) highestSeenRxSeq = 0
        // Only an in-order frame moves the cursor the peer is waiting on.
        framesSinceOutbound++
        return RxAction.PROCESS
    }

    /** Lowest seqs are needed soonest, so when full the highest held is the one given up, and only for a lower one. */
    private fun rememberOutOfOrder(frame: RemoteShell) {
        if (frame.seq <= nextExpectedRxSeq || frame.seq in pendingRx) return
        if (pendingRx.size < REORDER_SLOTS) {
            pendingRx[frame.seq] = frame
        } else {
            val highest = pendingRx.keys.max()
            if (frame.seq < highest) {
                pendingRx.remove(highest)
                pendingRx[frame.seq] = frame
            }
        }
        if (frame.seq > highestSeenRxSeq) highestSeenRxSeq = frame.seq
    }

    @Suppress("ReturnCount")
    private fun requestMissingSeqOnce(nowMs: Long): Int? {
        if (highestSeenRxSeq < nextExpectedRxSeq) return null
        val sameSeq = lastRequestedMissingSeq == nextExpectedRxSeq
        if (sameSeq && nowMs - lastMissingRequestMs < missingRequestIntervalMs) return null
        if (sameSeq) {
            missingRequestAttempts++
            if (missingRequestAttempts > RETRY_FLAT_ATTEMPTS) {
                missingRequestIntervalMs = (missingRequestIntervalMs * 2).coerceAtMost(RETRY_MAX_MS)
            }
        } else {
            missingRequestAttempts = 1
            missingRequestIntervalMs = baseIntervalMs()
        }
        lastRequestedMissingSeq = nextExpectedRxSeq
        lastMissingRequestMs = nowMs
        return nextExpectedRxSeq
    }

    /** Monotone and clamped to what we have sent, so a stale or confused cursor can neither close nor overgrant. */
    private fun notePeerCursor(rawCursor: Int, nowMs: Long) {
        val cursor = rawCursor.coerceAtMost(highestSentSeq())
        if (cursor <= peerAcked) return
        txHistory
            .firstOrNull { it.frame.seq == cursor }
            ?.let { sent ->
                val sample = (nowMs - sent.sentMs).toDouble()
                if (sample > 0) {
                    val current = ackLatencyMs
                    ackLatencyMs = if (current == null) sample else current + ACK_LATENCY_SMOOTHING * (sample - current)
                }
            }
        peerAcked = cursor
        nextRetransmitMs = 0L
        retransmitSeq = 0
        retransmits = 0
    }

    private fun baseIntervalMs(): Long {
        val latency = ackLatencyMs ?: return RETRY_MIN_MS
        return (RETRANSMIT_LATENCY_MULTIPLIER * latency).toLong().coerceIn(RETRY_MIN_MS, RETRY_MAX_MS)
    }

    // endregion

    private inline fun step(block: () -> Unit): ShellStep {
        out.clear()
        events.clear()
        block()
        return ShellStep(out.toList(), events.toList())
    }
}
