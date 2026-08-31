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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single

/**
 * Minimal wasmJs [DatabaseProvider]: exactly one real, persistent, OPFS-backed database — no multi-device switching, no
 * legacy-Android-DB migration, no cross-transport merge. Those are `DatabaseManager`'s job on android/jvm/iOS, and that
 * whole orchestrator is out of scope for a web client (no BLE/USB device-switching story exists there yet); it also
 * can't exist here at all, since it's built on `DataStore<Preferences>`, which has no wasmJs variant.
 *
 * The database opens lazily on first access (Koin instantiates this eagerly as a `@Single`, but opening a database is
 * not something a constructor should do) rather than behind any explicit lifecycle — wasmJs has exactly one caller path
 * today and nothing to defer against.
 */
@Single(binds = [DatabaseProvider::class])
class SingleDatabaseProvider : DatabaseProvider {

    private val database: MeshtasticDatabase by lazy { getDatabaseBuilder(DatabaseConstants.DEFAULT_DB_NAME).build() }

    override val currentDb: StateFlow<MeshtasticDatabase> by lazy { MutableStateFlow(database) }

    override fun <T> observeCurrentDb(query: (MeshtasticDatabase) -> Flow<T>): Flow<T> = query(database)

    override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(database)

    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(database)
}
