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
package org.meshtastic.desktop.stub

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.HeadingState
import org.meshtastic.feature.node.compass.MagneticFieldProvider
import org.meshtastic.feature.node.compass.PhoneLocationProvider
import org.meshtastic.feature.node.compass.PhoneLocationState

/** No-op [CompassHeadingProvider] — desktop has no compass sensor. */
class NoopCompassHeadingProvider : CompassHeadingProvider {
    override fun headingUpdates(): Flow<HeadingState> = flowOf(HeadingState(hasSensor = false))
}

/** No-op [PhoneLocationProvider] — desktop has no GPS provider. */
class NoopPhoneLocationProvider : PhoneLocationProvider {
    override fun locationUpdates(): Flow<PhoneLocationState> =
        flowOf(PhoneLocationState(permissionGranted = false, providerEnabled = false))
}

/** No-op [MagneticFieldProvider] — always returns zero declination. */
class NoopMagneticFieldProvider : MagneticFieldProvider {
    override fun getDeclination(latitude: Double, longitude: Double, altitude: Double, timeMillis: Long): Float = 0f
}
