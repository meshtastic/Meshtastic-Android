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
package org.meshtastic.app

import android.app.ActivityManager
import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import coil3.ImageLoader
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.plugin.module.dsl.startKoin
import org.meshtastic.app.di.AndroidKoinApp
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.discovery_interrupted_scan_restored
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.service.worker.MeshLogCleanupWorker
import org.meshtastic.feature.discovery.DiscoveryScanEngine
import org.meshtastic.feature.widget.LocalStatsWidgetReceiver
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * [android.app.ApplicationExitInfo.getDescription] reported by Android 17's per-app memory ceiling (zram-swap kill).
 */
private const val MEMORY_LIMITER_ANON_SWAP_REASON = "MemoryLimiter:AnonSwap"
private const val MEMORY_LIMITER_PREFS_NAME = "memory_limiter_exit_check"
private const val KEY_LAST_REPORTED_EXIT_TIMESTAMP = "last_reported_exit_timestamp"
private const val MEMORY_LIMITER_SCAN_DEPTH = 5

/**
 * The main application class for Meshtastic.
 *
 * This class initializes core application components using Koin for dependency injection.
 */
open class MeshUtilApplication :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {

    // SupervisorJob alone only isolates siblings: a failed child still reaches the global uncaught
    // handler. These launches are best-effort background init, so report rather than escalate.
    private val applicationScopeExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        Logger.e(throwable) { "Background application init failed in $context" }
    }

    protected val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + applicationScopeExceptionHandler)

    /** Supplies Coil's process-wide loader without retaining an Activity in its singleton factory. */
    override fun newImageLoader(context: Context): ImageLoader = get<ImageLoader>()

    override fun onCreate() {
        super.onCreate()
        ContextServices.app = this
        configureFlavorApplication(BuildConfig.APPLICATION_ID)

        startKoin<AndroidKoinApp> {
            androidContext(this@MeshUtilApplication)
            workManagerFactory()
        }

        // Schedule periodic MeshLog cleanup. Off-main: WorkManager uses on-demand init here
        // (the startup provider is removed), so getInstance() opens WorkManager's Room DB.
        applicationScope.launch { scheduleMeshLogCleanup() }

        // ApplicationExitInfo requires API 30+. A "MemoryLimiter:AnonSwap" reason means Android 17's per-app
        // memory ceiling zram-swapped then killed the previous process — see reportMemoryLimiterExitIfPresent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applicationScope.launch { reportMemoryLimiterExitIfPresent() }
        }

        // Generate and publish widget preview for Android 15+ widget picker
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applicationScope.launch {
                suspend fun pushPreview() {
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

                pushPreview()

                val widgetStateProvider: org.meshtastic.feature.widget.LocalStatsWidgetStateProvider = get()
                try {
                    // Wait for real data for up to 30 seconds before pushing an updated preview
                    withTimeout(30.seconds) {
                        widgetStateProvider.state.first { it.showContent && it.nodeShortName != null }
                    }

                    Logger.i { "Real node data acquired. Pushing updated widget preview." }
                    pushPreview()
                } catch (e: TimeoutCancellationException) {
                    Logger.i(e) { "Timed out waiting for real node data for widget preview." }
                }
            }
        }

        // Initialize DatabaseManager asynchronously with current device address so DAO consumers have an active DB
        applicationScope.launch {
            val dbManager: DatabaseManager = get()
            val meshPrefs: MeshPrefs = get()
            dbManager.init(meshPrefs.deviceAddress.value)
        }

        // Restore a radio left detuned by a discovery scan that was interrupted (crash, BLE loss, process death)
        // before it could restore the home LoRa config itself. Never returns; watches reconnects for the app's life.
        // The engine stays UI-free — we localize the "restored" notice here and push it through the app-wide alert.
        applicationScope.launch {
            val scanEngine: DiscoveryScanEngine = get()
            val serviceRepository: ServiceRepository = get()
            scanEngine.restoreInterruptedSessionsOnReconnect { homePreset ->
                serviceRepository.setErrorMessage(
                    getStringSuspend(Res.string.discovery_interrupted_scan_restored, homePreset),
                    Severity.Warn,
                )
            }
        }
    }

    /**
     * Stops the background init launched by [onCreate]. Robolectric never calls [onTerminate], so a unit test that
     * boots this Application must call this itself — otherwise those jobs outlive the test on real
     * [Dispatchers.Default] threads and their failures surface against whichever test is running next.
     */
    @VisibleForTesting
    fun cancelBackgroundInit() {
        applicationScope.cancel()
    }

    override fun onTerminate() {
        // cancel() not cancelAndJoin(): joining under runBlocking on the main thread can deadlock.
        cancelBackgroundInit()
        try {
            runBlocking { get<DatabaseManager>().close() }
        } finally {
            super.onTerminate()
            org.koin.core.context.stopKoin()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun reportMemoryLimiterExitIfPresent() {
        // getHistoricalProcessExitReasons() returns a persistent record, not a queue, so the timestamp
        // watermark keeps a kill from being re-reported on every cold start. Scanning a few records
        // (newest first) instead of just index 0 matters: a crash during the restart that follows a
        // limiter kill slides the AnonSwap record down the list, and the newest entry alone would miss it.
        val prefs = getSharedPreferences(MEMORY_LIMITER_PREFS_NAME, Context.MODE_PRIVATE)
        val watermark = prefs.getLong(KEY_LAST_REPORTED_EXIT_TIMESTAMP, -1L)
        val kill =
            getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(null, 0, MEMORY_LIMITER_SCAN_DEPTH)
                ?.firstOrNull { it.description == MEMORY_LIMITER_ANON_SWAP_REASON && it.timestamp > watermark }
                ?: return
        prefs.edit().putLong(KEY_LAST_REPORTED_EXIT_TIMESTAMP, kill.timestamp).apply()

        // Force PlatformAnalytics construction so its Kermit log writers (Crashlytics/Datadog) are
        // registered before we log, even though nothing else has injected it yet this early in startup.
        get<PlatformAnalytics>()

        // Unlike the recoverable, environment-driven states ExpectedCondition.kt guards against (BLE off,
        // a denied permission), a memory-ceiling kill means this process's own footprint was too large —
        // an actionable engineering signal, so it is deliberately reported as a non-fatal (Error), not a
        // rate-only breadcrumb.
        Logger.e { "Previous process exit: $MEMORY_LIMITER_ANON_SWAP_REASON pss=${kill.pss}KB rss=${kill.rss}KB" }
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
        get() = Configuration.Builder().setWorkerFactory(get()).build()
}
