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
package org.meshtastic.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.NodeMetadataEntity
import org.meshtastic.core.repository.AppMetadataRepository
import org.meshtastic.core.repository.NodeMetadata

/**
 * Stores app-managed node metadata such as favorites, mute state, and notes.
 */
@Single(binds = [AppMetadataRepository::class])
class AppMetadataRepositoryImpl(
    private val dbManager: DatabaseProvider,
) : AppMetadataRepository {

    override val metadataByNum: Flow<Map<Int, NodeMetadata>> =
        dbManager.currentDb.flatMapLatest { db -> db.nodeMetadataDao().getAllFlow() }
            .map { list -> list.associate { it.num to it.toModel() } }

    override suspend fun setFavorite(nodeNum: Int, isFavorite: Boolean) {
        dbManager.withDb { it.nodeMetadataDao().setFavoriteEnsuringExists(nodeNum, isFavorite) }
    }

    override suspend fun setIgnored(nodeNum: Int, isIgnored: Boolean) {
        dbManager.withDb { it.nodeMetadataDao().setIgnoredEnsuringExists(nodeNum, isIgnored) }
    }

    override suspend fun setMuted(nodeNum: Int, isMuted: Boolean) {
        dbManager.withDb { it.nodeMetadataDao().setMutedEnsuringExists(nodeNum, isMuted) }
    }

    override suspend fun setNotes(nodeNum: Int, notes: String) {
        dbManager.withDb { it.nodeMetadataDao().setNotesEnsuringExists(nodeNum, notes) }
    }

    override suspend fun setManuallyVerified(nodeNum: Int, verified: Boolean) {
        dbManager.withDb { it.nodeMetadataDao().setManuallyVerifiedEnsuringExists(nodeNum, verified) }
    }

    override suspend fun delete(nodeNum: Int) {
        dbManager.withDb { it.nodeMetadataDao().delete(nodeNum) }
    }
}

private fun NodeMetadataEntity.toModel() = NodeMetadata(
    num = num,
    isFavorite = isFavorite,
    isIgnored = isIgnored,
    isMuted = isMuted,
    notes = notes,
    manuallyVerified = manuallyVerified,
)
