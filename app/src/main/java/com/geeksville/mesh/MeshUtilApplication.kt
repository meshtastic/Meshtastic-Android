/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import com.geeksville.mesh.widget.LocalStatsWidgetReceiver
import com.geeksville.mesh.worker.MeshLogCleanupWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.core.android.AndroidEnvironment
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.prefs.meshlog.MeshLogPrefs
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * The main application class for Meshtastic.
 *
 * This class is annotated with [HiltAndroidApp] to enable Hilt for dependency injection. It initializes core
 * application components, including analytics and platform-specific helpers, and manages analytics consent based on
 * user preferences.
 */
@HiltAndroidApp
open class MeshUtilApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ContextServices.app = this
        initializeMaps(this)

        // Schedule periodic MeshLog cleanup
        scheduleMeshLogCleanup()

        // Generate and publish widget preview for Android 15+ widget picker
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applicationScope.launch {
                try {
                    Logger.i { "Pushing generated widget preview..." }
                    val result =
                        GlanceAppWidgetManager(this@MeshUtilApplication)
                            .setWidgetPreviews(
                                LocalStatsWidgetReceiver::class,
                                intSetOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN),
                            )
                    Logger.i { "setWidgetPreviews result: $result" }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Logger.e(e) { "Failed to set widget preview" }
                }
            }
        }

        // Initialize DatabaseManager asynchronously with current device address so DAO consumers have an active DB
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        applicationScope.launch { entryPoint.databaseManager().init(entryPoint.meshPrefs().deviceAddress) }
    }

    override fun onTerminate() {
        // Shutdown managers (useful for Robolectric tests)
        val entryPoint = EntryPointAccessors.fromApplication(this, AppEntryPoint::class.java)
        entryPoint.databaseManager().close()
        entryPoint.androidEnvironment().close()
        applicationScope.cancel()
        super.onTerminate()
    }

    private fun scheduleMeshLogCleanup() {
        val cleanupRequest =
            PeriodicWorkRequestBuilder<MeshLogCleanupWorker>(repeatInterval = 1.hours.toJavaDuration()).build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                MeshLogCleanupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                cleanupRequest,
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

    fun androidEnvironment(): AndroidEnvironment
}

fun logAssert(executeReliableWrite: Boolean) {
    if (!executeReliableWrite) {
        val ex = AssertionError("Assertion failed")
        Logger.e(ex) { "logAssert" }
        throw ex
    }
}
