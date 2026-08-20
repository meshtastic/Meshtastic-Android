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
package org.meshtastic.core.data.datasource

import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.MaintenanceUf2CacheEntity
import org.meshtastic.core.di.CoroutineDispatchers

@Single
class MaintenanceUf2LocalDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    // Reads may use the direct accessor; writes go through withDb so they register with the cross-transport merge
    // drain barrier (see DatabaseProvider).
    private val dao
        get() = dbManager.currentDb.value.maintenanceUf2Dao()

    suspend fun get(): MaintenanceUf2CacheEntity? = withContext(dispatchers.io) { dao.get() }

    suspend fun upsert(entity: MaintenanceUf2CacheEntity) {
        withContext(dispatchers.io) { dbManager.withDb { it.maintenanceUf2Dao().upsert(entity) } }
    }

    suspend fun count(): Int = withContext(dispatchers.io) { dao.count() }
}
