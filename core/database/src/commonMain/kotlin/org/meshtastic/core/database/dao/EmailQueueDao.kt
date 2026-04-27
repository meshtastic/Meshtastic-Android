/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.EmailQueueEntity

@Dao
interface EmailQueueDao {
    @Query("SELECT * FROM email_queue WHERE sent = 0 ORDER BY timestamp DESC")
    fun getUnsentEmails(): Flow<List<EmailQueueEntity>>

    @Insert
    suspend fun insert(email: EmailQueueEntity)

    @Query("UPDATE email_queue SET sent = 1 WHERE id = :id")
    suspend fun markAsSent(id: Long)

    @Query("DELETE FROM email_queue WHERE id = :id")
    suspend fun delete(id: Long)
}
