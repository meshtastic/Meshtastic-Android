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

package com.geeksville.mesh.android

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
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
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.util.exceptionReporter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.crashlytics.setCustomKeys
import com.google.firebase.initialize
import com.suddenh4x.ratingdialog.AppRating
import io.opentelemetry.api.GlobalOpenTelemetry
import org.meshtastic.core.analytics.AnalyticsClient
import org.meshtastic.core.analytics.FirebaseAnalytics
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.prefs.analytics.AnalyticsPrefs
import timber.log.Timber

abstract class GeeksvilleApplication :
    Application(),
    Logging {

    companion object {
        lateinit var analytics: AnalyticsClient
    }

    // / Are we running inside the testlab?
    val isInTestLab: Boolean
        get() {
            val testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab")
            if (testLabSetting != null) {
                info("Testlab is $testLabSetting")
            }
            return "true" == testLabSetting
        }

    abstract val analyticsPrefs: AnalyticsPrefs

    private val minimumLaunchTimes: Int = 10
    private val minimumDays: Int = 10
    private val minimumLaunchTimesToShowAgain: Int = 5
    private val minimumDaysToShowAgain: Int = 14

    /** Ask user to rate in play store */
    @Suppress("MagicNumber")
    fun askToRate(activity: AppCompatActivity) {
        if (!isGooglePlayAvailable) return

        @Suppress("MaxLineLength")
        exceptionReporter {
            // we don't want to crash our app because of bugs in this optional feature
            AppRating.Builder(activity)
                .setMinimumLaunchTimes(minimumLaunchTimes) // default is 5, 3 means app is launched 3 or more times
                .setMinimumDays(
                    minimumDays,
                ) // default is 5, 0 means install day, 10 means app is launched 10 or more days
                // later than installation
                .setMinimumLaunchTimesToShowAgain(
                    minimumLaunchTimesToShowAgain,
                ) // default is 5, 1 means app is launched 1 or more times after neutral button
                // clicked
                .setMinimumDaysToShowAgain(
                    minimumDaysToShowAgain,
                ) // default is 14, 1 means app is launched 1 or more days after neutral button
                // clicked
                .showIfMeetsConditions()
        }
    }

    lateinit var analyticsPrefsChangedListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate() {
        super.onCreate()
        initDatadog()
        initCrashlytics()
        updateAnalyticsConsent()
        // listen for changes to analytics prefs
        analyticsPrefsChangedListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "allowed") {
                    updateAnalyticsConsent()
                }
            }
        getSharedPreferences("analytics-prefs", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(analyticsPrefsChangedListener)
    }

    private val sampleRate = 100f

    private fun initCrashlytics() {
        analytics = FirebaseAnalytics(analyticsPrefs.installId)
        Firebase.initialize(this)
        Firebase.crashlytics.setUserId(analyticsPrefs.installId)
        Timber.plant(CrashlyticsTree())
    }

    private fun updateAnalyticsConsent() {
        if (!isAnalyticsAvailable || isInTestLab) {
            info("Analytics not available")
            return
        }
        val isAnalyticsAllowed = analyticsPrefs.analyticsAllowed
        info(if (isAnalyticsAllowed) "Analytics enabled" else "Analytics disabled")
        Datadog.setTrackingConsent(if (isAnalyticsAllowed) TrackingConsent.GRANTED else TrackingConsent.NOT_GRANTED)

        analytics.setEnabled(isAnalyticsAllowed)
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = isAnalyticsAllowed
        Firebase.analytics.setAnalyticsCollectionEnabled(isAnalyticsAllowed)
        Firebase.crashlytics.sendUnsentReports()
    }

    private class CrashlyticsTree : Timber.Tree() {

        companion object {
            private const val KEY_PRIORITY = "priority"
            private const val KEY_TAG = "tag"
            private const val KEY_MESSAGE = "message"
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
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

    private fun initDatadog() {
        val logger =
            Logger.Builder()
                .setNetworkInfoEnabled(true)
                .setRemoteSampleRate(sampleRate)
                .setBundleWithTraceEnabled(true)
                .setBundleWithRumEnabled(true)
                .build()
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
        val consent = TrackingConsent.PENDING
        Datadog.initialize(this, configuration, consent)
        Datadog.setUserInfo(analyticsPrefs.installId)

        val rumConfiguration =
            RumConfiguration.Builder(BuildConfig.datadogApplicationId)
                .trackAnonymousUser(true)
                .trackBackgroundEvents(true)
                .trackFrustrations(true)
                .trackLongTasks()
                .trackNonFatalAnrs(true)
                .trackUserInteractions()
                .enableComposeActionTracking()
                .build()
        Rum.enable(rumConfiguration)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        val traceConfig = TraceConfiguration.Builder().build()
        Trace.enable(traceConfig)

        GlobalOpenTelemetry.set(DatadogOpenTelemetry(BuildConfig.APPLICATION_ID))

        val sessionReplayConfig =
            SessionReplayConfiguration.Builder(sampleRate = 20.0f)
                // in case you need Jetpack Compose support
                .addExtensionSupport(ComposeExtensionSupport())
                .build()

        SessionReplay.enable(sessionReplayConfig)

        Timber.plant(Timber.DebugTree(), DatadogTree(logger))
    }
}

fun setAttributes(firmwareVersion: String, deviceHardware: DeviceHardware) {
    GlobalRumMonitor.get().addAttribute("firmware_version", firmwareVersion.extractSemanticVersion())
    GlobalRumMonitor.get().addAttribute("device_hardware", deviceHardware.hwModelSlug)
}

private val Context.isGooglePlayAvailable: Boolean
    get() =
        GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(this).let {
            it != ConnectionResult.SERVICE_MISSING && it != ConnectionResult.SERVICE_INVALID
        }

private val isDatadogAvailable: Boolean = Datadog.isInitialized()

val Context.isAnalyticsAvailable: Boolean
    get() = isDatadogAvailable && isGooglePlayAvailable

@OptIn(ExperimentalTrackingApi::class)
@Composable
fun AddNavigationTracking(navController: NavHostController) {
    NavigationViewTrackingEffect(
        navController = navController,
        trackArguments = true,
        destinationPredicate = AcceptAllNavDestinations(),
    )
}

fun String.extractSemanticVersion(): String {
    // Regex to capture up to three numeric parts separated by dots
    val regex = """^(\d+)(?:\.(\d+))?(?:\.(\d+))?""".toRegex()
    val matchResult = regex.find(this)
    return matchResult?.groupValues?.drop(1)?.filter { it.isNotEmpty() }?.joinToString(".")
        ?: this // Fallback to original if no match
}
