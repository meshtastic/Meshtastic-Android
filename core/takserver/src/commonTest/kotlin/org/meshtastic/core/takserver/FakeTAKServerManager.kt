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
package org.meshtastic.core.takserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared [TAKServerManager] fake for this module's commonTest. Not in `:core:testing` — that module's
 * `build.gradle.kts` documents an explicit boundary ("Heavy modules ... should depend on core:testing, not vice versa")
 * that a `:core:takserver`-specific fake would violate.
 *
 * `isRunning` defaults to `false` and toggles via [start]/[stop] like the real [TAKServerManagerImpl]; callers that
 * only exercise the connected-client / broadcast surface (not the start/stop lifecycle) can ignore it.
 */
internal class FakeTAKServerManager : TAKServerManager {
    override val isSupported = true

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isStarting = MutableStateFlow(false)
    override val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val _hasStartError = MutableStateFlow(false)
    override val hasStartError: StateFlow<Boolean> = _hasStartError.asStateFlow()

    val connections = MutableStateFlow(0)
    override val connectionCount: StateFlow<Int> = connections

    private val _inboundMessages = MutableSharedFlow<InboundCoTMessage>(extraBufferCapacity = 64)
    override val inboundMessages: SharedFlow<InboundCoTMessage> = _inboundMessages.asSharedFlow()

    private val _clientConnected = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val clientConnected: SharedFlow<Unit> = _clientConnected.asSharedFlow()

    val broadcasts = mutableListOf<CoTMessage>()
    val rawBroadcasts = mutableListOf<String>()
    var startCount = 0
    var stopped = false

    override fun start(scope: CoroutineScope) {
        startCount++
        _isRunning.value = true
    }

    override fun stop() {
        stopped = true
        _isRunning.value = false
    }

    override fun broadcast(cotMessage: CoTMessage) {
        broadcasts.add(cotMessage)
    }

    override fun broadcastRawXml(xml: String) {
        rawBroadcasts.add(xml)
    }

    suspend fun emitInbound(cotMessage: CoTMessage, clientInfo: TAKClientInfo? = null) {
        _inboundMessages.emit(InboundCoTMessage(cotMessage, clientInfo))
    }

    suspend fun emitClientConnected() = _clientConnected.emit(Unit)
}
