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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.getString
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
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.remote_shell_error_notice
import org.meshtastic.core.resources.remote_shell_full_screen
import org.meshtastic.core.resources.remote_shell_input_dropped
import org.meshtastic.core.resources.remote_shell_no_reply
import org.meshtastic.core.resources.remote_shell_no_reply_reason
import org.meshtastic.core.resources.remote_shell_session_closed
import org.meshtastic.core.resources.remote_shell_session_closed_reason
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.RemoteShell

private const val MAX_OUTPUT_LINES = 1_000
private const val DEFAULT_COLS = 80
private const val DEFAULT_ROWS = 24

/** Keystroke debounce in character mode, matching the python client's `INPUT_BATCH_WINDOW_SEC`. */
private const val FLUSH_WINDOW_MS = 500L

/** How often [RemoteShellLink.tick] runs: OPEN retries, a shut window, the heartbeat, prediction expiry. */
private const val TICK_MS = 250L

private const val MAX_HISTORY = 50
private const val DEL = "\u007f"

internal const val FONT_SIZE_DEFAULT_SP = 13
internal const val FONT_SIZE_MIN_SP = 9
internal const val FONT_SIZE_MAX_SP = 22

/** Commands worth one tap on a meshtasticd host; inserted into the composer, never sent unseen. */
internal val QUICK_COMMANDS =
    listOf(
        "uptime",
        "df -h",
        "free -h",
        "ip -br addr",
        "systemctl status meshtasticd --no-pager",
        "journalctl -u meshtasticd -n 30 --no-pager",
    )

