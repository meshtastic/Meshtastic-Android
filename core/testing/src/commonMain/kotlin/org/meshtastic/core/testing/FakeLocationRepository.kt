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
package org.meshtastic.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository

/** A test double for [LocationRepository] that provides a manual location emission mechanism. */
class FakeLocationRepository : LocationRepository {
    private val _receivingLocationUpdates = MutableStateFlow(false)
    override val receivingLocationUpdates: StateFlow<Boolean> = _receivingLocationUpdates

    private val _locations = MutableSharedFlow<Location>(replay = 1)

    override fun getLocations(): Flow<Location> = _locations

    fun setReceivingLocationUpdates(receiving: Boolean) {
        _receivingLocationUpdates.value = receiving
    }

    suspend fun emitLocation(location: Location) {
        _locations.emit(location)
    }
}

/** Platform-specific factory for creating [Location] objects in tests. */
expect fun createLocation(latitude: Double, longitude: Double, altitude: Double = 0.0): Location
