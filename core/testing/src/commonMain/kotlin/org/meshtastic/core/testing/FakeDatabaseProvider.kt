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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import kotlin.concurrent.Volatile

/** A real [DatabaseProvider] that uses an in-memory database for testing. */
class FakeDatabaseProvider : DatabaseProvider {
    @Volatile private var db: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()
    private val _currentDb = MutableStateFlow(db)
    override val currentDb: StateFlow<MeshtasticDatabase> = _currentDb

    override suspend fun <T> withReadDb(block: suspend (MeshtasticDatabase) -> T): T = block(db)

    override suspend fun <T> withDb(block: suspend (MeshtasticDatabase) -> T): T? = block(db)

    /**
     * Simulates the active-DB switch that happens when the app selects a different device — the new DB has no rows, so
     * any cache that lived only in the previous DB is gone. Closes the prior DB to free its resources.
     */
    fun switchToNewDatabase() {
        val previous = db
        db = getInMemoryDatabaseBuilder().build()
        _currentDb.value = db
        previous.close()
    }

    fun close() {
        db.close()
    }
}
