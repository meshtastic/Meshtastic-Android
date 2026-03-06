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
package org.meshtastic.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksJsonDataSourceImpl
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSourceImpl
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSource
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSourceImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataSourceModule {
    @Binds
    @Singleton
    fun bindDeviceHardwareJsonDataSource(impl: DeviceHardwareJsonDataSourceImpl): DeviceHardwareJsonDataSource

    @Binds
    @Singleton
    fun bindFirmwareReleaseJsonDataSource(impl: FirmwareReleaseJsonDataSourceImpl): FirmwareReleaseJsonDataSource

    @Binds
    @Singleton
    fun bindBootloaderOtaQuirksJsonDataSource(
        impl: BootloaderOtaQuirksJsonDataSourceImpl,
    ): BootloaderOtaQuirksJsonDataSource
}
