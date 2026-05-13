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
package org.meshtastic.core.common.database

import kotlinx.coroutines.flow.StateFlow

/** Interface for managing database instances and cache limits. */
interface DatabaseManager {
    /** Reactive stream of the current database cache limit. */
    val cacheLimit: StateFlow<Int>

    /** Returns the current database cache limit from storage. */
    fun getCurrentCacheLimit(): Int

    /** Sets the database cache limit. */
    fun setCacheLimit(limit: Int)

    /** Switches the active database to the one associated with the given [address]. */
    suspend fun switchActiveDatabase(address: String?)

    /** Returns true if a database exists for the given device address. */
    fun hasDatabaseFor(address: String?): Boolean
}
