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

import co.touchlab.kermit.Logger
import io.ktor.client.plugins.logging.Logger as KtorLogger

/**
 * Bridges Ktor's HTTP client logging to [Kermit][Logger] so HTTP request/response events appear in the standard app
 * logs rather than going to [System.out] via Ktor's default [io.ktor.client.plugins.logging.Logger.DEFAULT].
 *
 * Usage:
 * ```
 * HttpClient(engine) {
 *     install(Logging) {
 *         logger = KermitHttpLogger
 *         level = LogLevel.HEADERS
 *     }
 * }
 * ```
 */
object KermitHttpLogger : KtorLogger {
    override fun log(message: String) {
        Logger.d { message }
    }
}
