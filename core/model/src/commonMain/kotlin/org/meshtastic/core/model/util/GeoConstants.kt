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
package org.meshtastic.core.model.util

/** Common geographic constants for coordinate conversions. */
object GeoConstants {
    /** Multiplier to convert protobuf integer coordinates (1e-7 degree units) to decimal degrees. */
    const val DEG_D = 1e-7

    /** Multiplier to convert protobuf integer heading values (1e-5 degree units) to decimal degrees. */
    const val HEADING_DEG = 1e-5

    /** Mean radius of the Earth in meters, for haversine calculations. */
    const val EARTH_RADIUS_METERS = 6_371_000.0
}
