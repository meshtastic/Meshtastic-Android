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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tracks a window in which the locally connected node is expected to restart (a config save the firmware applies with a
 * reboot, or an explicit reboot command). While the window is open, an observed transport disconnect is the expected
 * consequence of the restart — the UI presents "restarting" instead of the alarming disconnected state, and the
 * foreground-service notification stays on a connecting presentation.
 *
 * The window closes when the node completes its post-reboot config handshake ([onConnected]) or after [DEFAULT_WINDOW]
 * if the node never comes back — after that, a persisting disconnect is a real problem and is shown as one.
 */
class NodeRestartTracker(private val scope: CoroutineScope) {

    private val _restartExpected = MutableStateFlow(false)

    /** True while a node restart (and its transport disconnect) is expected. */
    val restartExpected: StateFlow<Boolean> = _restartExpected.asStateFlow()

    private var expiryJob: Job? = null

    /** Opens (or extends) the expected-restart window. Call at the moment the restart-causing action is sent. */
    fun expectRestart(window: Duration = DEFAULT_WINDOW) {
        expiryJob?.cancel()
        _restartExpected.value = true
        expiryJob =
            scope.launch {
                delay(window)
                _restartExpected.value = false
            }
    }

    /** Closes the window; call when the node is fully back online (config handshake complete). */
    fun onConnected() {
        expiryJob?.cancel()
        expiryJob = null
        _restartExpected.value = false
    }

    companion object {
        /** Covers reboot delay (~5s), boot (~10-25s), reconnect and config sync (~10-30s) with headroom. */
        val DEFAULT_WINDOW = 90.seconds
    }
}
