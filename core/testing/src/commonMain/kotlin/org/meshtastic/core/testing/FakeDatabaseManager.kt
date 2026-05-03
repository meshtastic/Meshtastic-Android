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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.common.database.DatabaseManager

/** A test double for [DatabaseManager] that provides a simple implementation and tracks calls. */
class FakeDatabaseManager :
    BaseFake(),
    DatabaseManager {
    private val _cacheLimit = mutableStateFlow(DEFAULT_CACHE_LIMIT)
    override val cacheLimit: StateFlow<Int> = _cacheLimit

    var lastSwitchedAddress: String? = null
    val existingDatabases = mutableSetOf<String>()

    init {
        registerResetAction {
            _cacheLimit.value = DEFAULT_CACHE_LIMIT
            lastSwitchedAddress = null
            existingDatabases.clear()
        }
    }

    override fun getCurrentCacheLimit(): Int = _cacheLimit.value

    override fun setCacheLimit(limit: Int) {
        _cacheLimit.value = limit
    }

    override suspend fun switchActiveDatabase(address: String?) {
        lastSwitchedAddress = address
    }

    override fun hasDatabaseFor(address: String?): Boolean = address != null && existingDatabases.contains(address)

    companion object {
        private const val DEFAULT_CACHE_LIMIT = 100
    }
}
