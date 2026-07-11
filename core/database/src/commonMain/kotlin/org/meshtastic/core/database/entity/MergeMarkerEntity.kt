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
package org.meshtastic.core.database.entity

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A durable record, in the canonical (destination) database, that a given source database has already been folded in by
 * [org.meshtastic.core.database.DatabaseMerger]. Written inside the same merge transaction as the copied rows, so it
 * commits atomically with them. When [org.meshtastic.core.database.DatabaseManager.associateDevice] re-runs the merge
 * on the next connection, `DatabaseMerger.merge` finds the marker at the top of its transaction and skips the whole
 * merge — closing the crash window between the merge commit and the datastore alias write, where a retry would
 * otherwise duplicate the fresh-id packet/discovery rows. Markers share the destination DB's lifecycle: if the
 * canonical DB is LRU-evicted and later recreated empty, its markers vanish with the merged data they describe.
 */
@Entity(tableName = "merge_marker")
data class MergeMarkerEntity(
    @PrimaryKey @ColumnInfo(name = "source_db_name") val sourceDbName: String,
    @ColumnInfo(name = "merged_at") val mergedAt: Long = 0L,
)
