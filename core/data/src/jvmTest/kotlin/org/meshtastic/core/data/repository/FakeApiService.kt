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
package org.meshtastic.core.data.repository

import org.meshtastic.core.model.BootloaderOtaQuirksResponse
import org.meshtastic.core.model.EventFirmwareResponse
import org.meshtastic.core.model.FirmwareReleaseManifest
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkDeviceLinksResponse
import org.meshtastic.core.model.NetworkFirmwareNightly
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.network.service.ApiService

/**
 * [ApiService] whose endpoints answer with whatever a test sets. An endpoint left unset fails the call that reaches it,
 * and every call is counted before it runs, so a test can wait on or assert the path it expects.
 */
internal class FakeApiService(
    var deviceHardware: suspend () -> List<NetworkDeviceHardware> = { unused() },
    var deviceLinks: suspend () -> NetworkDeviceLinksResponse = { unused() },
    var firmwareReleases: suspend () -> NetworkFirmwareReleases = { unused() },
    var firmwareReleaseManifest: suspend (manifestUrl: String) -> FirmwareReleaseManifest = { unused() },
    var nightlyFirmware: suspend () -> NetworkFirmwareNightly? = { unused() },
    var eventFirmware: suspend () -> EventFirmwareResponse = { unused() },
    var bootloaderOtaQuirks: suspend () -> BootloaderOtaQuirksResponse = { unused() },
    var maintenanceUf2Manifest: suspend () -> MaintenanceUf2Manifest = { unused() },
) : ApiService {
    var deviceHardwareCalls = 0
        private set

    var deviceLinksCalls = 0
        private set

    var firmwareReleasesCalls = 0
        private set

    var firmwareReleaseManifestCalls = 0
        private set

    var nightlyFirmwareCalls = 0
        private set

    var eventFirmwareCalls = 0
        private set

    var bootloaderOtaQuirksCalls = 0
        private set

    var maintenanceUf2ManifestCalls = 0
        private set

    override suspend fun getDeviceHardware(): List<NetworkDeviceHardware> {
        deviceHardwareCalls++
        return deviceHardware()
    }

    override suspend fun getDeviceLinks(): NetworkDeviceLinksResponse {
        deviceLinksCalls++
        return deviceLinks()
    }

    override suspend fun getFirmwareReleases(): NetworkFirmwareReleases {
        firmwareReleasesCalls++
        return firmwareReleases()
    }

    override suspend fun getFirmwareReleaseManifest(manifestUrl: String): FirmwareReleaseManifest {
        firmwareReleaseManifestCalls++
        return firmwareReleaseManifest(manifestUrl)
    }

    override suspend fun getNightlyFirmware(): NetworkFirmwareNightly? {
        nightlyFirmwareCalls++
        return nightlyFirmware()
    }

    override suspend fun getEventFirmware(): EventFirmwareResponse {
        eventFirmwareCalls++
        return eventFirmware()
    }

    override suspend fun getBootloaderOtaQuirks(): BootloaderOtaQuirksResponse {
        bootloaderOtaQuirksCalls++
        return bootloaderOtaQuirks()
    }

    override suspend fun getMaintenanceUf2Manifest(): MaintenanceUf2Manifest {
        maintenanceUf2ManifestCalls++
        return maintenanceUf2Manifest()
    }

    private companion object {
        fun unused(): Nothing = error("endpoint not configured for this test")
    }
}
