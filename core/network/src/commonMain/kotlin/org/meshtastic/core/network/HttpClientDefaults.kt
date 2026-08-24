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

import io.ktor.client.plugins.HttpRequestRetryConfig

/**
 * Shared HTTP client configuration used by both Android and Desktop Ktor `HttpClient` setups.
 *
 * These values are consumed by the platform-specific Koin modules (`NetworkModule` on Android, `DesktopKoinModule` on
 * Desktop) when installing [io.ktor.client.plugins.HttpTimeout] and [io.ktor.client.plugins.HttpRequestRetry].
 */
object HttpClientDefaults {
    /** Timeout in milliseconds for connect and socket operations. */
    const val TIMEOUT_MS = 30_000L

    /**
     * Timeout in milliseconds for a whole request. Deliberately generous: api.meshtastic.org has been measured taking
     * 20-60s to serve `github/firmware/list` and `resource/deviceHardware`, and callers use stale-while-revalidate
     * caching so nothing user-facing waits on this deadline.
     */
    const val REQUEST_TIMEOUT_MS = 90_000L

    /** Maximum number of automatic retries on server errors (5xx) and transient connection/IO failures. */
    const val MAX_RETRIES = 3

    /** Base URL for the Meshtastic public API. Installed via the `DefaultRequest` plugin. */
    const val API_BASE_URL = "https://api.meshtastic.org/"
}

/**
 * Shared [io.ktor.client.plugins.HttpRequestRetry] policy for both engines.
 *
 * Retries on 5xx server errors and on transient connection/IO failures (dropped sockets, DNS blips, read timeouts) —
 * the common failure mode on flaky cellular — with exponential backoff. [HttpClientDefaults.MAX_RETRIES] applies to
 * both rules.
 */
fun HttpRequestRetryConfig.configureDefaultRetry() {
    retryOnServerErrors(maxRetries = HttpClientDefaults.MAX_RETRIES)
    retryOnException(maxRetries = HttpClientDefaults.MAX_RETRIES, retryOnTimeout = true)
    exponentialDelay()
}
