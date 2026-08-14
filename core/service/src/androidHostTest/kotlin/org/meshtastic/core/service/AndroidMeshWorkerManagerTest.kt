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
package org.meshtastic.core.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.awaitCancellation
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.service.worker.SendMessageWorker
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMeshWorkerManagerTest {

    @Test
    fun `repeated persisted row scheduling keeps the active unique worker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(BlockingWorkerFactory())
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val workManager = WorkManager.getInstance(context)
        val persistedId = PersistedPacketId(myNodeNum = 42, uuid = 99L)
        val manager = AndroidMeshWorkerManager(workManager)

        manager.enqueueSendMessage(persistedId)
        val first = workManager.getWorkInfosForUniqueWork(workName(persistedId)).get().single()
        manager.enqueueSendMessage(persistedId)
        val retained = workManager.getWorkInfosForUniqueWork(workName(persistedId)).get().single()

        assertFalse(first.state.isFinished, "The first request must remain active for KEEP to apply")
        assertEquals(first.id, retained.id, "KEEP must retain the active work request instead of replacing it")
        workManager.cancelUniqueWork(workName(persistedId)).result.get()
    }

    private fun workName(id: PersistedPacketId) = "${SendMessageWorker.WORK_NAME_PREFIX}${id.myNodeNum}_${id.uuid}"

    private class BlockingWorkerFactory : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = if (workerClassName == SendMessageWorker::class.java.name) {
            BlockingWorker(appContext, workerParameters)
        } else {
            null
        }
    }

    private class BlockingWorker(appContext: Context, workerParameters: WorkerParameters) :
        CoroutineWorker(appContext, workerParameters) {
        override suspend fun doWork(): Result = awaitCancellation()
    }
}
