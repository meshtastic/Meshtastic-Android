/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
 * Minimal specific implementation of [PlatformAnalytics].
 */
@Single
class MinimalPlatformAnalytics : PlatformAnalytics {
    init {
        if (BuildConfig.DEBUG) {
            Logger.setMinSeverity(Severity.Debug)
            Logger.i { "Minimal platform no-op analytics initialized (Debug mode)." }
        } else {
            Logger.setMinSeverity(Severity.Info)
            Logger.i { "Minimal platform no-op analytics initialized." }
        }
    }

    override fun setDeviceAttributes(firmwareVersion: String, model: String) {
        Logger.d { "Set device attributes called: firmwareVersion=$firmwareVersion, deviceHardware=$model" }
    }

    override val isPlatformServicesAvailable: Boolean
        get() = false

    override fun track(event: String, vararg properties: DataPair) {
        Logger.d { "Track called: event=$event, properties=${properties.toList()}" }
    }
}
