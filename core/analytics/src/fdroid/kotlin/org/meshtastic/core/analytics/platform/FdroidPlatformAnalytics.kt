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

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import org.meshtastic.core.analytics.BuildConfig
import org.meshtastic.core.analytics.DataPair
import timber.log.Timber
import javax.inject.Inject

/**
 * F-Droid specific implementation of [org.meshtastic.analytics.platform.PlatformAnalytics]. This provides no-op
 * implementations for analytics and other platform services.
 */
class FdroidPlatformAnalytics @Inject constructor() : PlatformAnalytics {
    init {
        // For F-Droid builds we don't initialize external analytics services.
        // In debug builds we attach a DebugTree for convenient local logging, but
        // release builds rely on system logging only.
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.i("F-Droid platform no-op analytics initialized (DebugTree planted).")
        } else {
            Timber.i("F-Droid platform no-op analytics initialized.")
        }
    }

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {
        // No-op for F-Droid
        Timber.d("Set device attributes called: firmwareVersion=$firmwareVersion, deviceHardware=$model")
    }

    @Composable
    override fun AddNavigationTrackingEffect(navController: NavHostController) {
        // No-op for F-Droid, but we can log navigation if needed for debugging
        if (BuildConfig.DEBUG) {
            navController.addOnDestinationChangedListener { _, destination, _ ->
                Timber.d("Navigation changed to: ${destination.route}")
            }
        }
    }

    override val isPlatformServicesAvailable: Boolean
        get() = false

    override fun track(event: String, vararg properties: DataPair) {
        Timber.d("Track called: event=$event, properties=${properties.toList()}")
    }
}
