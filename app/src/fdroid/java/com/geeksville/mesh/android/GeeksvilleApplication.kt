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
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.geeksville.mesh.BuildConfig
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.info
import org.meshtastic.core.analytics.AnalyticsClient
import org.meshtastic.core.analytics.NopAnalytics
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
            val testLabSetting = Settings.System.getString(contentResolver, "firebase.test.lab") ?: null
            if (testLabSetting != null) {
                info("Testlab is $testLabSetting")
            }
            return "true" == testLabSetting
        }

    abstract val analyticsPrefs: AnalyticsPrefs

    @Suppress("EmptyFunctionBlock", "UnusedParameter")
    fun askToRate(application: AppCompatActivity) {}

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        val nopAnalytics = NopAnalytics(this)
        analytics = nopAnalytics
    }
}

@Suppress("UnusedParameter")
fun setAttributes(deviceVersion: String, deviceHardware: DeviceHardware) {
    // No-op for F-Droid version
    info("Setting attributes: deviceVersion=$deviceVersion, deviceHardware=$deviceHardware")
}

@Composable
fun AddNavigationTracking(navController: NavHostController) {
    // No-op for F-Droid version
    navController.addOnDestinationChangedListener { _, destination, _ ->
        debug("Navigation changed to: ${destination.route}")
    }
}

val Context.isAnalyticsAvailable: Boolean
    get() = false
