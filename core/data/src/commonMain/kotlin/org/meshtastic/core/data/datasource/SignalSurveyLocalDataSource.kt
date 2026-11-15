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
package org.meshtastic.core.data.datasource

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.database.DatabaseProvider
import org.meshtastic.core.database.entity.SignalSampleEntity
import org.meshtastic.core.database.entity.SignalSurveySessionEntity
import org.meshtastic.core.database.entity.SignalSurveySessionRow
import org.meshtastic.core.di.CoroutineDispatchers

@Single
class SignalSurveyLocalDataSource(
    private val dbManager: DatabaseProvider,
    private val dispatchers: CoroutineDispatchers,
) {

    fun sessions(): Flow<List<SignalSurveySessionRow>> =
        dbManager.observeCurrentDb("signalSurveySessions") { it.signalSampleDao().sessions() }

    fun samples(sessionId: Long): Flow<List<SignalSampleEntity>> =
        dbManager.observeCurrentDb("signalSurveySamples") { it.signalSampleDao().samples(sessionId) }

    suspend fun samplesOnce(sessionId: Long): List<SignalSampleEntity> =
        withContext(dispatchers.io) { dbManager.withReadDb { it.signalSampleDao().getSamples(sessionId) } }

    /** The new session's id, or null when the database is shutting down. */
    suspend fun openSession(session: SignalSurveySessionEntity): Long? =
        withContext(dispatchers.io) { dbManager.withDb { it.signalSampleDao().insertSession(session) } }

    /** Ends the session at [endedAt]. One that logged nothing is dropped, so the history holds only runs to show. */
    suspend fun closeSession(sessionId: Long, endedAt: Long) {
        withContext(dispatchers.io) {
            dbManager.withDb { db ->
                val dao = db.signalSampleDao()
                if (dao.countSamples(sessionId) == 0) {
                    dao.deleteSession(sessionId)
                } else {
                    dao.endSession(sessionId, endedAt)
                }
            }
        }
    }

    suspend fun insert(sample: SignalSampleEntity) {
        withContext(dispatchers.io) { dbManager.withDb { it.signalSampleDao().insert(sample) } }
    }

    suspend fun deleteSession(sessionId: Long) {
        withContext(dispatchers.io) { dbManager.withDb { it.signalSampleDao().deleteSession(sessionId) } }
    }
}
