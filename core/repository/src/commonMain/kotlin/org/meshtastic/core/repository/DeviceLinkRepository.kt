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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.common.util.currentRegionCode
import org.meshtastic.core.model.DeviceLink

/**
 * Provides msh.to device links resolved by the Meshtastic API (`/resource/deviceLinks`) and cached locally. Vendor and
 * region-filtered marketplace links are shown on the device hardware detail view, plus a full directory.
 */
interface DeviceLinkRepository {
    /** Seeds the link table from the bundled snapshot if it is empty (fresh install, data clear, radio switch). */
    suspend fun ensureImported()

    /** Refreshes links from the API: upserts the resolved catalog and prunes short codes that no longer exist. */
    suspend fun reconcile()

    /**
     * Links attached to a device's [platformioTarget], region-filtered and sorted with vendor links first. Returns an
     * empty list when no links match.
     */
    suspend fun getLinksForTarget(platformioTarget: String, regionCode: String = currentRegionCode()): List<DeviceLink>

    /** All cached links, sorted by short code — backs the Settings "Device Links" directory. */
    fun observeAllLinks(): Flow<List<DeviceLink>>
}
