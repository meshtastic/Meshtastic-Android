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
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import org.meshtastic.core.network.KermitHttpLogger

/** The WebSocket client for one phone link, on the engine each platform can use. */
internal expect fun webSocketHttpClient(maxFrameSize: Long, connectTimeoutMillis: Long): HttpClient

/** The upgrade request line and response status, without headers, so a session cookie never reaches the log. */
internal fun HttpClientConfig<*>.logPhoneLinkHandshakes() {
    install(Logging) {
        logger = KermitHttpLogger
        level = LogLevel.INFO
    }
}
