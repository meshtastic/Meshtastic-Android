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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.MeshLogDataStore
import org.meshtastic.core.prefs.meshlog.MeshLogPrefsImpl
import org.meshtastic.core.service.worker.MeshLogCleanupWorker
import org.meshtastic.core.testing.FakeMeshLogRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeshLogCleanupWorkerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `persisted disabled policy never deletes after a delayed cold load`() = runTest {
        val harness = createHarness(loggingEnabled = false, retentionDays = 7, StandardTestDispatcher(testScheduler))
        runCurrent()
        val reactiveCollectors = harness.dataStore.collectionCount

        val result = async { harness.worker.doWork() }
        runCurrent()

        assertNull(harness.repository.lastDeletedOlderThan, "synthetic defaults must not drive cleanup")
        assertFalse(result.isCompleted)
        assertEquals(reactiveCollectors + 1, harness.dataStore.collectionCount)

        harness.dataStore.releaseLoad()
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), result.await())
        assertNull(harness.repository.lastDeletedOlderThan)
    }

    @Test
    fun `persisted zero retention never deletes after a delayed cold load`() = runTest {
        val harness = createHarness(loggingEnabled = true, retentionDays = 0, StandardTestDispatcher(testScheduler))
        runCurrent()
        val reactiveCollectors = harness.dataStore.collectionCount

        val result = async { harness.worker.doWork() }
        runCurrent()

        assertNull(harness.repository.lastDeletedOlderThan, "synthetic defaults must not drive cleanup")
        assertFalse(result.isCompleted)
        assertEquals(reactiveCollectors + 1, harness.dataStore.collectionCount)

        harness.dataStore.releaseLoad()
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), result.await())
        assertNull(harness.repository.lastDeletedOlderThan)
    }

    @Test
    fun `persisted positive retention deletes once with the loaded policy`() = runTest {
        val harness = createHarness(loggingEnabled = true, retentionDays = 7, StandardTestDispatcher(testScheduler))
        runCurrent()
        val reactiveCollectors = harness.dataStore.collectionCount

        val result = async { harness.worker.doWork() }
        runCurrent()

        assertNull(harness.repository.lastDeletedOlderThan, "cleanup must wait for persisted policy")
        assertFalse(result.isCompleted)
        assertEquals(reactiveCollectors + 1, harness.dataStore.collectionCount)

        harness.dataStore.releaseLoad()
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), result.await())
        assertEquals(7, harness.repository.lastDeletedOlderThan)
    }

    @Test
    fun `cancellation while loading persisted policy propagates`() = runTest {
        val harness = createHarness(loggingEnabled = true, retentionDays = 7, StandardTestDispatcher(testScheduler))
        runCurrent()

        val result = async { harness.worker.doWork() }
        runCurrent()
        assertFalse(result.isCompleted)

        val cancellation = CancellationException("persisted policy load cancelled")
        harness.dataStore.failLoad(cancellation)
        runCurrent()

        val thrown = assertFailsWith<CancellationException> { result.await() }
        assertEquals(cancellation.message, thrown.message)
        assertNull(harness.repository.lastDeletedOlderThan)
    }

    private fun createHarness(loggingEnabled: Boolean, retentionDays: Int, dispatcher: CoroutineDispatcher): Harness {
        val dataStore = DelayedMeshLogDataStore(loggingEnabled, retentionDays)
        val meshLogPrefs =
            MeshLogPrefsImpl(dataStore, CoroutineDispatchers(io = dispatcher, main = dispatcher, default = dispatcher))
        val repository = FakeMeshLogRepository()
        val worker =
            TestListenableWorkerBuilder<MeshLogCleanupWorker>(context)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker =
                            MeshLogCleanupWorker(appContext, workerParameters, repository, meshLogPrefs)
                    },
                )
                .build()
        return Harness(worker, repository, dataStore)
    }

    private data class Harness(
        val worker: MeshLogCleanupWorker,
        val repository: FakeMeshLogRepository,
        val dataStore: DelayedMeshLogDataStore,
    )

    private class DelayedMeshLogDataStore(loggingEnabled: Boolean, retentionDays: Int) : MeshLogDataStore {
        private val loadGate = CompletableDeferred<Unit>()
        private val persisted =
            MutableStateFlow(
                preferencesOf(
                    MeshLogPrefsImpl.KEY_LOGGING_ENABLED_PREF to loggingEnabled,
                    MeshLogPrefsImpl.KEY_RETENTION_DAYS_PREF to retentionDays,
                ),
            )

        var collectionCount: Int = 0
            private set

        override val data: Flow<Preferences> = flow {
            collectionCount += 1
            loadGate.await()
            emitAll(persisted)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(persisted.value).also { persisted.value = it }

        fun releaseLoad() {
            loadGate.complete(Unit)
        }

        fun failLoad(cause: Throwable) {
            loadGate.completeExceptionally(cause)
        }
    }
}
