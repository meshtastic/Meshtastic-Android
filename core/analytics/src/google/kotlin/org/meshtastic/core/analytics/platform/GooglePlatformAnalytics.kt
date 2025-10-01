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

package org.meshtastic.core.analytics.platform

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.util.Log.WARN
import androidx.compose.runtime.Composable
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.compose.ExperimentalTrackingApi
import com.datadog.android.compose.NavigationViewTrackingEffect
import com.datadog.android.compose.enableComposeActionTracking
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.AcceptAllNavDestinations
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.compose.ComposeExtensionSupport
import com.datadog.android.timber.DatadogTree
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.opentelemetry.DatadogOpenTelemetry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.initialize
import com.google.firebase.perf.performance
import dagger.hilt.android.qualifiers.ApplicationContext
import io.opentelemetry.api.GlobalOpenTelemetry
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.meshtastic.core.analytics.BuildConfig
import org.meshtastic.core.analytics.DataPair
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs
import timber.log.Timber
import javax.inject.Inject

/**
 * Google Play Services specific implementation of [PlatformAnalytics]. This helper initializes and manages Firebase and
 * Datadog services, and subscribes to analytics preference changes to update consent accordingly.
 */
class GooglePlatformAnalytics
@Inject
constructor(
    @ApplicationContext private val context: Context,
    analyticsPrefs: AnalyticsPrefs,
) : PlatformAnalytics {

    private val sampleRate = 10f // For Datadog remote sample rate

    private val isInTestLab: Boolean
        get() {
            val testLabSetting = Settings.System.getString(context.contentResolver, "firebase.test.lab")
            return "true" == testLabSetting
        }

    companion object {
        private const val TAG = "GooglePlatformAnalytics"
        private const val SERVICE_NAME = "org.meshtastic"
    }

    init {
        initDatadog(context as Application, analyticsPrefs)
        initCrashlytics(context, analyticsPrefs)
        Timber.plant(Timber.DebugTree()) // Always plant DebugTree

        if (isPlatformServicesAvailable) {
            val datadogLogger =
                Logger.Builder()
                    .setService(SERVICE_NAME)
                    .setNetworkInfoEnabled(true)
                    .setRemoteSampleRate(sampleRate)
                    .setBundleWithTraceEnabled(true)
                    .setBundleWithRumEnabled(true)
                    .build()
            Timber.plant(DatadogTree(datadogLogger))
            Timber.plant(CrashlyticsTree())
        }
        // Initial consent state
        updateAnalyticsConsent(analyticsPrefs.analyticsAllowed)

        // Subscribe to analytics preference changes
        analyticsPrefs
            .getAnalyticsAllowedChangesFlow()
            .onEach { allowed -> updateAnalyticsConsent(allowed) }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)
    }

    private fun initDatadog(application: Application, analyticsPrefs: AnalyticsPrefs) {
        val configuration =
            Configuration.Builder(
                clientToken = BuildConfig.datadogClientToken,
                env = if (BuildConfig.DEBUG) "debug" else "release",
                variant = BuildConfig.FLAVOR,
            )
                .useSite(DatadogSite.US5)
                .setCrashReportsEnabled(true)
                .setUseDeveloperModeWhenDebuggable(true)
                .build()
        // Initialize with PENDING, consent will be updated via updateAnalyticsConsent
        Datadog.initialize(application, configuration, TrackingConsent.PENDING)
        Datadog.setUserInfo(analyticsPrefs.installId)
        Datadog.setVerbosity(WARN)

        val rumConfiguration =
            RumConfiguration.Builder(BuildConfig.datadogApplicationId)
                .trackAnonymousUser(true)
                .trackBackgroundEvents(true)
                .trackFrustrations(true)
                .trackLongTasks()
                .trackNonFatalAnrs(true)
                .trackUserInteractions()
                .setSessionSampleRate(sampleRate)
                .enableComposeActionTracking()
                .build()
        Rum.enable(rumConfiguration)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        val traceConfig = TraceConfiguration.Builder().setNetworkInfoEnabled(true).build()
        Trace.enable(traceConfig)

        GlobalOpenTelemetry.set(DatadogOpenTelemetry(serviceName = SERVICE_NAME))

        val sessionReplayConfig =
            SessionReplayConfiguration.Builder(sampleRate = sampleRate)
                .addExtensionSupport(ComposeExtensionSupport())
                .build()
        SessionReplay.enable(sessionReplayConfig)
    }

    private fun initCrashlytics(application: Application, analyticsPrefs: AnalyticsPrefs) {
        Firebase.initialize(application)
        Firebase.crashlytics.setUserId(analyticsPrefs.installId)
    }

    /**
     * Updates the consent status for analytics, performance, and crash reporting services.
     *
     * @param allowed True if analytics are allowed, false otherwise.
     */
    fun updateAnalyticsConsent(allowed: Boolean) {
        if (!isPlatformServicesAvailable || isInTestLab) {
            Timber.i("Analytics not available or in test lab, consent update skipped.")
            return
        }
        Timber.i(if (allowed) "Analytics enabled" else "Analytics disabled")

        Datadog.setTrackingConsent(if (allowed) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED)
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = allowed
        Firebase.analytics.setAnalyticsCollectionEnabled(allowed)
        Firebase.performance.isPerformanceCollectionEnabled = allowed

        if (allowed) {
            Firebase.crashlytics.sendUnsentReports()
        }
    }

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {
        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        GlobalRumMonitor.get().addAttribute("firmware_version", firmwareVersion.extractSemanticVersion())
        GlobalRumMonitor.get().addAttribute("device_hardware", model)
    }

    @OptIn(ExperimentalTrackingApi::class)
    @Composable
    override fun addNavigationTrackingEffect(navController: NavHostController) = {
        if (Datadog.isInitialized()) {
            NavigationViewTrackingEffect(
                navController = navController,
                trackArguments = true,
                destinationPredicate = AcceptAllNavDestinations(),
            )
        }
    }

    private val isGooglePlayAvailable: Boolean
        get() =
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context).let {
                it != ConnectionResult.SERVICE_MISSING && it != ConnectionResult.SERVICE_INVALID
            }

    private val isDatadogAvailable: Boolean
        get() = Datadog.isInitialized()

    override val isPlatformServicesAvailable: Boolean
        get() = isGooglePlayAvailable && isDatadogAvailable

    private class CrashlyticsTree : Timber.Tree() {
        companion object {
            private const val KEY_PRIORITY = "priority"
            private const val KEY_TAG = "tag"
            private const val KEY_MESSAGE = "message"
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (!Firebase.crashlytics.isCrashlyticsCollectionEnabled) return

            Firebase.crashlytics.setCustomKeys {
                key(KEY_PRIORITY, priority)
                key(KEY_TAG, tag ?: "No Tag")
                key(KEY_MESSAGE, message)
            }

            if (t == null) {
                Firebase.crashlytics.recordException(Exception(message))
            } else {
                Firebase.crashlytics.recordException(t)
            }
        }
    }

    private fun String.extractSemanticVersion(): String {
        val regex = "^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?".toRegex()
        val matchResult = regex.find(this)
        return matchResult?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.joinToString(".") ?: this
    }

    override fun track(event: String, vararg properties: DataPair) {
        val bundle = Bundle()
        properties.forEach {
            when (it.value) {
                is Double -> bundle.putDouble(it.name, it.value)
                is Int ->
                    bundle.putLong(it.name, it.value.toLong()) // Firebase expects Long for integer values in bundles
                is Long -> bundle.putLong(it.name, it.value)
                is Float -> bundle.putDouble(it.name, it.value.toDouble())
                is String -> bundle.putString(it.name, it.value as String?) // Explicitly handle String
                else -> bundle.putString(it.name, it.value.toString()) // Fallback for other types
            }
            Timber.tag(TAG).d("Analytics: track $event (${it.name} : ${it.value})")
        }
        Firebase.analytics.logEvent(event, bundle)
    }
}
