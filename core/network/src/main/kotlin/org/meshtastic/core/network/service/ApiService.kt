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

package org.meshtastic.core.network.service

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkFirmwareReleases
import javax.inject.Inject

interface ApiService {
    suspend fun getDeviceHardware(): List<NetworkDeviceHardware>

    suspend fun getFirmwareReleases(): NetworkFirmwareReleases
}

class ApiServiceImpl @Inject constructor(private val client: HttpClient) : ApiService {
    override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> =
        client.get("https://api.meshtastic.org/resource/deviceHardware").body()

    override suspend fun getFirmwareReleases(): NetworkFirmwareReleases =
        client.get("https://api.meshtastic.org/github/firmware/list").body()
}
