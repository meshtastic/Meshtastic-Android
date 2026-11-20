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
package org.meshtastic.core.network.radio

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets

// The browser's WebSocket. Ktor's Js session throws WebSocketException when the plugin sets a frame cap, right after
// the socket opens, and the browser has no connect timeout to set; the transport refuses an oversized ToRadio itself.
@Suppress("UnusedParameter")
internal actual fun webSocketHttpClient(maxFrameSize: Long, connectTimeoutMillis: Long): HttpClient = HttpClient {
    install(WebSockets)
}
