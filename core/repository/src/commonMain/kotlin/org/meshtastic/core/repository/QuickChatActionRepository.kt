
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.QuickChatAction

interface QuickChatActionRepository {
    fun getAllActions(): Flow<List<QuickChatAction>>
    suspend fun upsert(action: QuickChatAction)
    suspend fun deleteAll()
    suspend fun delete(action: QuickChatAction)
    suspend fun setItemPosition(uuid: Long, newPos: Int)
}
