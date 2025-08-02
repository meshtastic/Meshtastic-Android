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
import androidx.core.content.edit
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.analytics.AnalyticsProvider
import com.geeksville.mesh.analytics.NopAnalytics
import com.geeksville.mesh.model.DeviceHardware
import timber.log.Timber

open class GeeksvilleApplication :
    Application(),
    Logging {

    companion object {
        lateinit var analytics: AnalyticsProvider
    }

    // / Are we running inside the testlab?
    val isInTestLab: Boolean
        get() {
            val testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab") ?: null
            if (testLabSetting != null) info("Testlab is $testLabSetting")
            return "true" == testLabSetting
        }

    private val analyticsPrefs: SharedPreferences by lazy { getSharedPreferences("analytics-prefs", MODE_PRIVATE) }

    var isAnalyticsAllowed: Boolean
        get() = analyticsPrefs.getBoolean("allowed", true)
        set(value) {
            analyticsPrefs.edit { putBoolean("allowed", value) }

            // Change the flag with the providers
            analytics.setEnabled(value && !isInTestLab) // Never do analytics in the test lab
        }

    @Suppress("UnusedParameter")
    fun askToRate(activity: AppCompatActivity) {
        // No-op for F-Droid version
    }

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val nopAnalytics = NopAnalytics(this)
        analytics = nopAnalytics
        isAnalyticsAllowed = false
    }
}

val Context.isGooglePlayAvailable: Boolean
    get() = false

@Suppress("UnusedParameter")
fun setAttributes(deviceVersion: String, deviceHardware: DeviceHardware) {
    // No-op for F-Droid version
}
