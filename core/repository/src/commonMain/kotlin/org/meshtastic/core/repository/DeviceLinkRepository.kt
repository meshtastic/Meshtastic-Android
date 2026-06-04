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
 * Provides msh.to device links imported from the bundled `urls.json`. Mirrors the Meshtastic-Apple device-links
 * feature: vendor, product-variant, and region-filtered marketplace links shown on the device hardware detail view,
 * plus a full directory.
 */
interface DeviceLinkRepository {
    /** Seeds the link table from the bundled JSON if it is empty (covers fresh install, data clear, radio switch). */
    suspend fun ensureImported()

    /** Re-imports the bundled JSON: upserts all links, recomputes `isVendor`, and prunes orphaned short codes. */
    suspend fun reconcile()

    /**
     * Links for a device's [platformioTarget], region-filtered and sorted with vendor/variant links first. Returns an
     * empty list when no links match.
     */
    suspend fun getLinksForTarget(platformioTarget: String, regionCode: String = currentRegionCode()): List<DeviceLink>

    /** All imported links, sorted by short code — backs the Settings "Device Links" directory. */
    fun observeAllLinks(): Flow<List<DeviceLink>>
}
