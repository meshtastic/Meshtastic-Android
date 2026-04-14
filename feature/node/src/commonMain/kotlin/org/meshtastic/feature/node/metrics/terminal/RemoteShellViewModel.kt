/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RemoteShellHandler
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RemoteShell
import kotlin.concurrent.Volatile

// ---------------------------------------------------------------------------
// Protocol constants (matched to dmshell_client.py / DMShell.cpp)
// ---------------------------------------------------------------------------

/** Maximum number of output lines held in the ring buffer before the oldest are dropped. */
private const val MAX_OUTPUT_LINES = 500

/** Default PTY column count sent in OPEN/RESIZE frames. */
private const val DEFAULT_COLS = 80

/** Default PTY row count sent in OPEN/RESIZE frames. */
private const val DEFAULT_ROWS = 24

/**
 * Maximum payload bytes per INPUT frame. Matches the firmware constant (meshtastic_Constants_DATA_PAYLOAD_LEN is 237
 * but the POC client caps at 64 per batch for radio efficiency).
 */
private const val MAX_INPUT_CHUNK_BYTES = 64

/**
 * Default debounce window in milliseconds. Matches the Python client's INPUT_BATCH_WINDOW_SEC = 0.5 s. Configurable via
 * [RemoteShellViewModel.setFlushWindowMs].
 */
private const val DEFAULT_FLUSH_WINDOW_MS = 500L

/** Number of sent frames to keep in the retransmission history ring buffer. */
private const val TX_HISTORY_MAX = 50

/** Idle period (ms) after which the first heartbeat PING is sent. */
private const val HEARTBEAT_IDLE_DELAY_MS = 5_000L

/** Interval (ms) between repeated heartbeat PINGs while the session is otherwise idle. */
private const val HEARTBEAT_REPEAT_MS = 15_000L

/** Poll interval (ms) for the heartbeat coroutine. */
private const val HEARTBEAT_POLL_MS = 250L

/** Minimum ms between re-requesting the same missing sequence number. */
private const val MISSING_SEQ_RETRY_MS = 1_000L

/** Size in bytes of an encoded replay-request payload (big-endian uint32). */
private const val REPLAY_REQUEST_BYTES = 4

/** Maximum configurable flush window in milliseconds. */
private const val MAX_FLUSH_WINDOW_MS = 5_000L

/** Size in bytes of a heartbeat-status payload (two big-endian uint32s). */
private const val HEARTBEAT_STATUS_BYTES = 8

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun encodeUint32BE(value: Int): ByteArray = Buffer().apply { writeInt(value) }.readByteArray()

/** Size in bytes of an encoded uint32 (big-endian). */
private const val UINT32_BYTES = 4

private fun decodeUint32BE(bytes: ByteArray, offset: Int = 0): Int =
    Buffer().apply { write(bytes, offset, UINT32_BYTES) }.readInt()

private fun encodeHeartbeatStatus(lastTxSeq: Int, lastRxSeq: Int): ByteArray = Buffer()
    .apply {
        writeInt(lastTxSeq)
        writeInt(lastRxSeq)
    }
    .readByteArray()

private fun decodeHeartbeatStatus(bytes: ByteArray): Pair<Int, Int>? = bytes
    .takeIf { it.size >= HEARTBEAT_STATUS_BYTES }
    ?.let { Buffer().apply { write(it) }.let { buf -> buf.readInt() to buf.readInt() } }

// ---------------------------------------------------------------------------
// Sent-frame record for retransmission history
// ---------------------------------------------------------------------------

