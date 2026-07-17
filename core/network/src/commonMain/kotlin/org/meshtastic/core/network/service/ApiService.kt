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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.NetworkFirmwareReleases

/**
 * Pointer to the nightly preview build published by CI to meshtastic.github.io. Served from GitHub Pages raw content
 * (not api.meshtastic.org) as `text/plain`, so it is parsed manually rather than via content negotiation.
 */
private const val NIGHTLY_INDEX_URL =
    "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-nightly/index.json"

private val nightlyIndexJson = Json { ignoreUnknownKeys = true }

/** Client for the Meshtastic public API (device hardware catalog and firmware releases). */
interface ApiService {
    /** Fetches the device hardware catalog from the Meshtastic API. */
    suspend fun getDeviceHardware(): List<NetworkDeviceHardware>

    /** Fetches the resolved device-links catalog (msh.to purchase links) from the Meshtastic API. */
    suspend fun getDeviceLinks(): NetworkDeviceLinksResponse

    /** Fetches the list of available firmware releases from the Meshtastic API. */
    suspend fun getFirmwareReleases(): NetworkFirmwareReleases

    /** Fetches the target manifest referenced by a firmware release's `zip_url`. */
    suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest

    /**
     * Fetches the nightly preview build pointer from meshtastic.github.io. Returns null when no nightly is currently
     * published (HTTP 404); throws on transport or server errors so callers can distinguish "gone" from "unreachable".
     */
    suspend fun getNightlyFirmware(): NetworkFirmwareNightly?

    /** Fetches event-firmware display metadata (editions, welcome messages, links) from the Meshtastic API. */
    suspend fun getEventFirmware(): EventFirmwareResponse
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

    override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse = client.get("resource/deviceLinks").body()

    override suspend fun getFirmwareReleases(): NetworkFirmwareReleases = client.get("github/firmware/list").body()

    override suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest =
        client.get(manifestUrl).body()

    override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? {
        val response = client.get(NIGHTLY_INDEX_URL)
        return when {
            response.status == HttpStatusCode.NotFound -> null

            response.status.isSuccess() ->
                nightlyIndexJson.decodeFromString<NetworkFirmwareNightly>(response.bodyAsText())

            else -> error("Unexpected HTTP ${response.status} fetching nightly firmware index")
        }
    }

    override suspend fun getEventFirmware(): EventFirmwareResponse = client.get("resource/eventFirmware").body()
}
