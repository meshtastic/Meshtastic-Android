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
package org.meshtastic.core.ble.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleLoggingConfig
import org.meshtastic.core.common.BuildConfigProvider

@Module
@ComponentScan("org.meshtastic.core.ble")
class CoreBleModule {
    /**
     * Quiet by default in release; verbose (Kable [Events][com.juul.kable.logs.Logging.Level.Events]) in debug builds.
     * Always single-line for grep/logcat friendliness.
     */
    @Single
    fun provideBleLoggingConfig(buildConfig: BuildConfigProvider): BleLoggingConfig =
        if (buildConfig.isDebug) BleLoggingConfig.Debug else BleLoggingConfig.Release
}