private data class SentFrame(
    val op: RemoteShell.OpCode,
    val sessionId: Int,
    val seq: Int,
    val ackSeq: Int,
    val payload: ByteString,
    val cols: Int,
    val rows: Int,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel for the RemoteShell terminal screen.
 *
 * ### Protocol fidelity
 * Implements the same reliability layer as `dmshell_client.py`:
 * - Every non-ACK outbound frame carries an incrementing [nextTxSeq] and a piggybacked [lastRxSeq] in the `ack_seq`
 *   field.
 * - Out-of-order inbound frames are buffered; a gap triggers an `ACK` with a 4-byte `REPLAY_REQUEST` payload asking the
 *   sender to retransmit from the first missing seq.
 * - The last [TX_HISTORY_MAX] sent frames are kept in [txHistory] for retransmission on request.
 * - PING/PONG heartbeats carry an 8-byte status payload `(lastTxSeq, lastRxSeq)`. On PONG, if the peer is behind our tx
 *   cursor we replay; if the peer reports frames we haven't seen we request a replay.
 *
 * ### PKI
 * Outbound [DataPacket]s use [DataPacket.PKC_CHANNEL_INDEX] so that `CommandSenderImpl` applies Curve25519 encryption
 * before handing the frame to the radio. The firmware rejects DMShell packets that are not PKI-encrypted.
 *
 * ### Input model
 * Raw streaming input with a configurable debounce flush window ([DEFAULT_FLUSH_WINDOW_MS] = 500 ms, matching the
 * Python client). Flush is also triggered immediately when the buffer reaches [MAX_INPUT_CHUNK_BYTES] or when the user
 * types a line terminator (`\n`, `\r`) or a tab (`\t`).
 *
 * ### Pending-input visibility
 * Unflushed keystrokes are exposed via [pendingInput] and rendered dim in the UI until the batch is sent
 * (snap-to-confirmed on flush).
 */
@Suppress("TooManyFunctions")
@KoinViewModel
class RemoteShellViewModel(
    @InjectedParam val destNum: Int,
    private val dispatchers: CoroutineDispatchers,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val remoteShellHandler: RemoteShellHandler,
) : ViewModel() {

    // region --- Session state ---

    enum class SessionState {
        IDLE,
        OPENING,
        OPEN,
        CLOSING,
        CLOSED,
        ERROR,
    }

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    companion object {
        private val OPENABLE_STATES = setOf(SessionState.IDLE, SessionState.CLOSED, SessionState.ERROR)
    }

    /** Session ID negotiated during OPEN / OPEN_OK exchange. */
    private val sessionId = MutableStateFlow(0)

    /** Remote PTY PID received in the OPEN_OK payload (0 if not provided). */
    private val _remotePid = MutableStateFlow(0)
    val remotePid: StateFlow<Int> = _remotePid.asStateFlow()

    // endregion

    // region --- Outbound sequence / retransmission ---

    /** Next seq to assign to an outbound non-ACK frame. */
    @Volatile private var nextTxSeq: Int = 1

    /** Ring buffer of the last [TX_HISTORY_MAX] sent frames for replay. */
    private val txHistory = ArrayDeque<SentFrame>()
    private val txMutex = Mutex()

    private suspend fun allocSeq(): Int = txMutex.withLock { nextTxSeq++ }

    private suspend fun rememberSent(frame: SentFrame) {
        if (frame.seq == 0 || frame.op == RemoteShell.OpCode.ACK) return
        txMutex.withLock {
            txHistory.addLast(frame)
            if (txHistory.size > TX_HISTORY_MAX) txHistory.removeFirst()
        }
    }

    private suspend fun pruneSentFrames(ackSeq: Int) {
        if (ackSeq <= 0) return
        txMutex.withLock { txHistory.removeAll { it.seq <= ackSeq } }
    }

    private suspend fun replayFrom(startSeq: Int) {
        val frame = txMutex.withLock { txHistory.firstOrNull { it.seq == startSeq } }
        if (frame == null) {
            Logger.w { "RemoteShell replay unavailable for seq=$startSeq" }
            return
        }
        Logger.d { "RemoteShell replaying seq=${frame.seq} op=${frame.op}" }
        sendFrame(
            RemoteShell(
                op = frame.op,
                session_id = frame.sessionId,
                seq = frame.seq,
                ack_seq = currentAckSeq(),
                payload = frame.payload,
                cols = frame.cols,
                rows = frame.rows,
            ),
            remember = false,
        )
    }

    private suspend fun currentAckSeq(): Int = rxMutex.withLock { lastRxSeq }

    private suspend fun highestSentSeq(): Int = txMutex.withLock { nextTxSeq - 1 }

    // endregion

    // region --- Inbound sequence tracking ---

    private val rxMutex = Mutex()

    /** Highest in-order rx seq we have delivered to the output buffer. */
    @Volatile private var lastRxSeq: Int = 0

    /** Next rx seq we expect to receive in order. */
    @Volatile private var nextExpectedRxSeq: Int = 1

    /** Highest seq we have ever seen from the peer (may be ahead of [nextExpectedRxSeq]). */
    @Volatile private var highestSeenRxSeq: Int = 0

    /** Out-of-order frames buffered while waiting for a gap to fill. */
    private val pendingRxFrames = mutableMapOf<Int, RemoteShell>()

    /** Tracks when we last requested a specific missing seq, to rate-limit retries. */
    @Volatile private var lastRequestedMissingSeq: Int = 0

    @Volatile private var lastMissingRequestTimeMs: Long = 0L

    private enum class RxAction {
        PROCESS,
        GAP,
        DUPLICATE,
    }

    private suspend fun noteReceivedSeq(seq: Int): RxAction = rxMutex.withLock {
        if (seq == 0) return@withLock RxAction.PROCESS
        when {
            seq < nextExpectedRxSeq -> RxAction.DUPLICATE
            seq > nextExpectedRxSeq -> {
                if (seq > highestSeenRxSeq) highestSeenRxSeq = seq
                RxAction.GAP
            }
            else -> {
                lastRxSeq = seq
                nextExpectedRxSeq = seq + 1
                if (seq > highestSeenRxSeq) highestSeenRxSeq = seq
                if (lastRequestedMissingSeq != 0 && nextExpectedRxSeq > lastRequestedMissingSeq) {
                    lastRequestedMissingSeq = 0
                }
                RxAction.PROCESS
            }
        }
    }

    private suspend fun requestMissingSeqOnce(): Int? = rxMutex.withLock {
        if (highestSeenRxSeq < nextExpectedRxSeq) return@withLock null
        val now = nowMillis
        if (
            lastRequestedMissingSeq == nextExpectedRxSeq && (now - lastMissingRequestTimeMs) < MISSING_SEQ_RETRY_MS
        ) {
            return@withLock null
        }
        lastRequestedMissingSeq = nextExpectedRxSeq
        lastMissingRequestTimeMs = now
        nextExpectedRxSeq
    }

    // endregion

    // region --- Output buffer ---

    private val _outputLines = MutableStateFlow<List<String>>(emptyList())
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    // endregion

    // region --- Raw input / pending buffer ---

    private val inputBuffer = StringBuilder()

    private val _pendingInput = MutableStateFlow("")
    val pendingInput: StateFlow<String> = _pendingInput.asStateFlow()

    private var flushJob: Job? = null

    private val _flushWindowMs = MutableStateFlow(DEFAULT_FLUSH_WINDOW_MS)
    val flushWindowMs: StateFlow<Long> = _flushWindowMs.asStateFlow()

    fun setFlushWindowMs(ms: Long) {
        _flushWindowMs.value = ms.coerceIn(0L, MAX_FLUSH_WINDOW_MS)
    }

    // endregion

    // region --- Terminal size ---

    private val _cols = MutableStateFlow(DEFAULT_COLS)
    val cols: StateFlow<Int> = _cols.asStateFlow()

    private val _rows = MutableStateFlow(DEFAULT_ROWS)
    val rows: StateFlow<Int> = _rows.asStateFlow()

    // endregion

    // region --- Phosphor colour pref ---

    private val _phosphor = MutableStateFlow(PhosphorPreset.GREEN)
    val phosphor: StateFlow<PhosphorPreset> = _phosphor.asStateFlow()

    fun setPhosphor(preset: PhosphorPreset) {
        _phosphor.value = preset
    }

    // endregion

    // region --- Heartbeat timing ---

    @Volatile private var lastActivityMs: Long = nowMillis

    @Volatile private var lastHeartbeatSentMs: Long = 0L

    private fun noteActivity(isHeartbeat: Boolean = false) {
        val now = nowMillis
        if (isHeartbeat) lastHeartbeatSentMs = now else lastActivityMs = now
    }

    private fun isHeartbeatDue(): Boolean {
        val now = nowMillis
        if ((now - lastActivityMs) < HEARTBEAT_IDLE_DELAY_MS) return false
        return lastHeartbeatSentMs <= lastActivityMs || (now - lastHeartbeatSentMs) >= HEARTBEAT_REPEAT_MS
    }

    // endregion

    // region --- Node info ---

    val nodeLongName: String
        get() = nodeRepository.nodeDBbyNum.value[destNum]?.user?.long_name ?: destNum.toString()

    // endregion

    // region --- Session control ---

    fun openSession() {
        if (_sessionState.value !in OPENABLE_STATES) return
        _sessionState.update { SessionState.OPENING }
        viewModelScope.launch {
            val newSessionId = commandSender.generatePacketId()
            sessionId.update { newSessionId }
            rxMutex.withLock {
                lastRxSeq = 0
                nextExpectedRxSeq = 1
                highestSeenRxSeq = 0
                pendingRxFrames.clear()
            }
            txMutex.withLock { nextTxSeq = 1 }
            sendFrame(
                RemoteShell(
                    op = RemoteShell.OpCode.OPEN,
                    session_id = newSessionId,
                    seq = allocSeq(),
                    cols = _cols.value,
                    rows = _rows.value,
                ),
            )
            Logger.d { "RemoteShell OPEN → destNum=$destNum sessionId=$newSessionId" }
            startHeartbeatLoop()
        }
    }

    fun closeSession() {
        if (_sessionState.value != SessionState.OPEN) return
        _sessionState.update { SessionState.CLOSING }
        viewModelScope.launch {
            sendFrame(
                RemoteShell(
                    op = RemoteShell.OpCode.CLOSE,
                    session_id = sessionId.value,
                    seq = allocSeq(),
                    ack_seq = currentAckSeq(),
                ),
            )
        }
    }

    fun resize(cols: Int, rows: Int) {
        _cols.value = cols
        _rows.value = rows
        if (_sessionState.value == SessionState.OPEN) {
            viewModelScope.launch {
                sendFrame(
                    RemoteShell(
                        op = RemoteShell.OpCode.RESIZE,
                        session_id = sessionId.value,
                        seq = allocSeq(),
                        ack_seq = currentAckSeq(),
                        cols = cols,
                        rows = rows,
                    ),
                )
            }
        }
    }

    // endregion

    // region --- Raw input handling ---

    /**
     * Appends [char] to the pending buffer and schedules a debounced flush. Flushes immediately on line-terminators and
     * tab to match the Python client's early-flush heuristic, and when the buffer reaches [MAX_INPUT_CHUNK_BYTES].
     */
    fun typeKey(char: Char) {
        inputBuffer.append(char)
        _pendingInput.value = inputBuffer.toString()
        when {
            char == '\n' || char == '\r' || char == '\t' -> flushBuffer()
            inputBuffer.length >= MAX_INPUT_CHUNK_BYTES -> flushBuffer()
            else -> scheduleFlush()
        }
    }

    /** Appends `\r` and flushes immediately (Enter key on mobile). */
    fun typeEnter() {
        inputBuffer.append('\r')
        _pendingInput.value = inputBuffer.toString()
        flushBuffer()
    }

    /** Removes the last byte from the pending buffer; cancels or reschedules the flush job. */
    fun typeBackspace() {
        if (inputBuffer.isEmpty()) return
        inputBuffer.deleteAt(inputBuffer.lastIndex)
        _pendingInput.value = inputBuffer.toString()
        if (inputBuffer.isEmpty()) {
            flushJob?.cancel()
            flushJob = null
        } else {
            scheduleFlush()
        }
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob =
            viewModelScope.launch {
                delay(_flushWindowMs.value)
                flushBuffer()
            }
    }

    private fun flushBuffer() {
        flushJob?.cancel()
        flushJob = null
        if (inputBuffer.isEmpty()) return

        val text = inputBuffer.toString()
        inputBuffer.clear()
        _pendingInput.value = ""

        if (_sessionState.value != SessionState.OPEN) return

        // No local echo — unflushed keystrokes are shown via pendingInput (rendered dim) and the
        // remote PTY will echo them back as OUTPUT frames once the INPUT is delivered.

        val payload = text.encodeToByteArray().toByteString()
        viewModelScope.launch {
            var offset = 0
            while (offset < payload.size) {
                val end = (offset + MAX_INPUT_CHUNK_BYTES).coerceAtMost(payload.size)
                sendFrame(
                    RemoteShell(
                        op = RemoteShell.OpCode.INPUT,
                        session_id = sessionId.value,
                        seq = allocSeq(),
                        ack_seq = currentAckSeq(),
                        payload = payload.substring(offset, end),
                    ),
                )
                offset = end
            }
        }
    }

    // endregion

    // region --- Heartbeat loop ---

    private var heartbeatJob: Job? = null

    @Suppress("LoopWithTooManyJumpStatements")
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob =
            safeLaunch(context = dispatchers.io, tag = "remoteShellHeartbeat") {
                val terminalStates = setOf(SessionState.CLOSED, SessionState.ERROR, SessionState.CLOSING)
                while (_sessionState.value !in terminalStates) {
                    delay(HEARTBEAT_POLL_MS)
                    if (_sessionState.value != SessionState.OPEN) continue
                    if (!isHeartbeatDue()) continue
                    sendFrame(
                        RemoteShell(
                            op = RemoteShell.OpCode.PING,
                            session_id = sessionId.value,
                            seq = allocSeq(),
                            ack_seq = currentAckSeq(),
                            payload = encodeHeartbeatStatus(highestSentSeq(), currentAckSeq()).toByteString(),
                        ),
                        isHeartbeat = true,
                    )
                }
            }
    }

    // endregion

    // region --- Inbound frame processing ---

    init {
        safeLaunch(context = dispatchers.io, tag = "remoteShellFrameCollector") {
            remoteShellHandler.lastFrame.collect { (from, frame) ->
                if (from != destNum) return@collect
                val ourSession = sessionId.value
                if (ourSession != 0 && frame.session_id != ourSession) return@collect
                noteActivity()
                processFrame(frame)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun processFrame(frame: RemoteShell) {
        pruneSentFrames(frame.ack_seq)

        if (frame.op == RemoteShell.OpCode.ACK) {
            val payload = frame.payload.toByteArray()
            if (payload.size >= REPLAY_REQUEST_BYTES) {
                replayFrom(decodeUint32BE(payload))
            }
            return
        }

        when (noteReceivedSeq(frame.seq)) {
            RxAction.DUPLICATE -> {
                requestMissingSeqOnce()?.let { sendAck(replayFrom = it) }
            }
            RxAction.GAP -> {
                rxMutex.withLock { pendingRxFrames[frame.seq] = frame }
                requestMissingSeqOnce()?.let { sendAck(replayFrom = it) }
            }
            RxAction.PROCESS -> {
                handleInOrderFrame(frame)
                drainPendingRxFrames()
                requestMissingSeqOnce()?.let { sendAck(replayFrom = it) }
            }
        }
    }

    @Suppress("LoopWithTooManyJumpStatements")
    private suspend fun drainPendingRxFrames() {
        while (true) {
            val next = rxMutex.withLock { pendingRxFrames.remove(nextExpectedRxSeq) } ?: break
            if (noteReceivedSeq(next.seq) == RxAction.PROCESS) {
                handleInOrderFrame(next)
            } else {
                rxMutex.withLock { pendingRxFrames[next.seq] = next }
                break
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun handleInOrderFrame(frame: RemoteShell) {
        when (frame.op) {
            RemoteShell.OpCode.OPEN_OK -> {
                _sessionState.update { SessionState.OPEN }
                val payload = frame.payload.toByteArray()
                if (payload.size >= UINT32_BYTES) {
                    _remotePid.update { decodeUint32BE(payload) }
                }
                Logger.i { "RemoteShell OPEN_OK session=${frame.session_id} pid=${_remotePid.value}" }
            }
            RemoteShell.OpCode.OUTPUT -> {
                val text =
                    frame.payload.utf8().ifEmpty {
                        return
                    }
                text.lines().forEach { appendOutput(it) }
            }
            RemoteShell.OpCode.ERROR -> {
                appendOutput("[error] ${frame.payload.utf8().ifEmpty { "unknown error" }}")
                _sessionState.update { SessionState.ERROR }
            }
            RemoteShell.OpCode.CLOSED -> {
                val msg = frame.payload.utf8()
                appendOutput(if (msg.isNotEmpty()) "[session closed: $msg]" else "[session closed]")
                _sessionState.update { SessionState.CLOSED }
            }
            RemoteShell.OpCode.PONG -> {
                val payload = frame.payload.toByteArray()
                if (payload.isEmpty()) return
                val (peerLastTxSeq, peerLastRxSeq) = decodeHeartbeatStatus(payload) ?: return
                val ourHighestTx = highestSentSeq()
                if (peerLastRxSeq < ourHighestTx) {
                    replayFrom(peerLastRxSeq + 1)
                }
                if (peerLastTxSeq > currentAckSeq()) {
                    rxMutex.withLock { if (peerLastTxSeq > highestSeenRxSeq) highestSeenRxSeq = peerLastTxSeq }
                    requestMissingSeqOnce()?.let { sendAck(replayFrom = it) }
                }
            }
            else -> Logger.d { "RemoteShell unhandled in-order op=${frame.op}" }
        }
    }

    // endregion

    // region --- Frame dispatch ---

    private suspend fun sendAck(replayFrom: Int? = null) {
        val payload = replayFrom?.let { encodeUint32BE(it).toByteString() } ?: ByteString.EMPTY
        sendFrame(
            RemoteShell(
                op = RemoteShell.OpCode.ACK,
                session_id = sessionId.value,
                seq = 0,
                ack_seq = currentAckSeq(),
                payload = payload,
            ),
            remember = false,
        )
    }

    private suspend fun sendFrame(frame: RemoteShell, remember: Boolean = true, isHeartbeat: Boolean = false) {
        if (remember) {
            rememberSent(
                SentFrame(
                    op = frame.op,
                    sessionId = frame.session_id,
                    seq = frame.seq,
                    ackSeq = frame.ack_seq,
                    payload = frame.payload,
                    cols = frame.cols,
                    rows = frame.rows,
                ),
            )
        }
        noteActivity(isHeartbeat)
        safeLaunch(context = dispatchers.io, tag = "remoteShellSend") {
            val myNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: 0
            val packet =
                DataPacket(
                    to = DataPacket.nodeNumToDefaultId(destNum),
                    from = DataPacket.nodeNumToDefaultId(myNum),
                    bytes = RemoteShell.ADAPTER.encode(frame).toByteString(),
                    dataType = PortNum.REMOTE_SHELL_APP.value,
                    // PKC_CHANNEL_INDEX (8) triggers Curve25519 encryption in CommandSenderImpl.
                    // The firmware rejects DMShell packets that are not PKI-encrypted.
                    channel = DataPacket.PKC_CHANNEL_INDEX,
                )
            commandSender.sendData(packet)
        }
    }

    // endregion

    // region --- Helpers ---

    private fun appendOutput(line: String) {
        _outputLines.update { current -> (current + line).takeLast(MAX_OUTPUT_LINES) }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        heartbeatJob?.cancel()
        if (_sessionState.value == SessionState.OPEN) closeSession()
        Logger.d { "RemoteShellViewModel cleared for destNum=$destNum" }
    }
}
