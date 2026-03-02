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
package org.meshtastic.feature.messaging.domain.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.meshtastic.core.domain.MessageQueue
import javax.inject.Inject
import javax.inject.Singleton

/** Android implementation of [MessageQueue] that uses [WorkManager] for reliable background transmission. */
@Singleton
class WorkManagerMessageQueue @Inject constructor(private val workManager: WorkManager) : MessageQueue {

    override suspend fun enqueue(packetId: Int) {
        val workRequest =
            OneTimeWorkRequestBuilder<SendMessageWorker>()
                .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
                .build()

        workManager.enqueueUniqueWork(
            "${SendMessageWorker.WORK_NAME_PREFIX}$packetId",
            ExistingWorkPolicy.REPLACE,
            workRequest,
        )
    }
}
