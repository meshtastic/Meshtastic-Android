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
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
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
import com.datadog.android.rum.configuration.VitalsUpdateFrequency
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Single
import org.meshtastic.app.BuildConfig
import org.meshtastic.core.common.log.ErrorReportThrottle
import org.meshtastic.core.common.log.shouldDowngradeForDatadog
import org.meshtastic.core.common.log.shouldReportAsException
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

    /**
     * True under Robolectric, where initializing the SDKs is both pointless and actively harmful.
     *
     * `Datadog.initialize` registers a `BroadcastReceiver` and installs a JVM shutdown hook. Robolectric tears the
     * application context down between test classes, so at JVM shutdown that hook unregisters a receiver that no longer
     * exists and throws `IllegalArgumentException: Receiver not registered` on its own `datadog_shutdown` thread.
     * Robolectric attributes a stray uncaught exception to whichever test is entering, so the failure lands on an
     * unrelated test — it surfaced as `MapNodeClusterItemsTest` failing at its `runComposeUiTest` line, in CI and in
     * the merge queue (where it ejects whatever else is queued), while passing locally and on rerun.
     *
     * Only the three test classes that deliberately boot the real [org.meshtastic.app.MeshUtilApplication] reach this
     * path; the rest already substitute a bare `Application` for the same family of reasons.
     */
    private val isRobolectric: Boolean
        get() = "robolectric" == Build.FINGERPRINT

    companion object {
        private const val TAG = "GooglePlatformAnalytics"
        private const val SERVICE_NAME = "org.meshtastic"

        private const val KEY_PRIORITY = "priority"
        private const val KEY_TAG = "tag"
        private const val KEY_MESSAGE = "message"
        private const val KEY_SUPPRESSED = "suppressed_since_last_report"
        private const val KEY_FIRMWARE_VERSION = "firmware_version"
        private const val KEY_DEVICE_HARDWARE = "device_hardware"
    }

    /** Separate budgets per backend — see the comment in [DatadogLogWriter]. */
    private val reportThrottle = ErrorReportThrottle(nowMs = SystemClock::elapsedRealtime)
    private val datadogThrottle = ErrorReportThrottle(nowMs = SystemClock::elapsedRealtime)

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
        if (!analyticsPrefs.analyticsAllowed.value || isInTestLab || isRobolectric) return

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
            // Pin RUM/crash/trace to the same service as the Logger and OpenTelemetry (SERVICE_NAME). The core SDK
            // otherwise defaults `service` to the applicationId (com.geeksville.mesh), splitting the app across two
            // Datadog services. On dd-sdk-android v2+ the service name is a Configuration.Builder constructor argument
            // (there is no setService() on the builder). See the PR description: this renames RUM/crash going forward.
            Configuration.Builder(
                clientToken = BuildConfig.datadogClientToken,
                env = if (BuildConfig.DEBUG) "Local" else "Production",
                variant = BuildConfig.FLAVOR,
                service = SERVICE_NAME,
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
                // Match Apple: disable long-task detection and continuous vitals monitoring to cut idle
                // CPU/battery drain (~15% savings). This app runs a 24/7 foreground service, so the same
                // concern applies. iOS sets longTaskThreshold = nil and vitalsUpdateFrequency = nil.
                .setVitalsUpdateFrequency(VitalsUpdateFrequency.NEVER)
                .trackNonFatalAnrs(true) // Android-specific; no iOS equivalent
                .setSessionSampleRate(sampleRate)
                .build()
        Rum.enable(rumConfiguration)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        val traceConfig = TraceConfiguration.Builder().setNetworkInfoEnabled(true).build()
        Trace.enable(traceConfig)

        // Session Replay is Android-only debug tooling — iOS ships no Session Replay at all. Enabled for
        // debug builds only, and MASK_ALL so no message content is ever recorded: MASK_ALL_INPUTS,
        // which this used before, masks input fields only and leaves rendered text — a conversation on
        // screen, a node's position — in the replay. That was inert while CI debug builds carried fake
        // datadog tokens; it stops being inert the moment they carry real ones.
        if (BuildConfig.DEBUG) {
            val sessionReplayConfig =
                SessionReplayConfiguration.Builder(sampleRate)
                    .setTextAndInputPrivacy(TextAndInputPrivacy.MASK_ALL)
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
        val semanticFirmware = firmwareVersion.extractSemanticVersion()

        // The connected radio is the most diagnostic axis this app has, and crash triage happens in Crashlytics.
        // Without these a crash report says nothing about which hardware or firmware produced it. Keys are sticky
        // for the process and are refreshed on every connect; there is no disconnect hook on PlatformAnalytics, so
        // a crash after an explicit disconnect still carries the last radio's values.
        // Deliberately not gated on isCrashlyticsCollectionEnabled: setting a key is legal while collection is off,
        // and updateAnalyticsConsent does not replay device attributes, so gating would leave the keys unset until
        // the next connect for anyone who grants consent after pairing a radio.
        if (isFirebaseInitialized) {
            Firebase.crashlytics.setCustomKeys {
                key(KEY_FIRMWARE_VERSION, semanticFirmware)
                key(KEY_DEVICE_HARDWARE, model)
            }
        }

        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        GlobalRumMonitor.get().addAttribute("firmware_version", semanticFirmware)
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

    override fun trackAction(name: String, attributes: Map<String, Any>) {
        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        GlobalRumMonitor.get().addAction(RumActionType.CUSTOM, name, attributes)
    }

    override fun startScreenView(key: String, name: String) {
        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        GlobalRumMonitor.get().startView(key = key, name = name)
    }

    override fun stopScreenView(key: String) {
        if (!Datadog.isInitialized() || !GlobalRumMonitor.isRegistered()) return
        GlobalRumMonitor.get().stopView(key = key)
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

            // Cancellations and expected conditions stay breadcrumbs only — see shouldReportAsException.
            if (!shouldReportAsException(severity, throwable)) return

            val suppressed = reportThrottle.acquire(ErrorReportThrottle.signature(tag, message)) ?: return

            // Custom keys stay set for every later report, so this is written unconditionally — skipping it when
            // the count is zero would leave an earlier report's positive count attached to this one.
            Firebase.crashlytics.setCustomKeys { key(KEY_SUPPRESSED, suppressed) }

            if (throwable != null) {
                Firebase.crashlytics.recordException(throwable)
            } else {
                Firebase.crashlytics.setCustomKeys {
                    key(KEY_PRIORITY, severity.ordinal)
                    key(KEY_TAG, tag)
                    key(KEY_MESSAGE, message)
                }
                Firebase.crashlytics.recordException(loggedException(message))
            }
        }
    }

    private inner class DatadogLogWriter : LogWriter() {
        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            val logger = datadogLogger ?: return
            // The Datadog SDK turns any log at ERROR or above into a RUM error purely from the level — it has no
            // per-call opt-out — so downgrading to WARN is the only way to keep an expected condition out of RUM
            // error tracking while still emitting the log line. Note this deliberately keeps CancellationException
            // at error here even though Crashlytics drops it; see shouldDowngradeForDatadog.
            val effectiveSeverity = if (shouldDowngradeForDatadog(severity, throwable)) Severity.Warn else severity

            // Only error-level logs become RUM errors, so only those are worth throttling; everything below stays a
            // plain log line and is cheap. Sharing the throttle with the Crashlytics writer would halve each
            // backend's allowance, so they keep separate budgets for the same signature.
            var suppressed = 0
            if (effectiveSeverity >= Severity.Error) {
                suppressed = datadogThrottle.acquire(ErrorReportThrottle.signature(tag, message)) ?: return
            }

            val datadogPriority =
                when (effectiveSeverity) {
                    Severity.Verbose -> android.util.Log.VERBOSE
                    Severity.Debug -> android.util.Log.DEBUG
                    Severity.Info -> android.util.Log.INFO
                    Severity.Warn -> android.util.Log.WARN
                    Severity.Error -> android.util.Log.ERROR
                    Severity.Assert -> android.util.Log.ASSERT
                }
            val attributes =
                buildMap<String, Any> {
                    put("tag", tag)
                    if (suppressed > 0) put(KEY_SUPPRESSED, suppressed)
                }
            logger.log(datadogPriority, message, throwable, attributes)
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

/**
 * Class-name prefixes of the logging machinery that sits between a call site and [loggedException].
 *
 * Resolved at runtime rather than written as literals so they still match after R8 renames them. Kermit is matched by
 * the package of [LogWriter], which is the Kermit type this file already depends on.
 */
private val loggingFramePrefixes: List<String> =
    listOfNotNull(
        GooglePlatformAnalytics::class.java.name,
        LogWriter::class.java.name.substringBeforeLast('.', "").takeIf { it.isNotBlank() },
    )

/**
 * Builds the stand-in exception for a log line that carried no [Throwable].
 *
 * Crashlytics groups a report by the topmost frame it can attribute. Because the exception is constructed inside the
 * log writer, every such stack starts with that writer and Kermit's dispatch, and Crashlytics walks past synthetic
 * coroutine lambda frames looking for something nameable — so every error logged from inside a `suspend` block
 * collapsed into a single `BaseLogger.processLog` issue (4,636 events across 1,203 users in a day and a half, hiding a
 * dozen unrelated signatures). Dropping the logging frames puts the real call site on top.
 *
 * Falls back to the untrimmed stack if the trim would leave nothing, since R8 may repackage these classes.
 */
private fun loggedException(message: String): Exception = Exception(message).apply {
    val callerFrames = stackTrace.dropWhile { frame -> loggingFramePrefixes.any { frame.className.startsWith(it) } }
    if (callerFrames.isNotEmpty()) stackTrace = callerFrames.toTypedArray()
}
