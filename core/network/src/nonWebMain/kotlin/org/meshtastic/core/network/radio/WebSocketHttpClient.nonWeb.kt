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
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.websocket.WebSockets

// CIO, not the platform default: Android's OkHttp applies network_security_config's cleartext ban to a LAN ws://
// radio, and CIO is what the app's MQTT WebSocket transport already uses.
internal actual fun webSocketHttpClient(maxFrameSize: Long, connectTimeoutMillis: Long): HttpClient = HttpClient(CIO) {
    install(WebSockets) { this.maxFrameSize = maxFrameSize }
    engine { endpoint { connectTimeout = connectTimeoutMillis } }
}
