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
import org.meshtastic.core.database.entity.EventFirmwareEditionEntity

@Dao
interface EventFirmwareEditionDao {
    @Upsert suspend fun upsertAll(editions: List<EventFirmwareEditionEntity>)

    @Query("SELECT * FROM event_firmware_edition WHERE edition = :edition")
    suspend fun getByEdition(edition: String): EventFirmwareEditionEntity?

    /**
     * Observes [edition], re-emitting whenever the table changes. This is what lets a background manifest refresh reach
     * already-visible event branding: a one-shot read resolves once when the connection is established, so metadata
     * that lands afterwards would sit in the table unseen until the user reconnected.
     */
    @Query("SELECT * FROM event_firmware_edition WHERE edition = :edition LIMIT 1")
    fun observeByEdition(edition: String): Flow<EventFirmwareEditionEntity?>

    /**
     * Deletes rows whose edition is not in [keep]. WARNING: `NOT IN ()` is always true in SQLite, so an **empty**
     * [keep] deletes every row — call sites must guard against passing an empty list (see
     * `EventFirmwareEditionLocalDataSource.deleteNotIn`, which no-ops on empty).
     */
    @Query("DELETE FROM event_firmware_edition WHERE edition NOT IN (:keep)")
    suspend fun deleteNotIn(keep: List<String>)

    @Query("SELECT COUNT(*) FROM event_firmware_edition")
    suspend fun count(): Int
}
