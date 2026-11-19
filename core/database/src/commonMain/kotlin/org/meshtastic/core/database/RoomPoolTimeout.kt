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
package org.meshtastic.core.database

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long Room's pool waits for a connection before it logs the timeout, with a dump naming each holder, and waits
 * again; set in `configureCommon`. Below `DatabaseManager.WITH_DB_TIMEOUT_MS` so the dump lands before a `withDb`
 * abandon.
 */
internal val ROOM_POOL_ACQUIRE_TIMEOUT: Duration = 10.seconds
