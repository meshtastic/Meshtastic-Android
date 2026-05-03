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
package org.meshtastic.app.analytics

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.opentelemetry.DatadogOpenTelemetry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.initialize
import io.opentelemetry.api.GlobalOpenTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.app.BuildConfig
import org.meshtastic.core.repository.AnalyticsPrefs
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.PlatformAnalytics
import co.touchlab.kermit.Logger as KermitLogger

/**
 * Google Play Services specific implementation of [PlatformAnalytics]. This helper initializes and manages Firebase and
 * Datadog services, and subscribes to analytics preference changes to update consent accordingly.
 *
 * This implementation delays initialization of SDKs until user consent is granted to reduce tracking "noise" and
 * respect privacy-focused environments.
 */
@Single
class GooglePlatformAnalytics(private val context: Context, private val analyticsPrefs: AnalyticsPrefs) :
    PlatformAnalytics {

    private val sampleRate = 100f // Match Apple: 100% sampling for cross-platform DataDog comparison

    private var datadogLogger: Logger? = null
    private var isFirebaseInitialized = false

    private val isInTestLab: Boolean
        get() {
            val testLabSetting = Settings.System.getString(context.contentResolver, "firebase.test.lab")
            return "true" == testLabSetting
        }

    companion object {
        private const val TAG = "GooglePlatformAnalytics"
        private const val SERVICE_NAME = "org.meshtastic"

        private const val KEY_PRIORITY = "priority"
        private const val KEY_TAG = "tag"
        private const val KEY_MESSAGE = "message"
    }

    init {
        // Setup Kermit log writers immediately, they will handle delayed SDK initialization gracefully.
        val writers = buildList {
            add(DatadogLogWriter())
            add(CrashlyticsLogWriter())
            if (BuildConfig.DEBUG) {
                add(co.touchlab.kermit.LogcatWriter())
            }
        }
        KermitLogger.setLogWriters(writers)
        KermitLogger.setMinSeverity(if (BuildConfig.DEBUG) Severity.Debug else Severity.Info)

        // Initial consent state
        updateAnalyticsConsent(analyticsPrefs.analyticsAllowed.value)

        // Subscribe to analytics preference changes
        analyticsPrefs.analyticsAllowed
            .onEach { allowed -> updateAnalyticsConsent(allowed) }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)
    }

    /**
     * Ensures that Datadog and Firebase SDKs are initialized if allowed. This is called lazily when consent is granted.
     */
    private fun ensureInitialized() {
        if (!analyticsPrefs.analyticsAllowed.value || isInTestLab) return

        if (!Datadog.isInitialized()) {
            initDatadog(context as Application)
            datadogLogger =
                Logger.Builder()
                    .setService(SERVICE_NAME)
                    .setNetworkInfoEnabled(false) // Disable to avoid collecting Local IP/SSID
                    .setRemoteSampleRate(sampleRate)
                    .setBundleWithTraceEnabled(true)
                    .setBundleWithRumEnabled(true)
                    .build()
        }

        if (!isFirebaseInitialized) {
            initCrashlytics(context as Application)
            isFirebaseInitialized = true
        }
    }

    private fun initDatadog(application: Application) {
        val configuration =
            Configuration.Builder(
                clientToken = BuildConfig.datadogClientToken,
                env = if (BuildConfig.DEBUG) "Local" else "Production",
                variant = BuildConfig.FLAVOR,
            )
                .useSite(DatadogSite.US5)
                .setCrashReportsEnabled(true)
                .setUseDeveloperModeWhenDebuggable(true)
                .build()
        // Initialize with PENDING, consent will be updated via updateAnalyticsConsent
        Datadog.initialize(application, configuration, TrackingConsent.PENDING)
        Datadog.setVerbosity(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)

        val rumConfiguration =
            RumConfiguration.Builder(BuildConfig.datadogApplicationId)
                .trackAnonymousUser(true)
                .trackBackgroundEvents(true) // Match Apple: track background events for cross-platform parity
                .trackFrustrations(false) // Disable click-tracking based frustration detection
                .trackLongTasks()
                .trackNonFatalAnrs(true)
                .setSessionSampleRate(sampleRate)
                .build()
        Rum.enable(rumConfiguration)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        val traceConfig = TraceConfiguration.Builder().setNetworkInfoEnabled(true).build()
        Trace.enable(traceConfig)

        // Session Replay for debug builds only, matching Apple's TestFlight-only gating.
        // Masks all text inputs to protect message content.
        if (BuildConfig.DEBUG) {
            val sessionReplayConfig =
                SessionReplayConfiguration.Builder(sampleRate)
                    .setTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL_INPUTS)
                    .build()
            SessionReplay.enable(sessionReplayConfig)
        }

        GlobalOpenTelemetry.set(DatadogOpenTelemetry(serviceName = SERVICE_NAME))
    }

    private fun initCrashlytics(application: Application) {
        Firebase.initialize(application)

        // Deny all ad-related consent types by default to minimize tracking noise
        Firebase.analytics.setConsent(
            mapOf(
                ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
                ConsentType.ANALYTICS_STORAGE to ConsentStatus.DENIED,
            ),
        )

        // Explicitly disable analytics collection until we confirm user consent
        Firebase.analytics.setAnalyticsCollectionEnabled(false)
    }

    /**
     * Updates the consent status for analytics, performance, and crash reporting services.
     *
     * @param allowed True if analytics are allowed, false otherwise.
     */
    fun updateAnalyticsConsent(allowed: Boolean) {
        if (isInTestLab) return

        if (allowed) {
            ensureInitialized()
        }

        KermitLogger.i { if (allowed) "Analytics enabled" else "Analytics disabled" }

        if (Datadog.isInitialized()) {
            Datadog.setTrackingConsent(if (allowed) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED)
        }

        if (isFirebaseInitialized) {
            Firebase.crashlytics.isCrashlyticsCollectionEnabled = allowed
            Firebase.analytics.setAnalyticsCollectionEnabled(allowed)

            if (allowed) {
                Firebase.crashlytics.sendUnsentReports()
                // Ensure ad-related PII collection remains disabled even if analytics is allowed
                Firebase.analytics.setUserProperty("allow_personalized_ads", "false")
            }

            // Manage Analytics Storage consent for Advanced Consent Mode
            val consentStatus = if (allowed) ConsentStatus.GRANTED else ConsentStatus.DENIED
            Firebase.analytics.setConsent(
                mapOf(
                    ConsentType.ANALYTICS_STORAGE to consentStatus,
                    // Keep ad-related types explicitly denied
                    ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                    ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                    ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
                ),
            )
        }
    }

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {
        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        GlobalRumMonitor.get().addAttribute("firmware_version", firmwareVersion.extractSemanticVersion())
        GlobalRumMonitor.get().addAttribute("device_hardware", model)
    }

    override fun trackConnect(
        firmwareVersion: String?,
        transportType: String?,
        hardwareModel: String?,
        nodes: Int,
        connectionRestored: Boolean,
    ) {
        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        val attributes = buildMap {
            firmwareVersion?.let { put("firmwareVersion", it) }
            transportType?.let { put("transportType", it) }
            hardwareModel?.let { put("hardwareModel", it) }
            put("nodes", nodes)
            if (connectionRestored) put("connectionRestored", true)
        }
        GlobalRumMonitor.get().addAction(RumActionType.CUSTOM, "connect", attributes)
    }

    private val isGooglePlayAvailable: Boolean
        get() =
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context).let {
                it != ConnectionResult.SERVICE_MISSING && it != ConnectionResult.SERVICE_INVALID
            }

    override val isPlatformServicesAvailable: Boolean
        get() = isGooglePlayAvailable

    private inner class CrashlyticsLogWriter : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            if (!isFirebaseInitialized) return
            if (!Firebase.crashlytics.isCrashlyticsCollectionEnabled) return

            // Add the log to the Crashlytics log buffer so it appears in reports
            Firebase.crashlytics.log("$severity/$tag: $message")

            // Filter out normal coroutine cancellations
            if (throwable is CancellationException) return

            // Only record non-fatal exceptions for actual Errors (Severity.Error or Severity.Assert)
            if (severity >= Severity.Error) {
                if (throwable != null) {
                    Firebase.crashlytics.recordException(throwable)
                } else {
                    Firebase.crashlytics.setCustomKeys {
                        key(KEY_PRIORITY, severity.ordinal)
                        key(KEY_TAG, tag)
                        key(KEY_MESSAGE, message)
                    }
                    Firebase.crashlytics.recordException(Exception(message))
                }
            }
        }
    }

    private inner class DatadogLogWriter : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            val logger = datadogLogger ?: return
            val datadogPriority =
                when (severity) {
                    Severity.Verbose -> android.util.Log.VERBOSE
                    Severity.Debug -> android.util.Log.DEBUG
                    Severity.Info -> android.util.Log.INFO
                    Severity.Warn -> android.util.Log.WARN
                    Severity.Error -> android.util.Log.ERROR
                    Severity.Assert -> android.util.Log.ASSERT
                }
            logger.log(datadogPriority, message, throwable, mapOf("tag" to tag))
        }
    }

    private fun String.extractSemanticVersion(): String {
        val regex = "^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?$".toRegex()
        val matchResult = regex.find(this)
        return matchResult?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.joinToString(".") ?: this
    }

    override fun track(event: String, vararg properties: DataPair) {
        if (!isFirebaseInitialized) return
        val bundle = Bundle()
        properties.forEach {
            val value = it.value
            when (value) {
                is Double -> bundle.putDouble(it.name, value)

                is Int -> bundle.putLong(it.name, value.toLong())

                // Firebase expects Long for integer values in bundles
                is Long -> bundle.putLong(it.name, value)

                is Float -> bundle.putDouble(it.name, value.toDouble())

                is String -> bundle.putString(it.name, value)

                // Explicitly handle String
                else -> bundle.putString(it.name, value.toString()) // Fallback for other types
            }
            KermitLogger.withTag(TAG).d {
                if (BuildConfig.DEBUG) {
                    "Analytics: track $event (${it.name} : $value)"
                } else {
                    "Analytics: track $event (${it.name})"
                }
            }
        }
        Firebase.analytics.logEvent(event, bundle)
    }
}
