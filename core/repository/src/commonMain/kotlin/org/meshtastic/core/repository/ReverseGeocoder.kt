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

/** What a reverse geocoder said about a point, in its own words. */
data class GeocodedPlace(
    /** ISO 3166-1 alpha-2. */
    val countryCode: String?,
    /** State, province or similar, as the geocoder names it ("Washington" or "WA"). */
    val subdivision: String?,
    val locality: String?,
)

interface ReverseGeocoder {
    /** Null when the platform has no geocoder, it found nothing, or it failed. */
    suspend fun place(latitude: Double, longitude: Double): GeocodedPlace?
}
