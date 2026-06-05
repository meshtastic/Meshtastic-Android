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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.DeviceLinkEntity
import org.meshtastic.core.di.CoroutineDispatchers

@Single
class DeviceLinkLocalDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    private val deviceLinkDao
        get() = dbManager.currentDb.value.deviceLinkDao()

    fun observeAll(): Flow<List<DeviceLinkEntity>> = deviceLinkDao.observeAll()

    suspend fun getAll(): List<DeviceLinkEntity> = withContext(dispatchers.io) { deviceLinkDao.getAll() }

    suspend fun upsertAll(links: List<DeviceLinkEntity>) =
        withContext(dispatchers.io) { deviceLinkDao.upsertAll(links) }

    suspend fun deleteNotIn(keep: List<String>) = withContext(dispatchers.io) { deviceLinkDao.deleteNotIn(keep) }

    suspend fun deleteAll() = withContext(dispatchers.io) { deviceLinkDao.deleteAll() }

    suspend fun count(): Int = withContext(dispatchers.io) { deviceLinkDao.count() }
}
