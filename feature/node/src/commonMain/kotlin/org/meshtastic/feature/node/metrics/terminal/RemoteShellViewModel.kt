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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RemoteShellHandler
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RemoteShell

private const val MAX_OUTPUT_LINES = 500
private const val DEFAULT_COLS = 80
private const val DEFAULT_ROWS = 24

/** Keystroke debounce, matching the python client's `INPUT_BATCH_WINDOW_SEC`. */
private const val FLUSH_WINDOW_MS = 500L

/** How often [RemoteShellLink.tick] runs: OPEN retries, a shut window, the heartbeat. */
private const val TICK_MS = 250L

/**
 * Terminal session against the firmware DMShell module.
 *
 * The protocol lives in [RemoteShellLink]; this class owns the clock, the radio and the UI state. Frames go out
 * PKI-encrypted ([NodeAddress.PKC_CHANNEL_INDEX]) without a routing ACK, since the shell's own sequence numbers carry
 * reliability and the firmware drops anything not PKI-encrypted.
 */
@Suppress("TooManyFunctions")
@KoinViewModel
class RemoteShellViewModel(
    @InjectedParam val destNum: Int,
    private val dispatchers: CoroutineDispatchers,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val remoteShellHandler: RemoteShellHandler,
    private val applicationScope: ApplicationCoroutineScope,
) : ViewModel() {

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

    private val _outputLines = MutableStateFlow<List<String>>(emptyList())
    val outputLines: StateFlow<List<String>> = _outputLines.asStateFlow()

    /** Typed but not yet handed to the link; drawn dim until the batch goes out. */
    private val _pendingInput = MutableStateFlow("")
    val pendingInput: StateFlow<String> = _pendingInput.asStateFlow()

    val nodeLongName: String
        get() = nodeRepository.nodeDBbyNum.value[destNum]?.user?.long_name ?: destNum.toString()

    private val linkMutex = Mutex()
    private var link: RemoteShellLink? = null
    private var tickJob: Job? = null

    private val output = TerminalOutput(MAX_OUTPUT_LINES)

    private val inputBuffer = StringBuilder()
    private var flushJob: Job? = null

    private var cols = DEFAULT_COLS
    private var rows = DEFAULT_ROWS

    init {
        safeLaunch(context = dispatchers.io, tag = "remoteShellFrameCollector") {
            remoteShellHandler.lastFrame.collect { (from, frame) ->
                if (from == destNum) withLink { it.receive(frame, nowMillis) }
            }
        }
    }

    // region --- Session control ---

    fun openSession() {
        if (_sessionState.value !in OPENABLE_STATES) return
        _sessionState.value = SessionState.OPENING
        safeLaunch(context = dispatchers.io, tag = "remoteShellOpen") {
            val fresh = RemoteShellLink(sessionId = commandSender.generatePacketId())
            linkMutex.withLock { link = fresh }
            withLink { it.open(cols, rows, nowMillis) }
            startTicking()
        }
    }

    fun closeSession() {
        if (_sessionState.value != SessionState.OPEN) return
        _sessionState.value = SessionState.CLOSING
        safeLaunch(context = dispatchers.io, tag = "remoteShellClose") { withLink { it.close() } }
    }

    fun resize(cols: Int, rows: Int) {
        this.cols = cols
        this.rows = rows
        safeLaunch(context = dispatchers.io, tag = "remoteShellResize") {
            withLink { it.resize(cols, rows, nowMillis) }
        }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob =
            safeLaunch(context = dispatchers.io, tag = "remoteShellTick") {
                while (true) {
                    delay(TICK_MS)
                    if (withLink { it.tick(nowMillis) }) break
                }
            }
    }

    // endregion

    // region --- Input ---

    /** Flushes at once on a line terminator, a tab, or a full chunk; otherwise after [FLUSH_WINDOW_MS]. */
    fun typeKey(char: Char) {
        inputBuffer.append(char)
        _pendingInput.value = inputBuffer.toString()
        when {
            char == '\n' || char == '\r' || char == '\t' -> flushBuffer()
            inputBuffer.length >= MAX_INPUT_CHUNK_BYTES -> flushBuffer()
            else -> scheduleFlush()
        }
    }

    /** Ctrl-C is worthless if it waits behind the debounce, so control sequences skip it. */
    fun typeControlSequence(text: String) {
        inputBuffer.append(text)
        flushBuffer()
    }

    fun typeEnter() {
        inputBuffer.append('\r')
        flushBuffer()
    }

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
        flushJob = viewModelScope.launch {
            delay(FLUSH_WINDOW_MS)
            flushBuffer()
        }
    }

    private fun flushBuffer() {
        flushJob?.cancel()
        flushJob = null
        val text = inputBuffer.toString()
        inputBuffer.clear()
        _pendingInput.value = ""
        if (text.isEmpty() || _sessionState.value != SessionState.OPEN) return
        // No local echo: the PTY echoes what it receives.
        safeLaunch(context = dispatchers.io, tag = "remoteShellInput") {
            withLink { it.input(text.encodeUtf8(), nowMillis) }
        }
    }

    // endregion

    // region --- Link plumbing ---

    /** Runs [block] against the live link, sends what it produced and applies its events. Returns true once closed. */
    private suspend fun withLink(block: (RemoteShellLink) -> ShellStep): Boolean {
        val (step, closed) =
            linkMutex.withLock {
                val current = link ?: return true
                block(current) to current.isClosed
            }
        step.send.forEach(::transmit)
        step.events.forEach(::apply)
        return closed
    }

    private fun apply(event: ShellEvent) {
        when (event) {
            ShellEvent.Opened -> {
                _sessionState.value = SessionState.OPEN
                Logger.i { "RemoteShell opened with $destNum" }
            }

            is ShellEvent.Output -> publish { output.append(event.bytes) }

            is ShellEvent.RemoteError -> publish { output.notice("[error] ${event.message.ifEmpty { "unknown" }}") }

            is ShellEvent.InputDropped -> publish { output.notice("[input dropped: ${event.bytes} bytes]") }

            is ShellEvent.Closed -> {
                val wasOpening = _sessionState.value == SessionState.OPENING
                publish { output.notice(closedNotice(event.reason, wasOpening)) }
                _sessionState.value = if (wasOpening) SessionState.ERROR else SessionState.CLOSED
            }
        }
    }

    private fun closedNotice(reason: String, wasOpening: Boolean): String = when {
        // A node that has not authorized us drops OPEN without replying, and so does one out of range.
        wasOpening && reason.isEmpty() -> "[no reply from the node]"

        wasOpening -> "[$reason - the node must list this phone's public key as an admin key, and be in range]"

        reason.isEmpty() -> "[session closed]"

        else -> "[session closed: $reason]"
    }

    private inline fun publish(block: () -> Unit) {
        block()
        _outputLines.value = output.lines()
    }

    private fun transmit(frame: RemoteShell) {
        val myNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: 0
        val packet =
            DataPacket(
                to = NodeAddress.numToDefaultId(destNum),
                from = NodeAddress.numToDefaultId(myNum),
                bytes = RemoteShell.ADAPTER.encode(frame).toByteString(),
                dataType = PortNum.REMOTE_SHELL_APP.value,
                channel = NodeAddress.PKC_CHANNEL_INDEX,
                wantAck = false,
            )
        // Not viewModelScope: the CLOSE sent from onCleared would otherwise never leave.
        applicationScope.launch(dispatchers.io) {
            safeCatching { commandSender.sendData(packet) }
                .onFailure { Logger.w(it) { "RemoteShell send failed op=${frame.op} seq=${frame.seq}" } }
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        tickJob?.cancel()
        // viewModelScope is already cancelled here, so close the link synchronously and send what it produced.
        link?.takeIf { !it.isClosed }?.close()?.send?.forEach(::transmit)
        Logger.d { "RemoteShellViewModel cleared for destNum=$destNum" }
    }

    private companion object {
        val OPENABLE_STATES = setOf(SessionState.IDLE, SessionState.CLOSED, SessionState.ERROR)
    }
}
