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

import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.service.ApiService

@Module
class FDroidNetworkModule {

    /**
     * F-Droid builds intentionally avoid network calls to the Meshtastic API.
     *
     * We throw [UnsupportedOperationException] (an [Exception], not an [Error]) so that `safeCatching {}` in the
     * repositories captures the failure and falls back to the bundled JSON assets instead of crashing the app.
     */
    @Single
    fun provideApiService(): ApiService = object : ApiService {
        override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> =
            throw UnsupportedOperationException("getDeviceHardware is not supported on F-Droid builds.")

        override suspend fun getFirmwareReleases(): NetworkFirmwareReleases =
            throw UnsupportedOperationException("getFirmwareReleases is not supported on F-Droid builds.")
    }
}
