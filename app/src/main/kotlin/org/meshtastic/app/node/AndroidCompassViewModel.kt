/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.app.node

import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.feature.node.compass.CompassHeadingProvider
import org.meshtastic.feature.node.compass.CompassViewModel
import org.meshtastic.feature.node.compass.MagneticFieldProvider
import org.meshtastic.feature.node.compass.PhoneLocationProvider

@KoinViewModel
class AndroidCompassViewModel(
    headingProvider: CompassHeadingProvider,
    locationProvider: PhoneLocationProvider,
    magneticFieldProvider: MagneticFieldProvider,
    dispatchers: CoroutineDispatchers,
) : CompassViewModel(headingProvider, locationProvider, magneticFieldProvider, dispatchers)
