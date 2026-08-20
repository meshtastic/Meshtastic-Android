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
package org.meshtastic.core.repository

import org.meshtastic.core.model.BootloaderOtaQuirksResponse

/**
 * Provides the nRF52 bootloader/OTA quirk catalog resolved by the Meshtastic API (`/resource/bootloaderOtaQuirks`) and
 * cached locally, seeded from the bundled `device_bootloader_ota_quirks.json` snapshot so it is never empty on a fresh
 * install or while offline.
 */
interface BootloaderOtaQuirksRepository {
    /**
     * Best-available snapshot: seeds from the bundled asset if the cache is empty, then returns cached data. Never
     * triggers a network call itself — freshness arrives via [reconcile], which [DeviceHardwareRepository] triggers on
     * the same cadence as its own catalog refresh. Callers must treat an absent/malformed entry for a given hwModel as
     * a deliberate refusal, not a gap to fill with a guess — see `applySoftDeviceVariant` in
     * `DeviceHardwareRepositoryImpl`.
     */
    suspend fun getSnapshot(): BootloaderOtaQuirksResponse

    /** Refreshes from the API. An empty response is ignored rather than wiping the existing seed/cache. */
    suspend fun reconcile()
}
