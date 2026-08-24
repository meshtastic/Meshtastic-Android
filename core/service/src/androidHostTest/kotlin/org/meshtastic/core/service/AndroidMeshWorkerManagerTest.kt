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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.service.worker.SendMessageWorker
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMeshWorkerManagerTest {

    @Test
    fun `repeated persisted row scheduling keeps the active unique worker`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = BlockingWorkerFactory()
        val configuration =
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setWorkerFactory(factory)
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        val workManager = WorkManager.getInstance(context)
        val persistedId = PersistedPacketId(myNodeNum = 42, uuid = 99L)
        val manager = AndroidMeshWorkerManager(workManager)

        try {
            manager.enqueueSendMessage(persistedId)
            runBlocking { factory.started.await() }
            val first = workManager.getWorkInfosForUniqueWork(workName(persistedId)).get().single()
            assertEquals(WorkInfo.State.RUNNING, first.state, "The worker must be parked before the GC guard runs")
            // Regression guard (#6720): GC pressure here used to collect the worker's CallbackToFutureAdapter
            // completer, marking the running work FAILED so the KEEP enqueue below replaced it (flaky on loaded CI).
            repeat(3) {
                System.gc()
                System.runFinalization()
            }
            manager.enqueueSendMessage(persistedId)
            val retained = workManager.getWorkInfosForUniqueWork(workName(persistedId)).get().single()

            assertEquals(first.id, retained.id, "KEEP must retain the active work request instead of replacing it")
        } finally {
            factory.release.complete(Unit)
            workManager.cancelUniqueWork(workName(persistedId)).result.get()
        }
    }

    private fun workName(id: PersistedPacketId) = "${SendMessageWorker.WORK_NAME_PREFIX}${id.myNodeNum}_${id.uuid}"

    private class BlockingWorkerFactory : WorkerFactory() {
        // Awaiting release (instead of awaitCancellation) keeps the parked worker coroutine strongly reachable;
        // startWork()'s future holds its completer only weakly, and a GC'd completer fails the work (#6720).
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = if (workerClassName == SendMessageWorker::class.java.name) {
            BlockingWorker(appContext, workerParameters, started, release)
        } else {
            null
        }
    }

    private class BlockingWorker(
        appContext: Context,
        workerParameters: WorkerParameters,
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
    ) : CoroutineWorker(appContext, workerParameters) {
        override suspend fun doWork(): Result {
            started.complete(Unit)
            release.await()
            return Result.success()
        }
    }
}
