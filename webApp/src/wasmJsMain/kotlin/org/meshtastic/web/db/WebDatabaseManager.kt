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
package org.meshtastic.web.db

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.database.DatabaseConstants

/**
 * [DatabaseManager] over the one OPFS database `SingleDatabaseProvider` opens: nothing to switch, evict or associate.
 */
class WebDatabaseManager : DatabaseManager {
    // The one database is the whole cache, which is the smallest limit the preference offers.
    override val cacheLimit: StateFlow<Int> = MutableStateFlow(DatabaseConstants.MIN_CACHE_LIMIT)

    override fun getCurrentCacheLimit(): Int = DatabaseConstants.MIN_CACHE_LIMIT

    override fun setCacheLimit(limit: Int) = Unit

    override suspend fun cachedDeviceDbCount(): Int = 1

    override suspend fun switchActiveDatabase(address: String?) = Unit

    override suspend fun associateDevice(
        address: String,
        nodeNum: Int,
        deviceId: String?,
        isSessionActive: () -> Boolean,
    ) = Unit

    override fun hasDatabaseFor(address: String?): Boolean = true
}
