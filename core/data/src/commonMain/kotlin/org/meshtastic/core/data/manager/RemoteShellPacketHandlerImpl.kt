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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.util.decodeOrNull
import org.meshtastic.core.repository.ReceivedShellFrame
import org.meshtastic.core.repository.RemoteShellHandler
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.RemoteShell

/**
 * Handles incoming [RemoteShell] packets (REMOTE_SHELL_APP portnum = 13).
 *
 * This is a scaffold implementation. The RemoteShell firmware feature is currently unreleased (gated to
 * [org.meshtastic.core.model.Capabilities.supportsRemoteShell]). When the firmware ships, this handler should be
 * expanded to manage PTY session state and relay I/O to the UI.
 */
@Single
class RemoteShellPacketHandlerImpl : RemoteShellHandler {

    /**
     * Emits every received [ReceivedShellFrame] (decoded frame + sender node number).
     *
     * Uses [MutableSharedFlow] with a buffer so that rapid or structurally-identical frames are never silently dropped
     * (unlike `StateFlow` which conflates by equality).
     */
    private val _lastFrame = MutableSharedFlow<ReceivedShellFrame>(extraBufferCapacity = 16)
    override val lastFrame: SharedFlow<ReceivedShellFrame> = _lastFrame.asSharedFlow()

    override fun handleRemoteShell(packet: MeshPacket) {
        val payload = packet.decoded?.payload ?: return
        val frame = RemoteShell.ADAPTER.decodeOrNull(payload, Logger) ?: return
        Logger.d { "RemoteShell frame from ${packet.from}: op=${frame.op} sessionId=${frame.session_id}" }
        _lastFrame.tryEmit(ReceivedShellFrame(from = packet.from, frame = frame))
    }
}
