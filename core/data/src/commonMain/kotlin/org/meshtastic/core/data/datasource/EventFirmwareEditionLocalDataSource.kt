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
import org.meshtastic.core.database.entity.EventFirmwareEditionEntity
import org.meshtastic.core.di.CoroutineDispatchers

@Single
class EventFirmwareEditionLocalDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {
    private val dao
        get() = dbManager.currentDb.value.eventFirmwareEditionDao()

    suspend fun getByEdition(edition: String): EventFirmwareEditionEntity? =
        withContext(dispatchers.io) { dao.getByEdition(edition) }

    suspend fun upsertAll(editions: List<EventFirmwareEditionEntity>) =
        withContext(dispatchers.io) { dao.upsertAll(editions) }

    suspend fun deleteNotIn(keep: List<String>) = withContext(dispatchers.io) { dao.deleteNotIn(keep) }

    suspend fun count(): Int = withContext(dispatchers.io) { dao.count() }
}
