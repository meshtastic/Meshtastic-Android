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
package org.meshtastic.core.network

import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.service.ApiService

@Single
class FirmwareReleaseRemoteDataSource(
    private val apiService: ApiService,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun getFirmwareReleases(): NetworkFirmwareReleases =
        withContext(dispatchers.io) { apiService.getFirmwareReleases() }

    suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest =
        withContext(dispatchers.io) { apiService.getFirmwareReleaseManifest(manifestUrl) }

    /** The nightly preview pointer from meshtastic.github.io, or null when no nightly is published. */
    suspend fun getNightlyFirmware(): NetworkFirmwareNightly? =
        withContext(dispatchers.io) { apiService.getNightlyFirmware() }
}
