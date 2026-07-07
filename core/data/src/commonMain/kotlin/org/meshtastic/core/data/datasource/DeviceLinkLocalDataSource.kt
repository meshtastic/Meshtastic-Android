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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.DeviceLinkEntity

@Single
class DeviceLinkLocalDataSource(private val dbManager: DatabaseProvider) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<DeviceLinkEntity>> =
        dbManager.currentDb.flatMapLatest { db -> db.deviceLinkDao().observeAll() }

    suspend fun getAll(): List<DeviceLinkEntity> = dbManager.withDb { it.deviceLinkDao().getAll() }.orEmpty()

    suspend fun upsertAll(links: List<DeviceLinkEntity>) {
        dbManager.withDb { it.deviceLinkDao().upsertAll(links) }
    }

    suspend fun deleteNotIn(keep: List<String>) {
        dbManager.withDb { it.deviceLinkDao().deleteNotIn(keep) }
    }

    suspend fun deleteAll() {
        dbManager.withDb { it.deviceLinkDao().deleteAll() }
    }

    suspend fun count(): Int = dbManager.withDb { it.deviceLinkDao().count() } ?: 0
}
