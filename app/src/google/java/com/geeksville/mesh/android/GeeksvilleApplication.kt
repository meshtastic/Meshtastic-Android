/*
 * Copyright (c) 2024 Meshtastic LLC
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
import androidx.core.content.edit
import com.geeksville.mesh.analytics.AnalyticsProvider
import com.geeksville.mesh.util.exceptionReporter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.suddenh4x.ratingdialog.AppRating

/**
 * Created by kevinh on 1/4/15.
 */

open class GeeksvilleApplication : Application(), Logging {

    companion object {
        lateinit var analytics: AnalyticsProvider
    }

    /// Are we running inside the testlab?
    val isInTestLab: Boolean
        get() {
            val testLabSetting =
                Settings.System.getString(contentResolver, "firebase.test.lab") ?: null
            if(testLabSetting != null)
                info("Testlab is $testLabSetting")
            return "true" == testLabSetting
        }

    private val analyticsPrefs: SharedPreferences by lazy {
        getSharedPreferences("analytics-prefs", Context.MODE_PRIVATE)
    }

    var isAnalyticsAllowed: Boolean
        get() = analyticsPrefs.getBoolean("allowed", true)
        set(value) {
            analyticsPrefs.edit {
                putBoolean("allowed", value)
            }

            // Change the flag with the providers
            analytics.setEnabled(value && !isInTestLab) // Never do analytics in the test lab
        }

    /** Ask user to rate in play store */
    fun askToRate(activity: AppCompatActivity) {
        if (!isGooglePlayAvailable()) return

        exceptionReporter { // we don't want to crash our app because of bugs in this optional feature
            AppRating.Builder(activity)
                .setMinimumLaunchTimes(10) // default is 5, 3 means app is launched 3 or more times
                .setMinimumDays(10) // default is 5, 0 means install day, 10 means app is launched 10 or more days later than installation
                .setMinimumLaunchTimesToShowAgain(5) // default is 5, 1 means app is launched 1 or more times after neutral button clicked
                .setMinimumDaysToShowAgain(14) // default is 14, 1 means app is launched 1 or more days after neutral button clicked
                .showIfMeetsConditions()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val firebaseAnalytics = com.geeksville.mesh.analytics.FirebaseAnalytics(this)
        analytics = firebaseAnalytics

        // Set analytics per prefs
        isAnalyticsAllowed = isAnalyticsAllowed
    }
}

fun Context.isGooglePlayAvailable(): Boolean {
    return GoogleApiAvailabilityLight.getInstance()
        .isGooglePlayServicesAvailable(this)
        .let {
            it != ConnectionResult.SERVICE_MISSING &&
            it != ConnectionResult.SERVICE_INVALID
        }
}