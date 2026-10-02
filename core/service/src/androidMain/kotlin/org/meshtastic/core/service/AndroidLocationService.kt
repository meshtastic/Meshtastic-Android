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
package org.meshtastic.core.service

import android.app.Application
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.common.hasLocationPermission
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService

@Single
class AndroidLocationService(private val context: Application, private val locationRepository: LocationRepository) :
    LocationService {

    // The fix becomes the node's fixed position, so an approximate one is refused rather than stored as exact.
    override suspend fun getCurrentLocation(): Location? {
        if (!context.hasLocationPermission(precise = true)) return null

        return locationRepository.getLocations().firstOrNull()
    }
}
