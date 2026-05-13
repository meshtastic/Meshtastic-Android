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
package org.meshtastic.core.network.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.koin.core.annotation.Single
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkFirmwareReleases

/** Client for the Meshtastic public API (device hardware catalog and firmware releases). */
interface ApiService {
    /** Fetches the device hardware catalog from the Meshtastic API. */
    suspend fun getDeviceHardware(): List<NetworkDeviceHardware>

    /** Fetches the list of available firmware releases from the Meshtastic API. */
    suspend fun getFirmwareReleases(): NetworkFirmwareReleases
}

/**
 * Ktor-based [ApiService] implementation.
 *
 * Uses relative paths — the base URL is set via the `DefaultRequest` plugin in the platform Koin modules.
 *
 * Registered with `binds = []` to prevent Koin from auto-binding to [ApiService]; host modules (`app`, `desktop`)
 * provide their own explicit `ApiService` binding to allow platform-specific `HttpClient` engines.
 */
@Single(binds = [])
class ApiServiceImpl(private val client: HttpClient) : ApiService {
    override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> = client.get("resource/deviceHardware").body()

    override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = client.get("github/firmware/list").body()
}
