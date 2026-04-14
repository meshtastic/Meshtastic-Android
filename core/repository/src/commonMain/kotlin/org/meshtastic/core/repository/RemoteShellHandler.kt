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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.SharedFlow
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.RemoteShell

/**
 * A decoded [RemoteShell] frame together with the node number that sent it.
 *
 * Propagating [from] allows downstream consumers (e.g. the ViewModel) to verify that a frame
 * actually originated from the expected peer rather than relying solely on [RemoteShell.session_id].
 */
data class ReceivedShellFrame(
    val from: Int,
    val frame: RemoteShell,
)

/**
 * Interface for handling RemoteShell packets (REMOTE_SHELL_APP portnum = 13).
 *
 * RemoteShell is a PTY-over-mesh feature that relays a shell session across the mesh network. The firmware-side
 * implementation is currently unreleased (gated to [Capabilities.supportsRemoteShell]).
 */
interface RemoteShellHandler {
    /**
     * The most recently received [ReceivedShellFrame], emitted to collectors.
     *
     * Uses [SharedFlow] (not `StateFlow`) so that rapid or identical frames are never silently dropped.
     */
    val lastFrame: SharedFlow<ReceivedShellFrame>

    /**
     * Processes an incoming RemoteShell packet.
     *
     * @param packet The received mesh packet carrying a [RemoteShell] payload.
     */
    fun handleRemoteShell(packet: MeshPacket)
}
