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

/**
 * Minimal [DatabaseManager] for web: exactly one OPFS-backed database exists (`core:database`'s
 * `SingleDatabaseProvider`), so there is nothing to switch, evict, or associate — every method is a straight reflection
 * of that single-database reality, not a stubbed-out no-op. android/jvm/iOS's real `DatabaseManager` (`nonWebMain`)
 * handles legacy-Android-DB migration, LRU eviction across cached per-device databases, and cross-transport merge; none
 * of that exists here because `SingleDatabaseProvider` itself doesn't support switching devices (see its own KDoc) —
 * this class can't add multi-device semantics `RadioControllerImpl` needs a [DatabaseManager] to fill in, without
 * `DatabaseManager` itself gaining a wasmJs implementation.
 */
class WebDatabaseManager : DatabaseManager {
    // No eviction on web — one OPFS file, unbounded by this class. Cap is a placeholder so callers reading it
    // (settings' cache-limit slider) see a real number, not zero.
    override val cacheLimit: StateFlow<Int> = MutableStateFlow(Int.MAX_VALUE)

    override fun getCurrentCacheLimit(): Int = Int.MAX_VALUE

    override fun setCacheLimit(limit: Int) {
        // No-op: nothing to evict against on a single, non-switching database.
    }

    override suspend fun cachedDeviceDbCount(): Int = 1

    override suspend fun switchActiveDatabase(address: String?) {
        // No-op: there is only ever one database, already active.
    }

    override suspend fun associateDevice(
        address: String,
        nodeNum: Int,
        deviceId: String?,
        isSessionActive: () -> Boolean,
    ) {
        // No-op: nothing to associate — the single database already serves every address.
    }

    override fun hasDatabaseFor(address: String?): Boolean = true
}
