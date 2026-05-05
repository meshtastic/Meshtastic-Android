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
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.NodeMetadataEntity

@Dao
interface NodeMetadataDao {

    @Upsert
    suspend fun upsert(metadata: NodeMetadataEntity)

    @Query("SELECT * FROM node_metadata")
    fun getAllFlow(): Flow<List<NodeMetadataEntity>>

    @Query("SELECT * FROM node_metadata WHERE num = :num")
    suspend fun getByNum(num: Int): NodeMetadataEntity?

    @Query("UPDATE node_metadata SET is_favorite = :isFavorite WHERE num = :num")
    suspend fun setFavorite(num: Int, isFavorite: Boolean)

    @Query("UPDATE node_metadata SET is_ignored = :isIgnored WHERE num = :num")
    suspend fun setIgnored(num: Int, isIgnored: Boolean)

    @Query("UPDATE node_metadata SET is_muted = :isMuted WHERE num = :num")
    suspend fun setMuted(num: Int, isMuted: Boolean)

    @Query("UPDATE node_metadata SET notes = :notes WHERE num = :num")
    suspend fun setNotes(num: Int, notes: String)

    @Query("UPDATE node_metadata SET manually_verified = :verified WHERE num = :num")
    suspend fun setManuallyVerified(num: Int, verified: Boolean)

    @Query("DELETE FROM node_metadata WHERE num = :num")
    suspend fun delete(num: Int)

    @Query("DELETE FROM node_metadata")
    suspend fun deleteAll()
}
