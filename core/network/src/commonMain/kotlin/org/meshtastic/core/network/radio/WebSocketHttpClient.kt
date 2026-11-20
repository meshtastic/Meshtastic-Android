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

/**
 * The WebSocket client for one phone link, on the engine each platform can use. It installs no HTTP logging: Ktor logs
 * the whole request URL, query included, so the transport logs each attempt under its anonymized label instead.
 */
internal expect fun webSocketHttpClient(maxFrameSize: Long, connectTimeoutMillis: Long): HttpClient
