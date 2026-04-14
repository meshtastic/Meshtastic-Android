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
package org.meshtastic.core.network

/**
 * Shared HTTP client configuration constants used by both Android and Desktop Ktor `HttpClient` setups.
 *
 * These values are consumed by the platform-specific Koin modules (`NetworkModule` on Android, `DesktopKoinModule` on
 * Desktop) when installing [io.ktor.client.plugins.HttpTimeout] and [io.ktor.client.plugins.HttpRequestRetry].
 */
object HttpClientDefaults {
    /** Timeout in milliseconds for connect, request, and socket operations. */
    const val TIMEOUT_MS = 30_000L

    /** Maximum number of automatic retries on server errors (5xx). */
    const val MAX_RETRIES = 3
}
