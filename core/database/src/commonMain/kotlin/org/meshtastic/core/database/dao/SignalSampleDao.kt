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
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.SignalSampleEntity
import org.meshtastic.core.database.entity.SignalSurveySessionEntity
import org.meshtastic.core.database.entity.SignalSurveySessionRow

@Dao
interface SignalSampleDao {
    @Insert suspend fun insertSession(session: SignalSurveySessionEntity): Long

    @Query("UPDATE signal_survey_session SET ended_at = :endedAt WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endedAt: Long)

    @Query("DELETE FROM signal_survey_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query(
        "SELECT s.*, (SELECT COUNT(*) FROM signal_sample WHERE session_id = s.id) AS reading_count " +
            "FROM signal_survey_session s ORDER BY s.started_at DESC",
    )
    fun sessions(): Flow<List<SignalSurveySessionRow>>

    @Query("SELECT * FROM signal_survey_session")
    suspend fun getAllSessions(): List<SignalSurveySessionEntity>

    @Insert suspend fun insert(sample: SignalSampleEntity)

    @Insert suspend fun insertAll(samples: List<SignalSampleEntity>)

    @Query("SELECT * FROM signal_sample WHERE session_id = :sessionId ORDER BY time")
    fun samples(sessionId: Long): Flow<List<SignalSampleEntity>>

    @Query("SELECT * FROM signal_sample WHERE session_id = :sessionId ORDER BY time")
    suspend fun getSamples(sessionId: Long): List<SignalSampleEntity>

    @Query("SELECT COUNT(*) FROM signal_sample WHERE session_id = :sessionId")
    suspend fun countSamples(sessionId: Long): Int
}
