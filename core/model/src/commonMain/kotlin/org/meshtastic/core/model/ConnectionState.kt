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
package org.meshtastic.core.model

sealed interface ConnectionState {
    /** Not connected; should attempt to reconnect. */
    data object Disconnected : ConnectionState

    /** Transport connecting. */
    data class Connecting(val attempt: Int = 1) : ConnectionState

    /** Transport up, handshake in progress. */
    data class Configuring(val phase: String = "", val progress: Float = 0f) : ConnectionState

    /** Fully connected and operational. */
    data object Connected : ConnectionState

    /** Connection dropped, attempting automatic reconnect. */
    data class Reconnecting(val attempt: Int = 1) : ConnectionState

    /** Device in light sleep. */
    data object DeviceSleep : ConnectionState

    /** Whether the connection is usable for sending messages. */
    val isConnected: Boolean get() = this is Connected
}
