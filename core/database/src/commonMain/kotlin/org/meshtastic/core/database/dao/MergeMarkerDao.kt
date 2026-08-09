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
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import org.meshtastic.core.database.entity.MergeMarkerEntity

/** Tracks which source databases have already been merged into this (canonical) database. See [MergeMarkerEntity]. */
@Dao
interface MergeMarkerDao {

    /** IGNORE keeps the earliest marker on a re-run; the row's mere existence is what matters, not its timestamp. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMarker(marker: MergeMarkerEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM merge_marker WHERE source_db_name = :sourceDbName)")
    suspend fun isMerged(sourceDbName: String): Boolean
}
