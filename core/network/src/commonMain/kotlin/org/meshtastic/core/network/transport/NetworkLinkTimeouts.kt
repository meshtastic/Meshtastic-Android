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
package org.meshtastic.core.network.transport

/** Timeouts the TCP and WebSocket phone links share. */
object NetworkLinkTimeouts {
    /** A failed connect just feeds the reconnect loop, so it is not fatal. */
    const val CONNECT_TIMEOUT_MS: Long = 30_000L

    /** How long a network radio link may go with nothing inbound before it is treated as dead. */
    const val INACTIVITY_TIMEOUT_MS: Long = 90_000L
}
