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
import androidx.room3.Transaction
import androidx.room3.Upsert
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType

@Dao
interface FirmwareReleaseDao {
    @Upsert suspend fun insert(firmwareReleaseEntity: FirmwareReleaseEntity)

    @Query("DELETE FROM firmware_release")
    suspend fun deleteAll()

    @Query("DELETE FROM firmware_release WHERE release_type = :releaseType")
    suspend fun deleteByType(releaseType: FirmwareReleaseType)

    /**
     * Replaces all rows of the given [types] with [releases] in one transaction, so releases removed or reclassified
     * upstream are pruned and a crash mid-refresh can't leave the table half-written. Other types are untouched.
     */
    @Transaction
    suspend fun replaceByTypes(types: List<FirmwareReleaseType>, releases: List<FirmwareReleaseEntity>) {
        types.forEach { deleteByType(it) }
        releases.forEach { insert(it) }
    }

    @Query("SELECT * FROM firmware_release")
    suspend fun getAllReleases(): List<FirmwareReleaseEntity>

    @Query("SELECT * FROM firmware_release WHERE release_type = :releaseType")
    suspend fun getReleasesByType(releaseType: FirmwareReleaseType): List<FirmwareReleaseEntity>

    @Query("SELECT COUNT(*) FROM firmware_release")
    suspend fun count(): Int
}
