/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.meshtastic.core.database.entity.FirmwareReleaseEntity
import org.meshtastic.core.database.entity.FirmwareReleaseType

@Dao
interface FirmwareReleaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(firmwareReleaseEntity: FirmwareReleaseEntity)

    @Query("DELETE FROM firmware_release")
    suspend fun deleteAll()

    @Query("SELECT * FROM firmware_release")
    suspend fun getAllReleases(): List<FirmwareReleaseEntity>

    @Query("SELECT * FROM firmware_release WHERE release_type = :releaseType")
    suspend fun getReleasesByType(releaseType: FirmwareReleaseType): List<FirmwareReleaseEntity>
}
