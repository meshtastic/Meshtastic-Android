/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.DiscordQueueEntity

@Dao
interface DiscordQueueDao {
    @Query("SELECT * FROM discord_queue WHERE sent = 0 ORDER BY timestamp DESC")
    fun getUnsentMessages(): Flow<List<DiscordQueueEntity>>

    @Insert
    suspend fun insert(message: DiscordQueueEntity)

    @Query("UPDATE discord_queue SET sent = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)

    @Query("DELETE FROM discord_queue WHERE id = :id")
    suspend fun delete(id: Long)
}
