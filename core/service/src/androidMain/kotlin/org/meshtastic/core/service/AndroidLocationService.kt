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

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.LocationService

@Single
class AndroidLocationService(private val context: Application, private val locationRepository: LocationRepository) :
    LocationService {

    override suspend fun getCurrentLocation(): Location? {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return null
        }

        return locationRepository.getLocations().firstOrNull()
    }
}
