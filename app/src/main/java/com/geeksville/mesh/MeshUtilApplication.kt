/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.geeksville.mesh.worker.MeshLogCleanupWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The main application class for Meshtastic.
 *
 * This class is annotated with [HiltAndroidApp] to enable Hilt for dependency injection. It initializes core
 * application components, including analytics and platform-specific helpers, and manages analytics consent based on
 * user preferences.
 */
@HiltAndroidApp
class MeshUtilApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        initializeMaps(this)

        // Schedule periodic MeshLog cleanup
        scheduleMeshLogCleanup()
        enqueueImmediateCleanupIfNeeded()

        // Initialize DatabaseManager asynchronously with current device address so DAO consumers have an active DB
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        CoroutineScope(Dispatchers.Default).launch {
            entryPoint.databaseManager().init(entryPoint.meshPrefs().deviceAddress)
        }
    }

    private fun scheduleMeshLogCleanup() {
        val cleanupRequest =
            PeriodicWorkRequestBuilder<MeshLogCleanupWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(MeshLogCleanupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, cleanupRequest)
    }

    private fun enqueueImmediateCleanupIfNeeded() {
        // Use entry point to access prefs outside of Hilt graph
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        val meshLogPrefs = entryPoint.meshLogPrefs()
        val retentionDays = meshLogPrefs.retentionDays
        if (!meshLogPrefs.loggingEnabled || retentionDays == MeshLogPrefs.NEVER_CLEAR_RETENTION_DAYS) {
            Logger.i { "Skipping immediate MeshLog cleanup; loggingEnabled=${meshLogPrefs.loggingEnabled}, retention=$retentionDays" }
            return
        }
        Logger.i { "Enqueuing immediate MeshLog cleanup with retentionDays=$retentionDays" }
        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "${MeshLogCleanupWorker.WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MeshLogCleanupWorker>().build(),
            )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun databaseManager(): DatabaseManager

    fun meshPrefs(): MeshPrefs

    fun meshLogPrefs(): MeshLogPrefs
}

fun logAssert(executeReliableWrite: Boolean) {
    if (!executeReliableWrite) {
        val ex = AssertionError("Assertion failed")
        Logger.e(ex) { "logAssert" }
        throw ex
    }
}
