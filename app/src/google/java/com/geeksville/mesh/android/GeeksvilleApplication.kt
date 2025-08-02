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
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.compose.enableComposeActionTracking
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.timber.DatadogTree
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.analytics.AnalyticsProvider
import com.geeksville.mesh.analytics.FirebaseAnalytics
import com.geeksville.mesh.model.DeviceHardware
import com.geeksville.mesh.util.exceptionReporter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.suddenh4x.ratingdialog.AppRating
import timber.log.Timber

/** Created by kevinh on 1/4/15. */
open class GeeksvilleApplication :
    Application(),
    Logging {

    companion object {
        lateinit var analytics: AnalyticsProvider
    }

    // / Are we running inside the testlab?
    val isInTestLab: Boolean
        get() {
            val testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab")
            if (testLabSetting != null) info("Testlab is $testLabSetting")
            return "true" == testLabSetting
        }

    private val analyticsPrefs: SharedPreferences by lazy { getSharedPreferences("analytics-prefs", MODE_PRIVATE) }

    var isAnalyticsAllowed: Boolean
        get() = analyticsPrefs.getBoolean("allowed", true)
        set(value) {
            analyticsPrefs.edit { putBoolean("allowed", value) }
            val newConsent =
                if (value && !isInTestLab) {
                    TrackingConsent.GRANTED
                } else {
                    TrackingConsent.NOT_GRANTED
                }

            info(if (value) "Analytics enabled" else "Analytics disabled")

            if (Datadog.isInitialized()) {
                Datadog.setTrackingConsent(newConsent)
            } else {
                initDatadog()
            }

            // Change the flag with the providers
            analytics.setEnabled(value && !isInTestLab) // Never do analytics in the test lab
        }

    private val minimumLaunchTimes: Int = 10
    private val minimumDays: Int = 10
    private val minimumLaunchTimesToShowAgain: Int = 5
    private val minimumDaysToShowAgain: Int = 14

    /** Ask user to rate in play store */
    @Suppress("MagicNumber")
    fun askToRate(activity: AppCompatActivity) {
        if (!isGooglePlayAvailable) return

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

    override fun onCreate() {
        super.onCreate()

        val firebaseAnalytics = FirebaseAnalytics(this)
        analytics = firebaseAnalytics

        // Set analytics per prefs
        isAnalyticsAllowed = isAnalyticsAllowed
        initDatadog()
    }

    private val sampleRate = 100f

    private fun initDatadog() {
        val logger =
            Logger.Builder()
                .setNetworkInfoEnabled(true)
                .setLogcatLogsEnabled(true)
                .setRemoteSampleRate(sampleRate)
                .setBundleWithTraceEnabled(true)
                .setName("TimberLogger")
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
        val consent =
            if (isAnalyticsAllowed && !isInTestLab) {
                TrackingConsent.GRANTED
            } else {
                TrackingConsent.NOT_GRANTED
            }
        Datadog.initialize(this, configuration, consent)
        Datadog.setVerbosity(Log.VERBOSE)

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

        Timber.plant(Timber.DebugTree(), DatadogTree(logger))
    }
}

fun setAttributes(firmwareVersion: String, deviceHardware: DeviceHardware) {
    GlobalRumMonitor.get().addAttribute("firmware_version", firmwareVersion)
    GlobalRumMonitor.get().addAttribute("device_hardware", deviceHardware.hwModelSlug)
}

val Context.isGooglePlayAvailable: Boolean
    get() =
        GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(this).let {
            it != ConnectionResult.SERVICE_MISSING && it != ConnectionResult.SERVICE_INVALID
        }