/**
 * Terminal session against the firmware DMShell module.
 *
 * The protocol lives in [RemoteShellLink], the screen model in [TerminalOutput] and predictive echo in [LocalEcho]; all
 * three are mutated only under [linkMutex], so a frame, a tick and a keystroke never interleave. This class owns the
 * clock, the radio and the UI state.
 *
 * Two input modes. Character mode streams keystrokes (debounced into one frame per burst) so tab completion, line
 * editing and prompts behave as on any terminal, and draws what was sent with [LocalEcho] until the node echoes it.
 * Line mode composes the whole command locally and sends it in one frame, the cheapest way to spend airtime; it keeps a
 * local history so recalling a command costs no round trip.
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

    enum class InputMode {
        CHARACTER,
        LINE,
    }

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val screenState = MutableStateFlow(TerminalScreenState())
    internal val screen: StateFlow<TerminalScreenState> = screenState.asStateFlow()

    private val _health = MutableStateFlow(LinkHealth())
    val health: StateFlow<LinkHealth> = _health.asStateFlow()

    private val _modifiers = MutableStateFlow(Modifiers())
    val modifiers: StateFlow<Modifiers> = _modifiers.asStateFlow()

    private val _inputMode = MutableStateFlow(InputMode.CHARACTER)
    val inputMode: StateFlow<InputMode> = _inputMode.asStateFlow()

    private val _composer = MutableStateFlow("")
    val composer: StateFlow<String> = _composer.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()
    private var historyCursor = -1

    private val _fontSizeSp = MutableStateFlow(FONT_SIZE_DEFAULT_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _bell = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val bell: SharedFlow<Unit> = _bell.asSharedFlow()

    val nodeLongName: String
        get() = nodeRepository.nodeDBbyNum.value[destNum]?.user?.long_name ?: destNum.toString()

    private val linkMutex = Mutex()
    private var link: RemoteShellLink? = null
    private val output = TerminalOutput(MAX_OUTPUT_LINES)
    private val echo = LocalEcho()
    private var tickJob: Job? = null

    /** Character mode: typed but not yet handed to the link, still editable locally. */
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
                    val closed = withLink { link ->
                        val now = nowMillis
                        echo.tick(now, link.health.roundTripMs)
                        link.tick(now)
                    }
                    if (closed) break
                }
            }
    }

    // endregion

    // region --- Character-mode input ---

    /** A typed character. With a sticky modifier armed it is sent at once as the modified byte(s). */
    fun typeKey(char: Char) {
        val mods = _modifiers.value
        if (!mods.isEmpty) {
            _modifiers.value = mods.consumed()
            sendNow(char.withModifiers(mods))
            return
        }
        inputBuffer.append(char)
        publishPending()
        when {
            char == '\t' -> flushBuffer()
            inputBuffer.length >= MAX_INPUT_CHUNK_BYTES -> flushBuffer()
            else -> scheduleFlush()
        }
    }

    fun typeEnter() {
        inputBuffer.append('\r')
        flushBuffer()
    }

    /** Edits the unsent buffer while there is one; past it, the remote line, by sending DEL. */
    fun typeBackspace() {
        if (inputBuffer.isEmpty()) {
            sendNow(DEL)
            return
        }
        inputBuffer.deleteAt(inputBuffer.lastIndex)
        publishPending()
        if (inputBuffer.isEmpty()) {
            flushJob?.cancel()
            flushJob = null
        } else {
            scheduleFlush()
        }
    }

    /** An extra-keys or hardware key. Pending typing goes first so the bytes reach the PTY in the order pressed. */
    fun sendKey(key: TerminalKey) {
        val mods = _modifiers.value
        _modifiers.value = mods.consumed()
        val sequence = key.sequence(screenState.value.applicationCursorKeys)
        sendNow(if (mods.alt != ModifierState.OFF) "\u001b$sequence" else sequence)
    }

    /** A hardware Ctrl/Alt chord. Any armed sticky modifier applies on top, as it would to a typed key. */
    fun typeChord(char: Char, ctrl: Boolean, alt: Boolean) {
        val sticky = _modifiers.value
        _modifiers.value = sticky.consumed()
        val chord =
            Modifiers(
                ctrl = if (ctrl || sticky.ctrl != ModifierState.OFF) ModifierState.ONCE else ModifierState.OFF,
                alt = if (alt || sticky.alt != ModifierState.OFF) ModifierState.ONCE else ModifierState.OFF,
            )
        sendNow(char.withModifiers(chord))
    }

    fun toggleCtrl() = _modifiers.update { it.copy(ctrl = it.ctrl.next()) }

    fun toggleAlt() = _modifiers.update { it.copy(alt = it.alt.next()) }

    /** Clipboard text: typed in character mode, appended to the composer in line mode. */
    fun paste(text: String) {
        if (text.isEmpty()) return
        if (_inputMode.value == InputMode.LINE) {
            _composer.update { it + text }
        } else {
            sendNow(text.replace("\r\n", "\r").replace('\n', '\r'))
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
        publishPending()
        if (text.isNotEmpty()) send(text)
    }

    private fun sendNow(text: String) {
        val pending = inputBuffer.toString()
        inputBuffer.clear()
        flushJob?.cancel()
        flushJob = null
        publishPending()
        send(pending + text)
    }

    private fun send(text: String) {
        if (_sessionState.value != SessionState.OPEN) return
        safeLaunch(context = dispatchers.io, tag = "remoteShellInput") {
            withLink { link ->
                val now = nowMillis
                echo.onSent(text, now)
                link.input(text.encodeUtf8(), now)
            }
        }
    }

    // endregion

    // region --- Line mode ---

    fun setInputMode(mode: InputMode) {
        if (mode == InputMode.LINE) flushBuffer()
        _inputMode.value = mode
    }

    fun setComposer(text: String) {
        _composer.value = text
        historyCursor = -1
    }

    fun insertCommand(command: String) = setComposer(command)

    /** Sends the composed line in one frame; an empty line still sends Enter, which a prompt may be waiting for. */
    fun submitComposer() {
        val line = _composer.value
        _composer.value = ""
        historyCursor = -1
        if (line.isNotBlank()) {
            _history.update { (listOf(line) + it.filterNot { old -> old == line }).take(MAX_HISTORY) }
        }
        sendNow(line + "\r")
    }

    /** Steps the composer through local history: [older] true walks back, false walks forward to an empty line. */
    fun recallHistory(older: Boolean) {
        val entries = _history.value
        if (entries.isEmpty()) return
        historyCursor = (if (older) historyCursor + 1 else historyCursor - 1).coerceIn(-1, entries.lastIndex)
        _composer.value = if (historyCursor < 0) "" else entries[historyCursor]
    }

    // endregion

    fun adjustFontSize(deltaSp: Int) = _fontSizeSp.update {
        (it + deltaSp).coerceIn(FONT_SIZE_MIN_SP, FONT_SIZE_MAX_SP)
    }

    // region --- Link plumbing ---

    /**
     * Runs [block] against the live link and applies what it produced, all under [linkMutex], then sends its frames.
     * Returns true once the link is closed or gone.
     */
    private suspend fun withLink(block: (RemoteShellLink) -> ShellStep): Boolean {
        val (step, closed) =
            linkMutex.withLock {
                val current = link ?: return true
                val step = block(current)
                step.events.forEach { applyEvent(it) }
                _health.value = current.health
                publishScreen()
                step to current.isClosed
            }
        step.send.forEach(::transmit)
        return closed
    }

    /** Caller holds [linkMutex]. */
    private suspend fun applyEvent(event: ShellEvent) {
        when (event) {
            ShellEvent.Opened -> {
                _sessionState.value = SessionState.OPEN
                Logger.i { "RemoteShell opened with $destNum" }
            }

            is ShellEvent.Output -> {
                val result = output.append(event.bytes)
                echo.onOutput(result.echo, nowMillis)
                if (result.bell) _bell.tryEmit(Unit)
                if (result.enteredFullScreen) output.notice(getString(Res.string.remote_shell_full_screen))
            }

            is ShellEvent.RemoteError ->
                output.notice(getString(Res.string.remote_shell_error_notice, event.message.ifEmpty { "?" }))

            is ShellEvent.InputDropped -> output.notice(getString(Res.string.remote_shell_input_dropped, event.bytes))

            is ShellEvent.Closed -> {
                val wasOpening = _sessionState.value == SessionState.OPENING
                output.notice(closedNotice(event.reason, wasOpening))
                _sessionState.value = if (wasOpening) SessionState.ERROR else SessionState.CLOSED
            }
        }
    }

    // A node that has not authorized us drops OPEN without replying, and so does one out of range.
    private suspend fun closedNotice(reason: String, wasOpening: Boolean): String = when {
        wasOpening && reason.isEmpty() -> getString(Res.string.remote_shell_no_reply)
        wasOpening -> getString(Res.string.remote_shell_no_reply_reason, reason)
        reason.isEmpty() -> getString(Res.string.remote_shell_session_closed)
        else -> getString(Res.string.remote_shell_session_closed_reason, reason)
    }

    /** Caller holds [linkMutex]. */
    private fun publishScreen() {
        screenState.update {
            it.copy(
                lines = output.lines(),
                cursorColumn = output.cursorColumn,
                applicationCursorKeys = output.applicationCursorKeys,
                predicted = echo.pending,
            )
        }
    }

    private fun publishPending() = screenState.update { it.copy(unsent = inputBuffer.toString()) }

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

/** Everything the terminal pane draws. [predicted] was sent and awaits echo; [unsent] is still in the debounce. */
internal data class TerminalScreenState(
    val lines: List<TerminalLine> = listOf(TerminalLine.Empty),
    val cursorColumn: Int = 0,
    val applicationCursorKeys: Boolean = false,
    val predicted: String = "",
    val unsent: String = "",
)
