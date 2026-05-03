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

import kotlinx.coroutines.flow.StateFlow

/**
 * Provides multiplatform access to the current [MeshtasticDatabase] and a safe transactional helper. Platform
 * implementations manage the concrete lifecycle (Room on Android, etc.).
 */
interface DatabaseProvider {
    /** Reactive stream of the currently active database instance. */
    val currentDb: StateFlow<MeshtasticDatabase>

    /** Execute [block] against the current database, returning `null` if no database is available. */
    suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T?
}
