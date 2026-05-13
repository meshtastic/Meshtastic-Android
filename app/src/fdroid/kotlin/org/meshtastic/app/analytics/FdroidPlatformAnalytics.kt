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

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.koin.core.annotation.Single
import org.meshtastic.app.BuildConfig
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.PlatformAnalytics

/**
 * F-Droid specific implementation of [PlatformAnalytics]. This provides no-op implementations for analytics and other
 * platform services.
 */
@Single
class FdroidPlatformAnalytics : PlatformAnalytics {
    init {
        // For F-Droid builds we don't initialize external analytics services.
        // Configure Kermit logging: Debug level for debug builds, Info level for release builds.
        Logger.setMinSeverity(if (BuildConfig.DEBUG) Severity.Debug else Severity.Info)
        Logger.i {
            "F-Droid platform no-op analytics initialized (${if (BuildConfig.DEBUG) "Debug" else "Info"} logging)."
        }
    }

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {
        if (BuildConfig.DEBUG) Logger.d { "setDeviceAttributes: firmwareVersion=$firmwareVersion, model=$model" }
    }

    override val isPlatformServicesAvailable: Boolean
        get() = false

    override fun track(event: String, vararg properties: DataPair) {
        // No-op for F-Droid
    }
}
